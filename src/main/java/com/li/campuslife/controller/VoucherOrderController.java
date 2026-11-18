package com.li.campuslife.controller;

import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀下单：返回订单号，实际落库由 MQ 消费者完成
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询订单状态：异步下单后前端轮询这个接口
     */
    @GetMapping("/{id}")
    public Result queryOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.queryOrder(orderId);
    }

    /**
     * 支付券订单（演示环境直接置为待使用）
     */
    @PostMapping("/{id}/pay")
    public Result pay(@PathVariable("id") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }

    /**
     * 核销券（演示：本人点击模拟店员核销）
     */
    @PostMapping("/{id}/consume")
    public Result consume(@PathVariable("id") Long orderId) {
        return voucherOrderService.consume(orderId);
    }

    /**
     * 我的券：到店出示订单号核销
     */
    @GetMapping("/list")
    public Result listMine() {
        return voucherOrderService.listMine();
    }
}