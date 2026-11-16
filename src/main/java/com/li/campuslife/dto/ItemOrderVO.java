package com.li.campuslife.dto;

import com.li.campuslife.entity.ItemOrderDetail;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图：带明细，前端一次拿到列表就能渲染。
 */
@Data
@Builder
public class ItemOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** 单位：分 */
    private Integer totalAmount;

    private Integer status;

    private String statusText;

    private LocalDateTime createTime;

    private List<ItemOrderDetail> details;
}
