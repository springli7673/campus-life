package com.li.campuslife.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 带上用户和券的信息，confirm 回调失败时才能做补偿回滚。
 */
public class SeckillCorrelationData extends CorrelationData {

    private final long userId;
    private final long voucherId;

    public SeckillCorrelationData(String id, long userId, long voucherId) {
        super(id);
        this.userId = userId;
        this.voucherId = voucherId;
    }

    public long getUserId() {
        return userId;
    }

    public long getVoucherId() {
        return voucherId;
    }
}