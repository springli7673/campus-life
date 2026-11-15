package com.li.campuslife.search;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.li.campuslife.entity.Campus;
import com.li.campuslife.entity.Shop;
import com.li.campuslife.entity.ShopType;
import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.service.ICampusService;
import com.li.campuslife.service.IShopTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ES 版检索。失败或熔断时自动降级到 {@link DbShopSearchService}，响应里 {@code degraded=true}。
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "campus.search", name = "mode", havingValue = "es", matchIfMissing = true)
public class EsShopSearchService implements ShopSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsShopSearchService.class);

    private static final String INDEX = "shop_v1";
    private static final String ALIAS = "shop";
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final ElasticsearchClient client;
    private final SearchCircuitBreaker circuitBreaker;
    private final DbShopSearchService dbShopSearchService;
    private final ShopMapper shopMapper;
    private final ICampusService campusService;
    private final IShopTypeService shopTypeService;

    public EsShopSearchService(ElasticsearchClient client,
                               SearchCircuitBreaker circuitBreaker,
                               DbShopSearchService dbShopSearchService,
                               ShopMapper shopMapper,
                               ICampusService campusService,
                               IShopTypeService shopTypeService) {
        this.client = client;
        this.circuitBreaker = circuitBreaker;
        this.dbShopSearchService = dbShopSearchService;
        this.shopMapper = shopMapper;
        this.campusService = campusService;
        this.shopTypeService = shopTypeService;
    }

    @Override
    public ShopSearchResp search(ShopSearchReq req) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[search] ES 熔断中，直接走 DB");
            return degrade(req);
        }
        try {
            ShopSearchResp resp = doSearch(req);
            circuitBreaker.recordSuccess();
            return resp;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("[search] ES 检索失败，降级 DB: {}", e.toString());
            return degrade(req);
        }
    }

    private ShopSearchResp degrade(ShopSearchReq req) {
        ShopSearchResp resp = dbShopSearchService.search(req);
        resp.setDegraded(true);
        return resp;
    }

    private ShopSearchResp doSearch(ShopSearchReq req) throws IOException {
        ShopSearchReq query = req == null ? new ShopSearchReq() : req;
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        String keyword = StrUtil.trim(query.getKeyword());

        Double lat = query.getLat();
        Double lon = query.getLon();
        if ((lat == null || lon == null) && query.getCampusId() != null) {
            Campus campus = campusService.getById(query.getCampusId());
            if (campus != null) {
                lat = campus.getLatitude();
                lon = campus.getLongitude();
            }
        }
        final Double geoLat = lat;
        final Double geoLon = lon;

        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (StrUtil.isNotBlank(keyword)) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("name^3", "area^2", "address")
                    .type(TextQueryType.BestFields)));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }
        if (query.getTypeId() != null) {
            bool.filter(f -> f.term(t -> t.field("typeId").value(query.getTypeId())));
        }
        if (query.getCampusId() != null) {
            bool.filter(f -> f.term(t -> t.field("campusId").value(query.getCampusId())));
        }
        if (query.getMinPrice() != null || query.getMaxPrice() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field("avgPrice");
                if (query.getMinPrice() != null) {
                    n.gte(query.getMinPrice().doubleValue());
                }
                if (query.getMaxPrice() != null) {
                    n.lte(query.getMaxPrice().doubleValue());
                }
                return n;
            })));
        }
        if (geoLat != null && geoLon != null) {
            String distance = StrUtil.isBlank(query.getRadius()) ? "5km" : query.getRadius();
            bool.filter(f -> f.geoDistance(g -> g
                    .field("location")
                    .distance(distance)
                    .location(l -> l.latlon(ll -> ll.lat(geoLat).lon(geoLon)))));
        }

        FunctionScoreQuery.Builder functionScore = new FunctionScoreQuery.Builder()
                .query(bool.build()._toQuery())
                .boostMode(FunctionBoostMode.Multiply);
        if (geoLat != null && geoLon != null) {
            functionScore.functions(fn -> fn.gauss(g -> g.geo(gg -> gg
                    .field("location")
                    .placement(p -> p.origin(GeoLocation.of(o -> o.latlon(ll -> ll.lat(geoLat).lon(geoLon))))
                            .offset("500m")
                            .scale("2km")))));
        }
        functionScore.functions(fn -> fn.fieldValueFactor(f -> f
                .field("sold")
                .modifier(FieldValueFactorModifier.Log1p)
                .factor(0.2)
                .missing(1.0)));
        functionScore.functions(fn -> fn.fieldValueFactor(f -> f
                .field("score")
                .factor(2.0)
                .missing(0.0)));

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(ALIAS)
                .from((page - 1) * size)
                .size(size)
                .trackTotalHits(t -> t.enabled(true))
                .query(q -> q.functionScore(functionScore.build()))
                .highlight(h -> h.fields("name", f -> f))
                .aggregations("types", a -> a.terms(t -> t.field("typeId").size(20)))
                .aggregations("campuses", a -> a.terms(t -> t.field("campusId").size(20)))
                .aggregations("priceRanges", a -> a.range(r -> r.field("avgPrice")
                        .ranges(rr -> rr.key("0-10").from(0.0).to(10.0))
                        .ranges(rr -> rr.key("10-30").from(10.0).to(30.0))
                        .ranges(rr -> rr.key("30-60").from(30.0).to(60.0))
                        .ranges(rr -> rr.key("60-100").from(60.0).to(100.0))
                        .ranges(rr -> rr.key("100+").from(100.0))));

        String sortBy = query.getSortBy() == null ? "comprehensive" : query.getSortBy();
        if ("distance".equals(sortBy) && geoLat != null && geoLon != null) {
            request.sort(so -> so.geoDistance(g -> g
                    .field("location")
                    .location(l -> l.latlon(ll -> ll.lat(geoLat).lon(geoLon)))
                    .order(SortOrder.Asc)));
        } else if ("price".equals(sortBy)) {
            request.sort(so -> so.field(f -> f.field("avgPrice").order(SortOrder.Asc)));
        } else if ("sales".equals(sortBy)) {
            request.sort(so -> so.field(f -> f.field("sold").order(SortOrder.Desc)));
        } else if ("comments".equals(sortBy)) {
            request.sort(so -> so.field(f -> f.field("comments").order(SortOrder.Desc)));
        } else if ("score".equals(sortBy)) {
            request.sort(so -> so.field(f -> f.field("score").order(SortOrder.Desc)));
        }

        SearchResponse<ShopDoc> response = client.search(request.build(), ShopDoc.class);
        ShopSearchResp resp = new ShopSearchResp();
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
        resp.setDegraded(false);

        List<ShopDoc> list = new ArrayList<>(response.hits().hits().size());
        for (Hit<ShopDoc> hit : response.hits().hits()) {
            ShopDoc doc = hit.source();
            if (doc == null) {
                continue;
            }
            Map<String, List<String>> highlight = hit.highlight();
            if (highlight != null && highlight.get("name") != null && !highlight.get("name").isEmpty()) {
                doc.setName(highlight.get("name").get(0));
            }
            if (geoLat != null && geoLon != null) {
                doc.setDistance(distance(geoLat, geoLon, doc.getLocation()));
            }
            list.add(doc);
        }
        resp.setList(list);
        resp.setFacets(buildFacets(response));
        return resp;
    }

    private ShopSearchResp.Facets buildFacets(SearchResponse<ShopDoc> response) {
        ShopSearchResp.Facets facets = new ShopSearchResp.Facets();
        Map<Long, String> typeNames = shopTypeService.list().stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(ShopType::getId,
                        t -> t.getName() == null ? "" : t.getName(), (a, b) -> a));
        Map<Long, String> campusNames = campusService.list().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Campus::getId,
                        c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));

        addTermFacets(response.aggregations().get("types"), typeNames, "分类 ", facets.getTypes());
        addTermFacets(response.aggregations().get("campuses"), campusNames, "校区 ", facets.getCampuses());

        Aggregate priceRanges = response.aggregations().get("priceRanges");
        if (priceRanges != null && priceRanges.isRange()) {
            for (RangeBucket bucket : priceRanges.range().buckets().array()) {
                String key = bucket.key();
                facets.getPriceRanges().add(new ShopSearchResp.FacetItem(
                        key, DbShopSearchService.priceLabel(key), bucket.docCount()));
            }
        }
        return facets;
    }

    /**
     * 数值字段的 terms 聚合返回的是 lterms（long terms），不是 sterms；两种都兼容。
     */
    private static void addTermFacets(Aggregate aggregate,
                                      Map<Long, String> names,
                                      String prefix,
                                      List<ShopSearchResp.FacetItem> target) {
        if (aggregate == null) {
            return;
        }
        if (aggregate.isLterms()) {
            for (LongTermsBucket bucket : aggregate.lterms().buckets().array()) {
                long id = bucket.key();
                target.add(new ShopSearchResp.FacetItem(
                        String.valueOf(id),
                        names.getOrDefault(id, prefix + id),
                        bucket.docCount()));
            }
        } else if (aggregate.isSterms()) {
            for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                String key = bucket.key().stringValue();
                long id;
                try {
                    id = Long.parseLong(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                target.add(new ShopSearchResp.FacetItem(
                        key,
                        names.getOrDefault(id, prefix + id),
                        bucket.docCount()));
            }
        }
    }

    /**
     * 增量 upsert 一家商户，供 MQ 消费者调用。
     */
    public void indexShop(Shop shop) throws IOException {
        if (shop == null || shop.getId() == null) {
            return;
        }
        ShopDoc doc = ShopDoc.from(shop);
        if (doc == null) {
            return;
        }
        client.index(i -> i.index(ALIAS).id(String.valueOf(shop.getId())).document(doc));
    }

    /**
     * 删除索引文档。文档本来就不存在时忽略 404。
     */
    public void deleteShop(long shopId) throws IOException {
        try {
            client.delete(d -> d.index(ALIAS).id(String.valueOf(shopId)));
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;
            }
        }
    }

    /**
     * 当前别名下的文档数，对账用。
     */
    public long countIndexed() throws IOException {
        return client.count(c -> c.index(ALIAS)).count();
    }

    /**
     * 启动时建索引 / 补别名；索引为空时从 DB 同步一次。ES 没起来不影响应用启动。
     */
    public void ensureIndexAndSync() throws IOException {
        boolean indexExists = client.indices().exists(e -> e.index(INDEX)).value();
        if (!indexExists) {
            try (Reader reader = new InputStreamReader(
                    new ClassPathResource("es/shop-index.json").getInputStream(), StandardCharsets.UTF_8)) {
                client.indices().create(c -> c.index(INDEX).withJson(reader));
            }
            client.indices().putAlias(a -> a.index(INDEX).name(ALIAS));
            log.info("[search] 已创建索引 {} 和别名 {}", INDEX, ALIAS);
        } else {
            boolean aliasExists = client.indices().existsAlias(a -> a.name(ALIAS)).value();
            if (!aliasExists) {
                client.indices().putAlias(a -> a.index(INDEX).name(ALIAS));
                log.info("[search] 已补建别名 {}", ALIAS);
            }
        }

        long count = client.count(c -> c.index(ALIAS)).count();
        if (count == 0) {
            int synced = syncAll();
            log.info("[search] 索引为空，已从 DB 同步 {} 家商户", synced);
        } else {
            log.info("[search] 索引已存在，当前文档数 {}", count);
        }
    }

    /**
     * 全量把 tb_shop 灌进索引。当前数据量很小，一次 bulk 即可；10 万级时需要改成游标分批。
     */
    public int syncAll() throws IOException {
        return syncAll(0, 1);
    }

    /**
     * 分片全量同步：按 {@code id % shardTotal == shardIndex} 取商户，供分片广播 Job 调用。
     */
    public int syncAll(int shardIndex, int shardTotal) throws IOException {
        int total = Math.max(1, shardTotal);
        int index = Math.max(0, Math.min(shardIndex, total - 1));
        QueryWrapper<Shop> query = new QueryWrapper<>();
        if (total > 1) {
            query.apply("MOD(id, {0}) = {1}", total, index);
        }
        List<Shop> shops = shopMapper.selectList(query);
        if (shops.isEmpty()) {
            return 0;
        }
        List<BulkOperation> operations = new ArrayList<>(shops.size());
        for (Shop shop : shops) {
            ShopDoc doc = ShopDoc.from(shop);
            if (doc == null) {
                continue;
            }
            operations.add(new BulkOperation.Builder()
                    .index(i -> i.index(ALIAS).id(String.valueOf(shop.getId())).document(doc))
                    .build());
        }
        BulkResponse response = client.bulk(new BulkRequest.Builder().operations(operations).build());
        if (response.errors()) {
            long failed = response.items().stream().filter(item -> item.error() != null).count();
            log.warn("[search] 全量同步完成，但有 {} 条失败", failed);
        }
        client.indices().refresh(r -> r.index(ALIAS));
        log.info("[search] 全量同步完成，共 {} 家商户", operations.size());
        return operations.size();
    }

    private static Double distance(Double lat, Double lon, String location) {
        if (lat == null || lon == null || location == null) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double docLat = Double.parseDouble(parts[0].trim());
            double docLon = Double.parseDouble(parts[1].trim());
            return Math.round(distance(lat, lon, docLat, docLon) * 10) / 10.0;
        } catch (NumberFormatException e) {
            return null;
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
