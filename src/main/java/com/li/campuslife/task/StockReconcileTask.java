package com.li.campuslife.task;

import com.li.campuslife.dto.ReconcileResult;
import com.li.campuslife.service.StockReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时对账：默认 5 分钟一轮，只有差异连续两轮都一致才修复。
 */
@Component
public class StockReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(StockReconcileTask.class);

    private final StockReconcileService reconcileService;

    @Value("${campus.reconcile.enabled:true}")
    private boolean enabled;

    public StockReconcileTask(StockReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    /**
     * 兼容单实例 / 回滚调用：@Scheduled 可以直接加在这个无参方法上。
     */
    public void run() {
        run(0, 1);
    }

    /**
     * 由 XXL-JOB 的 stockReconcileJob 分片广播调用。
     *
     * @param shardIndex 当前分片下标，从 0 开始
     * @param shardTotal 分片总数
     */
    public void run(int shardIndex, int shardTotal) {
        if (!enabled) {
            log.debug("[reconcile] 任务已关闭，跳过 shard={}/{}", shardIndex, shardTotal);
            return;
        }
        try {
            List<ReconcileResult> results = reconcileService.reconcileAll(false, shardIndex, shardTotal);
            List<ReconcileResult> anomalies = results.stream()
                    .filter(ReconcileResult::hasAnomaly)
                    .toList();
            long repaired = anomalies.stream().filter(ReconcileResult::repaired).count();
            if (anomalies.isEmpty()) {
                log.debug("[reconcile] shard={}/{} 全部一致，共检查 {} 张秒杀券",
                        shardIndex, shardTotal, results.size());
            } else {
                log.warn("[reconcile] shard={}/{} 检查 {} 张秒杀券，发现 {} 张差异，本轮修复 {} 张",
                        shardIndex, shardTotal, results.size(), anomalies.size(), repaired);
            }
        } catch (Exception e) {
            log.error("[reconcile] 对账任务执行失败 shard={}/{}", shardIndex, shardTotal, e);
        }
    }
}
