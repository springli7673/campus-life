package com.li.campuslife.search;

/**
 * 商户检索。ES 与 MySQL 两个实现，ES 失败时由 EsShopSearchService 降级到 DB。
 */
public interface ShopSearchService {

    ShopSearchResp search(ShopSearchReq req);
}
