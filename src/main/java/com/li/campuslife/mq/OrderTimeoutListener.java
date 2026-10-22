package com.li.campuslife.mq;


import com.li.campuslife.service.IVoucherOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutListener.class);

    private final IVoucherOrderService voucherOrderService;

    public OrderTimeoutListener(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @RabbitListener(queues = MqNames.ORDER_TIMEOUT_QUEUE)
    public void onTimeout(OrderTimeoutMessage message) {
        boolean cancelled = voucherOrderService.cancelIfUnpaid(message.orderId());
        log.info("[timeout] 关单处理完成 orderId={} cancelled={}", message.orderId(), cancelled);
    }
}