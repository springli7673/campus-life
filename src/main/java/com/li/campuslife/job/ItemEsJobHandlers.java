package com.li.campuslife.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.Item;
import com.li.campuslife.mapper.ItemMapper;
import com.li.campuslife.search.EsItemService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 商品索引的调度任务：每日全量重建 + 数量对账。
 *
 * <p>全量用 id 游标分批（1000/批），8.4 万条约 14 秒；
 * 对账发现数量不一致就把任务标失败，交给 admin 的告警。</p>
 */
@Component
@RequiredArgsConstructor
public class ItemEsJobHandlers {

    private static final Logger log = LoggerFactory.getLogger(ItemEsJobHandlers.class);

    private final EsItemService esItemService;
    private final ItemMapper itemMapper;

    @XxlJob("itemEsFullSyncJob")
    public void fullSync() {
        long start = System.currentTimeMillis();
        XxlJobHelper.log("itemEsFullSyncJob start");
        try {
            int synced = esItemService.syncAll();
            long indexCount = esItemService.count();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("itemEsFullSyncJob success synced={} indexCount={} cost={}ms",
                    synced, indexCount, cost);
            log.info("[item-es] 定时全量同步完成 synced={} indexCount={} cost={}ms", synced, indexCount, cost);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("itemEsFullSyncJob failed");
        }
    }

    @XxlJob("itemEsReconcileJob")
    public void reconcile() {
        XxlJobHelper.log("itemEsReconcileJob start");
        try {
            Long dbCount = itemMapper.selectCount(new QueryWrapper<Item>().eq("status", 1));
            long indexCount = esItemService.count();
            long diff = (dbCount == null ? 0 : dbCount) - indexCount;
            XxlJobHelper.log("itemEsReconcileJob db={} index={} diff={}", dbCount, indexCount, diff);
            if (diff != 0) {
                XxlJobHelper.handleFail("商品索引与数据库数量不一致: db=" + dbCount + ", index=" + indexCount);
            }
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("itemEsReconcileJob failed");
        }
    }
}
