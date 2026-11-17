package com.li.campuslife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.campuslife.dto.CommentAddDTO;
import com.li.campuslife.dto.Result;
import com.li.campuslife.entity.BlogComments;

/**
 * 笔记评论。
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /** 按笔记查评论：一级评论带回复，时间升序 */
    Result listTree(Long blogId);

    /** 发表评论或回复，并把笔记的评论数 +1 */
    Result add(CommentAddDTO dto);
}
