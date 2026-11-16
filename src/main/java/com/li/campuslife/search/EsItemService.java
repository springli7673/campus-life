package com.li.campuslife.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.Item;
import com.li.campuslife.entity.ItemDoc;
import com.li.campuslife.mapper.ItemMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品 ES 检索。
 *
 * <p>8.8 万条数据下 MySQL 的 {@code LIKE '%kw%'} 无法走索引，实测单次查询约 500ms；
 * 这里改成倒排索引，同样查询在 10ms 量级。</p>
 *
 * <p>全量同步按 id 游标分批（1000/批），避免一次 bulk 30MB 请求把 socket 超时打爆。</p>
 */
@Service
public class EsItemService {

    private static final Logger log = LoggerFactory.getLogger(EsItemService.class);

    private static final String INDEX = "item_v1";
    private static final String ALIAS = "item";
    private static final int BATCH_SIZE = 1000;

    private final ElasticsearchClient client;
    private final ItemMapper itemMapper;

    /** 索引是否可用（建好且非空）。不可用时业务回落到 MySQL。 */
    private volatile boolean ready = false;

    @Value("${campus.search.item-es-enabled:true}")
    private boolean enabled;

    public EsItemService(ElasticsearchClient client, ItemMapper itemMapper) {
        this.client = client;
        this.itemMapper = itemMapper;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[item-es] 已通过配置关闭商品 ES 检索，走 MySQL");
            return;
        }
        try {
            ensureIndex();
            long count = client.count(c -> c.index(ALIAS)).count();
            ready = count > 0;
            log.info("[item-es] 索引就绪，当前文档数 {}，{}", count,
                    ready ? "检索走 ES" : "索引为空，调用 POST /item/sync 初始化");
        } catch (Exception e) {
            ready = false;
            log.warn("[item-es] 初始化失败，检索回落 MySQL: {}", e.toString());
        }
    }

    public boolean ready() {
        return enabled && ready;
    }

    /** 建索引 + 别名，幂等 */
    public void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
        if (!exists) {
            try (Reader reader = new InputStreamReader(
                    new ClassPathResource("es/item-index.json").getInputStream(), StandardCharsets.UTF_8)) {
                client.indices().create(c -> c.index(INDEX).withJson(reader));
            }
            client.indices().putAlias(a -> a.index(INDEX).name(ALIAS));
            log.info("[item-es] 已创建索引 {} 和别名 {}", INDEX, ALIAS);
            return;
        }
        if (!client.indices().existsAlias(a -> a.name(ALIAS)).value()) {
            client.indices().putAlias(a -> a.index(INDEX).name(ALIAS));
            log.info("[item-es] 已补建别名 {}", ALIAS);
        }
    }

    /**
     * 全量同步：按 id 游标分批，每批 1000 条。
     *
     * @return 同步条数
     */
    public int syncAll() throws IOException {
        ensureIndex();
        long cursor = 0L;
        int total = 0;
        int failed = 0;
        while (true) {
            QueryWrapper<Item> wrapper = new QueryWrapper<>();
            wrapper.eq("status", 1).gt("id", cursor).orderByAsc("id").last("LIMIT " + BATCH_SIZE);
            List<Item> rows = itemMapper.selectList(wrapper);
            if (rows.isEmpty()) {
                break;
            }

            List<BulkOperation> operations = new ArrayList<>(rows.size());
            for (Item item : rows) {
                ItemDoc doc = toDoc(item);
                operations.add(new BulkOperation.Builder()
                        .index(i -> i.index(ALIAS).id(String.valueOf(item.getId())).document(doc))
                        .build());
            }
            BulkResponse response = client.bulk(new BulkRequest.Builder().operations(operations).build());
            if (response.errors()) {
                for (var item : response.items()) {
                    if (item.error() != null) {
                        failed++;
                    }
                }
            }

            cursor = rows.get(rows.size() - 1).getId();
            total += rows.size();
            log.info("[item-es] 同步进度 {} 条，cursor={}", total, cursor);
        }

        client.indices().refresh(r -> r.index(ALIAS));
        long count = client.count(c -> c.index(ALIAS)).count();
        ready = count > 0;
        log.info("[item-es] 全量同步完成：提交 {} 条，失败 {} 条，索引文档数 {}", total, failed, count);
        return total;
    }

    /**
     * 检索。命中记录直接带全字段返回，不再回查数据库。
     */
    public SearchResult search(String keyword, String category, String sort, int page, int size) throws IOException {
        int current = Math.max(page, 1);
        int limit = Math.max(size, 1);
        String kw = StrUtil.trimToEmpty(keyword);

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(ALIAS)
                // ES 默认只精确统计到 10000 条，超过就返回 10000，必须显式打开
                .trackTotalHits(t -> t.enabled(true))
                .from((current - 1) * limit)
                .size(limit);

        var bool = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
        if (StrUtil.isNotBlank(kw)) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(kw)
                    .fields("name^3", "brand^2")
                    .type(TextQueryType.BestFields)));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }
        if (StrUtil.isNotBlank(category) && !"全部".equals(category)) {
            bool.filter(f -> f.term(t -> t.field("category").value(category)));
        }
        request.query(q -> q.bool(bool.build()));

        String order = sort == null ? "default" : sort;
        switch (order) {
            case "sales" -> request.sort(s -> s.field(f -> f.field("sold").order(SortOrder.Desc)));
            case "priceAsc" -> request.sort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
            case "priceDesc" -> request.sort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
            default -> {
                // 有关键词时按相关度，无关键词时按销量
                if (StrUtil.isBlank(kw)) {
                    request.sort(s -> s.field(f -> f.field("sold").order(SortOrder.Desc)));
                }
            }
        }

        SearchResponse<ItemDoc> response = client.search(request.build(), ItemDoc.class);
        long total = response.hits().total() == null ? 0L : response.hits().total().value();
        List<ItemDoc> list = new ArrayList<>();
        for (Hit<ItemDoc> hit : response.hits().hits()) {
            if (hit.source() != null) {
                list.add(hit.source());
            }
        }
        return new SearchResult(list, total);
    }

    private ItemDoc toDoc(Item item) {
        ItemDoc doc = new ItemDoc();
        doc.setId(item.getId());
        doc.setStoreId(item.getStoreId());
        doc.setStoreName(storeName(item.getStoreId()));
        doc.setName(item.getName());
        doc.setPrice(item.getPrice());
        doc.setStock(item.getStock());
        doc.setImage(item.getImage());
        doc.setCategory(item.getCategory());
        doc.setBrand(item.getBrand());
        doc.setSpec(parseSpec(item.getSpec()));
        doc.setSold(item.getSold());
        doc.setCommentCount(item.getCommentCount());
        doc.setUpdateTime(item.getUpdateTime() == null ? null : item.getUpdateTime().toString().replace('T', ' '));
        return doc;
    }

    private static final Map<Long, String> STORE_NAMES = Map.of(
            1L, "校园数码",
            2L, "校园服饰",
            3L, "校园箱包",
            4L, "校园母婴",
            5L, "校园超市",
            6L, "校园生活馆"
    );

    private String storeName(Long storeId) {
        return STORE_NAMES.getOrDefault(storeId, "校园商城");
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

    public record SearchResult(List<ItemDoc> list, long total) {
    }
}
