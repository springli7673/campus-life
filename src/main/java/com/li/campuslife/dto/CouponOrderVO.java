package com.li.campuslife.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的券：券订单 + 券信息 + 店铺名，前端直接渲染「到店出示」。
 */
@Data
@Builder
public class CouponOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private Long voucherId;

    private String title;

    private String subTitle;

    private String shopName;

    /** 实付，单位分 */
    private Long payValue;

    /** 面额，单位分 */
    private Long actualValue;

    private Integer status;

    private String statusText;

    private LocalDateTime createTime;

    private LocalDateTime useTime;
}
