package com.li.campuslife.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 商户检索响应。degraded=true 表示 ES 不可用，本次结果来自 MySQL。
 */
@Data
public class ShopSearchResp {

    private List<ShopDoc> list = new ArrayList<>();
    private long total;
    private int page = 1;
    private int size = 20;
    private Facets facets = new Facets();
    private boolean degraded;

    @Data
    public static class Facets {
        private List<FacetItem> types = new ArrayList<>();
        private List<FacetItem> campuses = new ArrayList<>();
        private List<FacetItem> priceRanges = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacetItem {
        private String key;
        private String label;
        private long count;
    }
}
