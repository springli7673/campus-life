package com.li.campuslife.task;

import com.li.campuslife.dto.ReconcileResult;
import com.li.campuslife.service.StockReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(fixedDelayString = "${campus.reconcile.interval-ms:300000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            List<ReconcileResult> results = reconcileService.reconcileAll(false);
            List<ReconcileResult> anomalies = results.stream()
                    .filter(ReconcileResult::hasAnomaly)
                    .toList();
            long repaired = anomalies.stream().filter(ReconcileResult::repaired).count();
            if (anomalies.isEmpty()) {
                log.debug("[reconcile] 全部一致，共检查 {} 张秒杀券", results.size());
            } else {
                log.warn("[reconcile] 检查 {} 张秒杀券，发现 {} 张差异，本轮修复 {} 张",
                        results.size(), anomalies.size(), repaired);
            }
        } catch (Exception e) {
            log.error("[reconcile] 对账任务执行失败", e);
        }
    }
}
