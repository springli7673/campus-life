package com.li.campuslife.controller;

import com.li.campuslife.dto.CommentAddDTO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.service.IBlogCommentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记评论。查看游客可访问，发表需登录。
 */
@RestController
@RequestMapping("/blog-comments")
@RequiredArgsConstructor
public class BlogCommentsController {

    private final IBlogCommentsService blogCommentsService;

    @GetMapping("/list/{blogId}")
    public Result list(@PathVariable("blogId") Long blogId) {
        return blogCommentsService.listTree(blogId);
    }

    @PostMapping
    public Result add(@RequestBody CommentAddDTO dto) {
        return blogCommentsService.add(dto);
    }
}
