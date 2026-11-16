package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.ItemVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Item;
import com.li.campuslife.mapper.ItemMapper;
import com.li.campuslife.service.IItemService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品检索：当前用 MySQL 实现，8.8 万数据下 LIKE 全表扫会有明显延迟，
 * 后续接入 ES 时只需替换本类，Controller 与前端不用改。
 */
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    /** 店铺 id → 名称；建 tb_store 表后改成查库 */
    private static final Map<Long, String> STORE_NAMES = Map.of(
            1L, "校园数码",
            2L, "校园服饰",
            3L, "校园箱包",
            4L, "校园母婴",
            5L, "校园超市",
            6L, "校园生活馆"
    );

    private static final int MAX_PAGE_SIZE = 50;

    @Override
    public Result page(String keyword, String category, String sort, Integer page, Integer size) {
        int current = page == null || page < 1 ? 1 : page;
        int limit = size == null || size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);

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
                .storeId(item.getStoreId())
                .storeName(STORE_NAMES.getOrDefault(item.getStoreId(), "校园商城"))
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
