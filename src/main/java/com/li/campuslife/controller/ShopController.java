package com.li.campuslife.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Shop;
import com.li.campuslife.search.ShopSearchReq;
import com.li.campuslife.search.ShopSearchService;
import com.li.campuslife.service.IShopService;
import com.li.campuslife.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    private ShopSearchService shopSearchService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写库 + 事务提交后投递 ES 同步消息
        return shopService.saveShop(shop);
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型 + 校区分页查询商铺信息
     *
     * @param typeId   商铺类型
     * @param current  页码
     * @param sortBy   distance（默认）/ comments / score
     * @param campusId 校区，不传则查全部
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sortBy", defaultValue = "distance") String sortBy,
            @RequestParam(value = "campusId", required = false) Long campusId
    ) {
        return shopService.queryShopByType(typeId, current, sortBy, campusId);
    }

    /**
     * 关键词搜索：商户名 / 地址 / 商圈
     */
    @GetMapping("/search")
    public Result search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "campusId", required = false) Long campusId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return shopService.search(keyword, campusId, current);
    }

    /**
     * ES 版检索：支持关键词、类型、校区、价格区间、距离、排序、聚合和高亮。
     * ES 不可用时自动降级到 DB，响应体 degraded=true。
     */
    @GetMapping("/search/v2")
    public Result searchV2(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "campusId", required = false) Long campusId,
            @RequestParam(value = "typeId", required = false) Long typeId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "sortBy", defaultValue = "comprehensive") String sortBy,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lon", required = false) Double lon,
            @RequestParam(value = "radius", defaultValue = "5km") String radius,
            @RequestParam(value = "minPrice", required = false) Long minPrice,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice
    ) {
        ShopSearchReq req = new ShopSearchReq();
        req.setKeyword(keyword);
        req.setCampusId(campusId);
        req.setTypeId(typeId);
        req.setPage(page);
        req.setSize(size);
        req.setSortBy(sortBy);
        req.setLat(lat);
        req.setLon(lon);
        req.setRadius(radius);
        req.setMinPrice(minPrice);
        req.setMaxPrice(maxPrice);
        return Result.ok(shopSearchService.search(req));
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
