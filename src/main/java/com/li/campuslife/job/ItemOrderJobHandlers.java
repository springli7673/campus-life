package com.li.campuslife.job;

import com.li.campuslife.service.IItemOrderService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 商品订单超时关单。
 *
 * <p>下单不付款库存会被一直占着，靠这个任务定时回收。
 * 关单是条件更新（status=1 才关），重复执行不会把已支付的订单误关。</p>
 */
@Component
@RequiredArgsConstructor
public class ItemOrderJobHandlers {

    private static final Logger log = LoggerFactory.getLogger(ItemOrderJobHandlers.class);

    private final IItemOrderService itemOrderService;

    @Value("${campus.order.item-timeout-minutes:30}")
    private int timeoutMinutes;

    @XxlJob("itemOrderTimeoutJob")
    public void cancelTimeoutOrders() {
        long start = System.currentTimeMillis();
        XxlJobHelper.log("itemOrderTimeoutJob start, timeout={}min", timeoutMinutes);
        try {
            int closed = itemOrderService.cancelTimeoutOrders(timeoutMinutes);
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("itemOrderTimeoutJob success closed={} cost={}ms", closed, cost);
            if (closed > 0) {
                log.info("[item-order] 超时关单 {} 笔，耗时 {}ms", closed, cost);
            }
        } catch (Exception e) {
            XxlJobHelper.log(e);
            XxlJobHelper.handleFail("itemOrderTimeoutJob failed");
        }
    }
}
