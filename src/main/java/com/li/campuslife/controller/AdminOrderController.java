package com.li.campuslife.controller;

import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IItemOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单运维接口。正常路径是 XXL-JOB 定时扫，这里供手工补扫（排查问题时用）。
 * 鉴权同商品管理：X-Admin-Token。
 */
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final IItemOrderService itemOrderService;

    @Value("${campus.order.item-timeout-minutes:30}")
    private int timeoutMinutes;

    @PostMapping("/timeout-scan")
    public Result scanTimeout(@RequestParam(required = false) Integer minutes) {
        int threshold = minutes == null || minutes < 1 ? timeoutMinutes : minutes;
        int closed = itemOrderService.cancelTimeoutOrders(threshold);
        return Result.ok("已关闭 " + closed + " 笔超时订单（阈值 " + threshold + " 分钟）");
    }
}
