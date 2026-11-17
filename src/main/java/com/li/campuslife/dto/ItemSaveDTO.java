package com.li.campuslife.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 商品新增/修改入参。字段为空表示不修改。
 */
@Data
public class ItemSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    /** 单位：分 */
    private Integer price;

    private Integer stock;

    private String image;

    private String category;

    private String brand;

    private Map<String, String> spec;

    /** 1 上架 2 下架 */
    private Integer status;
}
