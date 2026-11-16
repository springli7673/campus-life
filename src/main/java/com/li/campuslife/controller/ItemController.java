package com.li.campuslife.controller;

import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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

    /**
     * 全量重建商品索引（游标分批，8.8 万条约 1 分钟）。
     * 仅本地/运维使用，上线应改成受保护的运维接口或交给 XXL-JOB。
     */
    @PostMapping("/sync")
    public Result sync() throws IOException {
        long start = System.currentTimeMillis();
        int total = itemService.syncToEs();
        return Result.ok("同步完成：" + total + " 条，耗时 " + (System.currentTimeMillis() - start) + "ms");
    }
}
