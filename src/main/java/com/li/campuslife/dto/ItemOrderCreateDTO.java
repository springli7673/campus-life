package com.li.campuslife.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下单请求：一次可以买多件（购物车结算），也可以只买一件（立即购买）。
 */
@Data
public class ItemOrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Line> items;

    /**
     * 请求幂等号（UUID）。为空则不做幂等，用于兼容老客户端。
     * 同一个 requestId 重复提交只会生成一笔订单。
     */
    private String requestId;

    @Data
    public static class Line implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long itemId;
        private Integer quantity;
    }
}
