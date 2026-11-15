package com.li.campuslife.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时确保索引和别名存在；索引为空时从 DB 全量同步一次。
 * ES 没起来只记 warn，应用照常启动，检索会自动降级到 DB。
 */
@Component
@ConditionalOnProperty(prefix = "campus.search", name = "mode", havingValue = "es", matchIfMissing = true)
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final EsShopSearchService esShopSearchService;

    public SearchIndexInitializer(EsShopSearchService esShopSearchService) {
        this.esShopSearchService = esShopSearchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            esShopSearchService.ensureIndexAndSync();
        } catch (Exception e) {
            log.warn("[search] ES 索引初始化失败，检索将走 DB 降级: {}", e.toString());
        }
    }
}
