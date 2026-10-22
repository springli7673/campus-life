package com.li.campuslife.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Shop;
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
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
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
