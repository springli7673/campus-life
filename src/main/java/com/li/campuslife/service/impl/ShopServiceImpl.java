package com.li.campuslife.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Campus;
import com.li.campuslife.entity.Shop;
import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.service.ICampusService;
import com.li.campuslife.service.IShopService;
import com.li.campuslife.utils.CacheClient;
import com.li.campuslife.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.li.campuslife.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.li.campuslife.utils.RedisConstants.CACHE_SHOP_TTL;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ICampusService campusService;

    @Override
    public Result queryById(Long id) {
        // 切换这一行即可切换方案
        // 1.仅防穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 2.互斥锁 穿透+击穿
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 3.逻辑过期
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 先更新数据库，再删除缓存 Cache-Aside
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Long campusId) {
        Campus campus = campusService.getById(campusId == null ? 1L : campusId);
        List<Shop> shops = query()
                .eq("type_id", typeId)
                .eq(campusId != null, "campus_id", campusId)
                .list();
        fillDistance(shops, campus);
        return Result.ok(pageOf(shops, current, sortBy));
    }

    @Override
    public Result search(String keyword, Long campusId, Integer current) {
        String kw = StrUtil.trim(keyword);
        if (StrUtil.isBlank(kw)) {
            return Result.ok(Collections.emptyList());
        }
        Campus campus = campusService.getById(campusId == null ? 1L : campusId);
        List<Shop> shops = query()
                .eq(campusId != null, "campus_id", campusId)
                .and(w -> w.like("name", kw).or().like("address", kw).or().like("area", kw))
                .list();
        fillDistance(shops, campus);
        return Result.ok(pageOf(shops, current, "distance"));
    }

    /** 用校区中心算直线距离（Haversine），商户只有 240 家，直接在内存里算比拼 Redis Geo 简单 */
    private void fillDistance(List<Shop> shops, Campus campus) {
        if (campus == null || campus.getLatitude() == null || campus.getLongitude() == null) {
            return;
        }
        for (Shop shop : shops) {
            if (shop.getX() == null || shop.getY() == null) {
                continue;
            }
            double meters = distance(campus.getLatitude(), campus.getLongitude(), shop.getY(), shop.getX());
            shop.setDistance(Math.round(meters * 10) / 10.0);
        }
    }

    private static double distance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    private List<Shop> pageOf(List<Shop> shops, Integer current, String sortBy) {
        if (shops.isEmpty()) {
            return Collections.emptyList();
        }
        shops.sort(comparator(sortBy).thenComparing(Shop::getId));
        int page = Math.max(current == null ? 1 : current, 1);
        int from = (page - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        if (from >= shops.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + SystemConstants.DEFAULT_PAGE_SIZE, shops.size());
        return new ArrayList<>(shops.subList(from, to));
    }

    private static Comparator<Shop> comparator(String sortBy) {
        if ("comments".equals(sortBy)) {
            return Comparator.comparingInt((Shop s) -> s.getComments() == null ? 0 : s.getComments()).reversed();
        }
        if ("score".equals(sortBy)) {
            return Comparator.comparingInt((Shop s) -> s.getScore() == null ? 0 : s.getScore()).reversed();
        }
        return Comparator.comparingDouble((Shop s) -> s.getDistance() == null ? Double.MAX_VALUE : s.getDistance());
    }
}
