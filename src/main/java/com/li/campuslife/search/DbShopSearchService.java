package com.li.campuslife.search;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.Campus;
import com.li.campuslife.entity.Shop;
import com.li.campuslife.entity.ShopType;
import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.service.ICampusService;
import com.li.campuslife.service.IShopTypeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DB 版检索：把原来的 LIKE + 内存算距离 + 内存排序搬到这里，作为 ES 不可用时的降级实现。
 */
@Service
public class DbShopSearchService implements ShopSearchService {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /**
     * 当前数据 avg_price 的量级是 3~130，按这个分桶；如果以后改成“分”，这里要同步调整。
     */
    private static final String[] PRICE_KEYS = {"0-10", "10-30", "30-60", "60-100", "100+"};
    private static final String[] PRICE_LABELS = {"10 元以下", "10-30 元", "30-60 元", "60-100 元", "100 元以上"};
    private static final long[] PRICE_BOUNDS = {0L, 10L, 30L, 60L, 100L, Long.MAX_VALUE};

    private final ShopMapper shopMapper;
    private final ICampusService campusService;
    private final IShopTypeService shopTypeService;

    public DbShopSearchService(ShopMapper shopMapper,
                               ICampusService campusService,
                               IShopTypeService shopTypeService) {
        this.shopMapper = shopMapper;
        this.campusService = campusService;
        this.shopTypeService = shopTypeService;
    }

    @Override
    public ShopSearchResp search(ShopSearchReq req) {
        ShopSearchReq query = req == null ? new ShopSearchReq() : req;
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        String keyword = StrUtil.trim(query.getKeyword());

        QueryWrapper<Shop> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like("name", keyword)
                    .or().like("address", keyword)
                    .or().like("area", keyword));
        }
        if (query.getTypeId() != null) {
            wrapper.eq("type_id", query.getTypeId());
        }
        if (query.getCampusId() != null) {
            wrapper.eq("campus_id", query.getCampusId());
        }
        if (query.getMinPrice() != null) {
            wrapper.ge("avg_price", query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le("avg_price", query.getMaxPrice());
        }

        List<Shop> shops = shopMapper.selectList(wrapper);

        Double lat = query.getLat();
        Double lon = query.getLon();
        if (lat == null || lon == null) {
            Campus campus = campusService.getById(query.getCampusId() == null ? 1L : query.getCampusId());
            if (campus != null) {
                lat = campus.getLatitude();
                lon = campus.getLongitude();
            }
        }
        fillDistance(shops, lat, lon);
        shops.sort(comparator(query.getSortBy()).thenComparing(Shop::getId));

        ShopSearchResp resp = new ShopSearchResp();
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotal(shops.size());
        resp.setFacets(buildFacets(shops));
        resp.setDegraded(false);

        int from = (page - 1) * size;
        if (from >= shops.size()) {
            return resp;
        }
        int to = Math.min(from + size, shops.size());
        List<ShopDoc> list = new ArrayList<>(to - from);
        for (Shop shop : shops.subList(from, to)) {
            ShopDoc doc = ShopDoc.from(shop);
            if (doc == null) {
                continue;
            }
            doc.setDistance(shop.getDistance());
            list.add(doc);
        }
        resp.setList(list);
        return resp;
    }

    private ShopSearchResp.Facets buildFacets(List<Shop> shops) {
        ShopSearchResp.Facets facets = new ShopSearchResp.Facets();
        Map<Long, String> typeNames = shopTypeService.list().stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(ShopType::getId,
                        t -> t.getName() == null ? "" : t.getName(), (a, b) -> a));
        Map<Long, String> campusNames = campusService.list().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Campus::getId,
                        c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));

        Map<Long, Long> typeCounts = shops.stream()
                .filter(s -> s.getTypeId() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId, Collectors.counting()));
        Map<Long, Long> campusCounts = shops.stream()
                .filter(s -> s.getCampusId() != null)
                .collect(Collectors.groupingBy(Shop::getCampusId, Collectors.counting()));

        facets.setTypes(toFacetItems(typeCounts, typeNames, "分类 "));
        facets.setCampuses(toFacetItems(campusCounts, campusNames, "校区 "));
        facets.setPriceRanges(priceFacets(shops));
        return facets;
    }

    private static List<ShopSearchResp.FacetItem> toFacetItems(Map<Long, Long> counts,
                                                               Map<Long, String> names,
                                                               String prefix) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(e -> new ShopSearchResp.FacetItem(
                        String.valueOf(e.getKey()),
                        names.getOrDefault(e.getKey(), prefix + e.getKey()),
                        e.getValue()))
                .toList();
    }

    private static List<ShopSearchResp.FacetItem> priceFacets(List<Shop> shops) {
        long[] counts = new long[PRICE_KEYS.length];
        for (Shop shop : shops) {
            Long price = shop.getAvgPrice();
            if (price == null) {
                continue;
            }
            for (int i = 0; i < counts.length; i++) {
                if (price >= PRICE_BOUNDS[i] && price < PRICE_BOUNDS[i + 1]) {
                    counts[i]++;
                    break;
                }
            }
        }
        List<ShopSearchResp.FacetItem> items = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            items.add(new ShopSearchResp.FacetItem(PRICE_KEYS[i], PRICE_LABELS[i], counts[i]));
        }
        return items;
    }

    static String priceLabel(String key) {
        for (int i = 0; i < PRICE_KEYS.length; i++) {
            if (PRICE_KEYS[i].equals(key)) {
                return PRICE_LABELS[i];
            }
        }
        return key;
    }

    private void fillDistance(List<Shop> shops, Double lat, Double lon) {
        if (lat == null || lon == null) {
            return;
        }
        for (Shop shop : shops) {
            if (shop.getX() == null || shop.getY() == null) {
                continue;
            }
            double meters = distance(lat, lon, shop.getY(), shop.getX());
            shop.setDistance(Math.round(meters * 10) / 10.0);
        }
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }

    private static Comparator<Shop> comparator(String sortBy) {
        if ("price".equals(sortBy)) {
            return Comparator.comparingLong((Shop s) ->
                    s.getAvgPrice() == null ? Long.MAX_VALUE : s.getAvgPrice());
        }
        if ("sales".equals(sortBy)) {
            return Comparator.comparingInt((Shop s) ->
                    s.getSold() == null ? 0 : s.getSold()).reversed();
        }
        if ("comments".equals(sortBy)) {
            return Comparator.comparingInt((Shop s) ->
                    s.getComments() == null ? 0 : s.getComments()).reversed();
        }
        if ("score".equals(sortBy)) {
            return Comparator.comparingInt((Shop s) ->
                    s.getScore() == null ? 0 : s.getScore()).reversed();
        }
        return Comparator.comparingDouble((Shop s) ->
                s.getDistance() == null ? Double.MAX_VALUE : s.getDistance());
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
