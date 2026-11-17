package com.li.campuslife.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发表评论入参。parentId 为 0 表示一级评论，非 0 表示回复某条一级评论。
 */
@Data
public class CommentAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long blogId;

    private String content;

    /** 0 或空：一级评论 */
    private Long parentId;

    /** 回复的目标评论 id，用于「回复 @某人」 */
    private Long answerId;
}
