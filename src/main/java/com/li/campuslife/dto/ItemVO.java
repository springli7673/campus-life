package com.li.campuslife.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 商品视图对象：spec 从 JSON 字符串转成 Map，避免前端二次解析。
 */
@Data
@Builder
public class ItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long storeId;

    /** 店铺名，由 storeId 映射（见 ItemServiceImpl.STORE_NAMES） */
    private String storeName;

    private String name;

    /** 单位：分 */
    private Integer price;

    private Integer stock;

    private String image;

    private String category;

    private String brand;

    private Map<String, String> spec;

    private Integer sold;

    private Integer commentCount;
}
