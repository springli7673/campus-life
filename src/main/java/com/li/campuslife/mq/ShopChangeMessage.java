package com.li.campuslife.mq;

/**
 * 商户变更消息，只带 shopId；消费端按 id 回查数据库，避免消息体里的字段过期。
 */
public record ShopChangeMessage(long shopId) {
}
