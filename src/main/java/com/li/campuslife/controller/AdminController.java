package com.li.campuslife.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.dto.ReconcileResult;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.OrderException;
import com.li.campuslife.entity.ReconcileLog;
import com.li.campuslife.mq.SeckillOrderMessage;
import com.li.campuslife.mq.SeckillOrderProducer;
import com.li.campuslife.service.IOrderExceptionService;
import com.li.campuslife.service.IReconcileLogService;
import com.li.campuslife.service.SeckillPreheatService;
import com.li.campuslife.service.StockReconcileService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 演示用运维接口。生产环境应加鉴权或直接下线。
 */
@RestController
@RequestMapping("/admin/seckill")
public class AdminController {

    @Resource
    private SeckillPreheatService preheatService;
    @Resource
    private IOrderExceptionService orderExceptionService;
    @Resource
    private SeckillOrderProducer seckillOrderProducer;
    @Resource
    private StockReconcileService stockReconcileService;
    @Resource
    private IReconcileLogService reconcileLogService;

    /** 预热秒杀券到 Redis；force=true 覆盖库存（重置演示用） */
    @PostMapping("/preheat")
    public Result preheat(@RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(Map.of("vouchers", preheatService.preheat(force)));
    }

    /** 立即对账；repair=true 时跳过"两次观测"直接修复 */
    @GetMapping("/reconcile")
    public Result reconcile(@RequestParam(defaultValue = "false") boolean repair) {
        List<ReconcileResult> items = stockReconcileService.reconcileAll(repair);
        long anomalies = items.stream().filter(ReconcileResult::hasAnomaly).count();
        long repaired = items.stream().filter(ReconcileResult::repaired).count();
        return Result.ok(Map.of(
                "checked", items.size(),
                "anomalies", anomalies,
                "repaired", repaired,
                "items", items));
    }

    /** 对账日志（只在发现差异时写入） */
    @GetMapping("/reconcile/logs")
    public Result reconcileLogs(@RequestParam(defaultValue = "50") Integer limit) {
        int size = Math.min(Math.max(limit == null ? 50 : limit, 1), 200);
        return Result.ok(reconcileLogService.list(new QueryWrapper<ReconcileLog>()
                .orderByDesc("create_time")
                .last("limit " + size)));
    }

    /** 查看异常订单，默认只看 OPEN */
    @GetMapping("/exception")
    public Result exceptions(@RequestParam(defaultValue = "0") Integer status) {
        List<OrderException> list = orderExceptionService.list(new QueryWrapper<OrderException>()
                .eq("status", status)
                .orderByDesc("create_time")
                .last("limit 100"));
        return Result.ok(list);
    }

    /** 人工重投一条异常订单 */
    @PostMapping("/exception/{orderId}/retry")
    public Result retry(@PathVariable Long orderId) {
        OrderException exception = orderExceptionService.getOne(
                new QueryWrapper<OrderException>().eq("order_id", orderId));
        if (exception == null) {
            return Result.fail("异常记录不存在");
        }
        seckillOrderProducer.send(new SeckillOrderMessage(
                exception.getOrderId(), exception.getUserId(), exception.getVoucherId()));
        exception.setStatus(1).setRetryCount(exception.getRetryCount() + 1);
        orderExceptionService.updateById(exception);
        return Result.ok(Map.of("orderId", orderId, "status", 1));
    }
}
