package com.li.campuslife.controller;

import com.li.campuslife.dto.ItemSaveDTO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品管理接口（后台）。
 *
 * <p>鉴权由 {@code AdminAuthInterceptor} 用请求头 X-Admin-Token 完成；
 * 每个写操作都会异步触发 ES 索引更新。</p>
 */
@RestController
@RequestMapping("/admin/item")
@RequiredArgsConstructor
public class ItemAdminController {

    private final IItemService itemService;

    @GetMapping("/page")
    public Result page(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false, defaultValue = "1") Integer page,
                       @RequestParam(required = false, defaultValue = "20") Integer size) {
        return itemService.adminPage(keyword, status, page, size);
    }

    @PostMapping
    public Result create(@RequestBody ItemSaveDTO dto) {
        return itemService.saveItem(dto);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id, @RequestBody ItemSaveDTO dto) {
        return itemService.updateItem(id, dto);
    }

    @PutMapping("/{id}/status")
    public Result changeStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        return itemService.changeStatus(id, status);
    }
}
