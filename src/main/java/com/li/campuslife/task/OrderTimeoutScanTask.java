package com.li.campuslife.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.campuslife.entity.VoucherOrder;
import com.li.campuslife.service.IVoucherOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 延迟消息丢失时的兜底：定时扫未支付的超时订单。
 */
@Component
public class OrderTimeoutScanTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScanTask.class);

    private final IVoucherOrderService voucherOrderService;

    @Value("${campus.seckill.order-timeout-seconds:1800}")
    private long timeoutSeconds;

    public OrderTimeoutScanTask(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @Scheduled(fixedDelayString = "${campus.seckill.timeout-scan-delay-ms:300000}")
    public void scan() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<VoucherOrder> list = voucherOrderService.list(new QueryWrapper<VoucherOrder>()
                .eq("status", 1)
                .inSql("voucher_id", "SELECT voucher_id FROM tb_seckill_voucher")
                .lt("create_time", deadline)
                .last("limit 200"));
        if (list.isEmpty()) {
            return;
        }
        log.info("[scan] 发现 {} 笔超时未支付订单", list.size());
        for (VoucherOrder order : list) {
            try {
                voucherOrderService.cancelIfUnpaid(order.getId());
            } catch (Exception e) {
                log.error("[scan] 关单失败 orderId={}", order.getId(), e);
            }
        }
    }
}