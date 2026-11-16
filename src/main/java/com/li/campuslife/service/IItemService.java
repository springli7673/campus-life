package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.ItemVO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.Item;

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

    List<ItemVO> toVOList(List<Item> items);

    ItemVO toVO(Item item);
}
