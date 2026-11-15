package com.li.campuslife.search;

import lombok.Data;

/**
 * 商户检索请求。
 */
@Data
public class ShopSearchReq {

    private String keyword;
    private Long campusId;
    private Long typeId;
    private Integer page = 1;
    private Integer size = 20;
    /**
     * comprehensive（综合）/ distance / price / sales / comments / score
     */
    private String sortBy = "comprehensive";
    private Double lat;
    private Double lon;
    /**
     * 地理过滤半径，仅当有 lat/lon 或 campusId 时生效
     */
    private String radius = "5km";
    private Long minPrice;
    private Long maxPrice;
}
