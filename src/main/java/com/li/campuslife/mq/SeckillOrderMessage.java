package com.li.campuslife.mq;

/**
 * 秒杀下单消息。只带 ID，不带金额、状态等可变字段。
 */
public record SeckillOrderMessage(long orderId, long userId, long voucherId) {
}