package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.ItemSaveDTO;
import com.li.campuslife.dto.ItemVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Item;

import java.io.IOException;
import java.util.List;

public interface IItemService extends IService<Item> {

    /**
     * 商品分页检索。数据量 8.8 万，先用 MySQL 实现，后续由 ES 版本替换。
     *
     * @param keyword  匹配商品名或品牌，可为空
     * @param category 类目，为空或「全部」表示不限
     * @param sort     default / sales / priceAsc / priceDesc
     */
    Result page(String keyword, String category, String sort, Integer page, Integer size);

    Result detail(Long id);

    Result recommend(Integer size);

    Result categories();

    /**
     * 全量同步到 ES（游标分批）。返回同步条数。
     */
    int syncToEs() throws IOException;

    // ===== 管理端 =====

    /** 管理列表：包含下架商品，status 为空表示不限 */
    Result adminPage(String keyword, Integer status, Integer page, Integer size);

    Result saveItem(ItemSaveDTO dto);

    Result updateItem(Long id, ItemSaveDTO dto);

    /** 上架 / 下架，变更后异步同步索引 */
    Result changeStatus(Long id, Integer status);

    List<ItemVO> toVOList(List<Item> items);

    ItemVO toVO(Item item);
}
