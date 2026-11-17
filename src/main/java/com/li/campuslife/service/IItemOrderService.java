package com.li.campuslife.service;

import com.li.campuslife.dto.ItemOrderCreateDTO;
import com.li.campuslife.dto.Result;

public interface IItemOrderService {

    /** 创建订单：校验商品、扣库存、写主表与明细，返回订单号 */
    Result create(ItemOrderCreateDTO dto);

    /** 支付（演示环境直接置为已支付） */
    Result pay(String orderNo);

    /** 取消订单并回补库存 */
    Result cancel(String orderNo);

    /** 我的订单，分页 */
    Result listMine(Integer page, Integer size);

    /** 订单详情 */
    Result detail(String orderNo);

    /** 关闭超时未支付订单，供调度任务调用 */
    int cancelTimeoutOrders(int timeoutMinutes);
}
