package com.li.campuslife.controller;

import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城商品接口。检索实现可替换（MySQL / ES），对外契约不变。
 */
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;

    @GetMapping("/page")
    public Result page(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false, defaultValue = "default") String sort,
                       @RequestParam(required = false, defaultValue = "1") Integer page,
                       @RequestParam(required = false, defaultValue = "10") Integer size) {
        return itemService.page(keyword, category, sort, page, size);
    }

    @GetMapping("/recommend")
    public Result recommend(@RequestParam(required = false, defaultValue = "6") Integer size) {
        return itemService.recommend(size);
    }

    @GetMapping("/categories")
    public Result categories() {
        return itemService.categories();
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return itemService.detail(id);
    }
}
