package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.ItemSaveDTO;
import com.li.campuslife.dto.ItemVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Item;
import com.li.campuslife.exception.BizException;
import com.li.campuslife.mapper.ItemMapper;
import com.li.campuslife.mq.ItemSyncProducer;
import com.li.campuslife.search.EsItemService;
import com.li.campuslife.service.IItemService;
import com.li.campuslife.utils.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品检索：优先走 ES，异常或索引未就绪时回落到 MySQL。
 *
 * <p>MySQL 实现完整保留，既作降级方案，也用来对比两种方案的耗时。</p>
 */
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 50;

    private final EsItemService esItemService;
    private final ItemSyncProducer itemSyncProducer;

    @Override
    public Result page(String keyword, String category, String sort, Integer page, Integer size) {
        int current = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);

        if (esItemService.ready()) {
            try {
                EsItemService.SearchResult result = esItemService.search(keyword, category, sort, current, limit);
                return Result.ok(result.list(), result.total());
            } catch (Exception e) {
                log.warn("[item] ES 检索失败，回落 MySQL: {}", e.toString());
            }
        }
        return pageFromDb(keyword, category, sort, current, limit);
    }

    /** MySQL 实现：LIKE 前缀通配走不了索引，8.8 万数据下约 500ms */
    private Result pageFromDb(String keyword, String category, String sort, int current, int limit) {
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        if (StrUtil.isNotBlank(category) && !"全部".equals(category)) {
            wrapper.eq("category", category);
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("name", kw).or().like("brand", kw));
        }

        String order = sort == null ? "default" : sort;
        switch (order) {
            case "sales" -> wrapper.orderByDesc("sold");
            case "priceAsc" -> wrapper.orderByAsc("price");
            case "priceDesc" -> wrapper.orderByDesc("price");
            default -> wrapper.orderByDesc("sold").orderByDesc("id");
        }

        long total = count(wrapper);
        wrapper.last("LIMIT " + (current - 1) * limit + "," + limit);
        List<Item> items = list(wrapper);
        return Result.ok(toVOList(items), total);
    }

    @Override
    public int syncToEs() throws IOException {
        return esItemService.syncAll();
    }

    @Override
    public Result adminPage(String keyword, Integer status, Integer page, Integer size) {
        int current = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);

        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("name", kw).or().like("id", kw));
        }
        wrapper.orderByDesc("update_time");

        long total = count(wrapper);
        wrapper.last("LIMIT " + (current - 1) * limit + "," + limit);
        return Result.ok(toVOList(list(wrapper)), total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result saveItem(ItemSaveDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getName())) {
            throw new BizException("商品名称不能为空");
        }
        if (dto.getPrice() == null || dto.getPrice() <= 0) {
            throw new BizException("价格必须大于 0");
        }
        if (dto.getStock() == null || dto.getStock() < 0) {
            throw new BizException("库存不能为负");
        }

        Item item = new Item()
                .setName(dto.getName().trim())
                .setPrice(dto.getPrice())
                .setStock(dto.getStock())
                .setImage(dto.getImage())
                .setCategory(dto.getCategory())
                .setBrand(dto.getBrand())
                .setSpec(toSpecJson(dto.getSpec()))
                .setSold(0)
                .setCommentCount(0)
                .setStatus(dto.getStatus() == null ? 1 : dto.getStatus())
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());

        save(item);
        notifyIndexAfterCommit(item.getId());
        return Result.ok(item.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateItem(Long id, ItemSaveDTO dto) {
        Item item = getById(id);
        if (item == null) {
            throw new BizException("商品不存在");
        }
        if (dto.getName() != null) {
            if (StrUtil.isBlank(dto.getName())) {
                throw new BizException("商品名称不能为空");
            }
            item.setName(dto.getName().trim());
        }
        if (dto.getPrice() != null) {
            if (dto.getPrice() <= 0) {
                throw new BizException("价格必须大于 0");
            }
            item.setPrice(dto.getPrice());
        }
        if (dto.getStock() != null) {
            if (dto.getStock() < 0) {
                throw new BizException("库存不能为负");
            }
            item.setStock(dto.getStock());
        }
        if (dto.getImage() != null) {
            item.setImage(dto.getImage());
        }
        if (dto.getCategory() != null) {
            item.setCategory(dto.getCategory());
        }
        if (dto.getBrand() != null) {
            item.setBrand(dto.getBrand());
        }
        if (dto.getSpec() != null) {
            item.setSpec(toSpecJson(dto.getSpec()));
        }
        item.setUpdateTime(LocalDateTime.now());

        updateById(item);
        notifyIndexAfterCommit(id);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result changeStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException("状态只能是 1（上架）或 2（下架）");
        }
        Item item = getById(id);
        if (item == null) {
            throw new BizException("商品不存在");
        }
        item.setStatus(status);
        item.setUpdateTime(LocalDateTime.now());
        updateById(item);
        notifyIndexAfterCommit(id);
        return Result.ok();
    }

    /** 商品变更后异步重建索引；消费端下架会自动删除文档 */
    private void notifyIndexAfterCommit(Long itemId) {
        AfterCommit.run(() -> itemSyncProducer.send(itemId));
    }

    private String toSpecJson(Map<String, String> spec) {
        return spec == null || spec.isEmpty() ? null : JSONUtil.toJsonStr(spec);
    }

    @Override
    public Result detail(Long id) {
        Item item = getById(id);
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            return Result.fail("商品不存在或已下架");
        }
        return Result.ok(toVO(item));
    }

    @Override
    public Result recommend(Integer size) {
        int limit = size == null || size < 1 ? 6 : Math.min(size, MAX_PAGE_SIZE);
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByDesc("sold").last("LIMIT " + limit);
        return Result.ok(toVOList(list(wrapper)));
    }

    @Override
    public Result categories() {
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT category").eq("status", 1).orderByAsc("category");
        List<Item> rows = list(wrapper);
        List<String> categories = new ArrayList<>(rows.size());
        for (Item row : rows) {
            if (StrUtil.isNotBlank(row.getCategory())) {
                categories.add(row.getCategory());
            }
        }
        return Result.ok(categories);
    }

    @Override
    public List<ItemVO> toVOList(List<Item> items) {
        List<ItemVO> list = new ArrayList<>(items.size());
        for (Item item : items) {
            list.add(toVO(item));
        }
        return list;
    }

    @Override
    public ItemVO toVO(Item item) {
        return ItemVO.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .stock(item.getStock())
                .image(item.getImage())
                .category(item.getCategory())
                .brand(item.getBrand())
                .spec(parseSpec(item.getSpec()))
                .sold(item.getSold())
                .commentCount(item.getCommentCount())
                .build();
    }

    private Map<String, String> parseSpec(String spec) {
        if (StrUtil.isBlank(spec)) {
            return Map.of();
        }
        try {
            JSONObject json = JSONUtil.parseObj(spec);
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : json.keySet()) {
                result.put(key, String.valueOf(json.get(key)));
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
