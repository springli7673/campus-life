package com.li.campuslife.mq;

import com.li.campuslife.service.IVoucherOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderListener.class);

    private final IVoucherOrderService voucherOrderService;
    private final OrderTimeoutProducer orderTimeoutProducer;

    public SeckillOrderListener(IVoucherOrderService voucherOrderService,
                                OrderTimeoutProducer orderTimeoutProducer) {
        this.voucherOrderService = voucherOrderService;
        this.orderTimeoutProducer = orderTimeoutProducer;
    }

    @RabbitListener(queues = MqNames.SECKILL_ORDER_QUEUE)
    public void onOrderCreated(SeckillOrderMessage message) {
        try {
            voucherOrderService.createVoucherOrder(message);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引 uk_user_voucher 挡住重复消息：视为已处理，正常 ACK
            log.warn("[seckill] 重复消息，已忽略 orderId={} userId={} voucherId={}",
                    message.orderId(), message.userId(), message.voucherId());
            return;
        }
        orderTimeoutProducer.send(message.orderId());
    }
}