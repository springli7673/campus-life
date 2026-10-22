package com.li.campuslife.controller;

import com.li.campuslife.dto.Result;
import com.li.campuslife.service.ICampusService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校区列表：前端顶部的"豫东大学"下拉用它渲染。
 */
@RestController
@RequestMapping("/campus")
public class CampusController {

    @Resource
    private ICampusService campusService;

    @GetMapping("/list")
    public Result list() {
        return Result.ok(campusService.query()
                .eq("status", 1)
                .orderByAsc("id")
                .list());
    }
}
