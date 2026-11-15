package com.li.campuslife.job;

import com.li.campuslife.service.OrderRecoveryService;
import com.li.campuslife.service.SeckillPreheatService;
import com.li.campuslife.task.OrderTimeoutScanTask;
import com.li.campuslife.task.StockReconcileTask;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB 的「壳」：只负责读分片、埋日志、把异常反馈给调度中心。
 * 业务逻辑仍在 Task / Service 里，回滚时把 @Scheduled 加回原方法即可。
 */
@Component
public class XxlJobHandlers {

    private final OrderTimeoutScanTask orderTimeoutScanTask;
    private final StockReconcileTask stockReconcileTask;
    private final SeckillPreheatService seckillPreheatService;
    private final OrderRecoveryService orderRecoveryService;

    public XxlJobHandlers(OrderTimeoutScanTask orderTimeoutScanTask,
                          StockReconcileTask stockReconcileTask,
                          SeckillPreheatService seckillPreheatService,
                          OrderRecoveryService orderRecoveryService) {
        this.orderTimeoutScanTask = orderTimeoutScanTask;
        this.stockReconcileTask = stockReconcileTask;
        this.seckillPreheatService = seckillPreheatService;
        this.orderRecoveryService = orderRecoveryService;
    }

    /**
     * 超时未支付订单兜底关单。路由用「故障转移」，不需要分片。
     */
    @XxlJob("orderTimeoutJob")
    public void orderTimeoutJob() {
        long start = System.currentTimeMillis();
        XxlJobHelper.log("orderTimeoutJob start");
        try {
            orderTimeoutScanTask.scan();
            XxlJobHelper.log("orderTimeoutJob success, cost={}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("orderTimeoutJob failed: " + e.getMessage());
        }
    }

    /**
     * 库存对账。路由用「分片广播」，每个实例只处理自己的分片。
     */
    @XxlJob("stockReconcileJob")
    public void stockReconcileJob() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        long start = System.currentTimeMillis();
        XxlJobHelper.log("stockReconcileJob start shard={}/{}", shardIndex, shardTotal);
        try {
            stockReconcileTask.run(shardIndex, shardTotal);
            XxlJobHelper.log("stockReconcileJob success shard={}/{}, cost={}ms",
                    shardIndex, shardTotal, System.currentTimeMillis() - start);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("stockReconcileJob failed shard=" + shardIndex + "/" + shardTotal);
        }
    }

    /**
     * 开售前 5 分钟把券的库存和时间窗预热到 Redis。
     */
    @XxlJob("seckillWarmUpJob")
    public void seckillWarmUpJob() {
        long start = System.currentTimeMillis();
        try {
            int count = seckillPreheatService.preheatStartingSoon(5);
            XxlJobHelper.log("seckillWarmUpJob success, vouchers={}, cost={}ms",
                    count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("seckillWarmUpJob failed: " + e.getMessage());
        }
    }

    /**
     * 异常订单自动重投，最多 5 次；分片广播避免多实例重复处理。
     */
    @XxlJob("exceptionRetryJob")
    public void exceptionRetryJob() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        long start = System.currentTimeMillis();
        XxlJobHelper.log("exceptionRetryJob start shard={}/{}", shardIndex, shardTotal);
        try {
            int retried = orderRecoveryService.retryOpen(shardIndex, shardTotal, 5, 100);
            XxlJobHelper.log("exceptionRetryJob success shard={}/{}, retried={}, cost={}ms",
                    shardIndex, shardTotal, retried, System.currentTimeMillis() - start);
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("exceptionRetryJob failed shard=" + shardIndex + "/" + shardTotal);
        }
    }
}
