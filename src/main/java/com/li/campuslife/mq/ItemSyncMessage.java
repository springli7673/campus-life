package com.li.campuslife.mq;

/**
 * 商品变更消息，只带 itemId；消费端回查数据库，避免消息体字段过期。
 */
public record ItemSyncMessage(long itemId) {
}
