package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.VoucherOrder;
import com.li.campuslife.mq.SeckillOrderMessage;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀入口：Redis 预扣 + 投递消息，返回订单号
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 消费端落库：唯一索引 + 条件扣库存
     */
    void createVoucherOrder(SeckillOrderMessage message);

    /**
     * 超时关单：只有未支付才会取消，并回补库存
     */
    boolean cancelIfUnpaid(Long orderId);

    /**
     * 查询订单状态
     */
    Result queryOrder(Long orderId);
}