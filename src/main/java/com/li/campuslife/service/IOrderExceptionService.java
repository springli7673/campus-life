package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.entity.OrderException;

public interface IOrderExceptionService extends IService<OrderException> {

    /** 幂等记录一条异常（orderId 唯一，重复则忽略） */
    void record(Long orderId, Long userId, Long voucherId, String reason, String payload);
}