package com.li.campuslife.dto;

import java.util.List;

/**
 * 单张秒杀券的对账结果。
 *
 * @param voucherId   券 id
 * @param dbStock     tb_seckill_voucher.stock（权威值）
 * @param redisStock  Redis 剩余库存，-1 表示未预热
 * @param soldCount   DB 中未取消的订单数
 * @param strayUsers  Redis 占位集合里多出来的用户（DB 无有效订单）
 * @param missingUsers DB 有有效订单、Redis 占位集合里缺失的用户
 * @param repaired    本次是否执行了修复
 * @param state       一致 / 首次观测待复核 / 已修复 / 未预热
 */
public record ReconcileResult(
        long voucherId,
        int dbStock,
        int redisStock,
        int soldCount,
        List<String> strayUsers,
        List<String> missingUsers,
        boolean repaired,
        String state) {

    public boolean hasAnomaly() {
        return redisStock >= 0
                && (redisStock != dbStock || !strayUsers.isEmpty() || !missingUsers.isEmpty());
    }
}
