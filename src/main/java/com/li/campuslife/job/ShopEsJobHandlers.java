package com.li.campuslife.job;

import com.li.campuslife.mapper.ShopMapper;
import com.li.campuslife.search.EsShopSearchService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ES 索引相关 Job。只在 mode=es 时注册，避免回滚到 db 模式时还去连 ES。
 */
@Component
@ConditionalOnProperty(prefix = "campus.search", name = "mode", havingValue = "es", matchIfMissing = true)
public class ShopEsJobHandlers {

    private final EsShopSearchService esShopSearchService;
    private final ShopMapper shopMapper;

    public ShopEsJobHandlers(EsShopSearchService esShopSearchService, ShopMapper shopMapper) {
        this.esShopSearchService = esShopSearchService;
        this.shopMapper = shopMapper;
    }

    /**
     * 分片广播全量重建。按 id % shardTotal 分片，每个实例只灌自己那份。
     */
    @XxlJob("shopEsFullSyncJob")
    public void shopEsFullSyncJob() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        long start = System.currentTimeMillis();
        XxlJobHelper.log("shopEsFullSyncJob start shard={}/{}", shardIndex, shardTotal);
        try {
            int synced = esShopSearchService.syncAll(shardIndex, shardTotal);
            XxlJobHelper.log("shopEsFullSyncJob success shard={}/{}, synced={}, cost={}ms",
                    shardIndex, shardTotal, synced, System.currentTimeMillis() - start);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("shopEsFullSyncJob failed shard=" + shardIndex + "/" + shardTotal);
        }
    }

    /**
     * DB 与 ES 文档数对账，不一致就把 Job 标失败，触发 admin 告警。
     */
    @XxlJob("shopEsReconcileJob")
    public void shopEsReconcileJob() {
        long start = System.currentTimeMillis();
        XxlJobHelper.log("shopEsReconcileJob start");
        try {
            Long dbCountObj = shopMapper.selectCount(null);
            long dbCount = dbCountObj == null ? 0L : dbCountObj;
            long esCount = esShopSearchService.countIndexed();
            XxlJobHelper.log("shopEsReconcileJob db={}, es={}, cost={}ms",
                    dbCount, esCount, System.currentTimeMillis() - start);
            if (dbCount != esCount) {
                XxlJobHelper.handleFail("count mismatch, db=" + dbCount + ", es=" + esCount);
            }
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("shopEsReconcileJob failed: " + e.getMessage());
        }
    }
}
