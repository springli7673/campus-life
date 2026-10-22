package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.OrderException;
import com.li.campuslife.mapper.OrderExceptionMapper;
import com.li.campuslife.service.IOrderExceptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderExceptionServiceImpl extends ServiceImpl<OrderExceptionMapper, OrderException>
        implements IOrderExceptionService {

    private static final Logger log = LoggerFactory.getLogger(OrderExceptionServiceImpl.class);

    @Override
    public void record(Long orderId, Long userId, Long voucherId, String reason, String payload) {
        OrderException e = new OrderException()
                .setOrderId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setReason(reason == null ? "unknown" : reason)
                .setPayload(payload)
                .setStatus(0)
                .setRetryCount(0)
                .setCreateTime(LocalDateTime.now());
        try {
            save(e);
            log.warn("[exception] 已记录异常订单 orderId={} reason={}", orderId, reason);
        } catch (DataIntegrityViolationException dup) {
            log.info("[exception] 异常订单已存在，忽略 orderId={}", orderId);
        }
    }
}