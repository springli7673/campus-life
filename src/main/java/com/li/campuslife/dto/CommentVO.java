package com.li.campuslife.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图：一级评论带 replies，前端直接渲染楼中楼。
 */
@Data
@Builder
public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String name;

    private String icon;

    private String content;

    /** 回复的目标用户名，一级评论为空 */
    private String answerName;

    private Integer liked;

    private LocalDateTime createTime;

    private List<CommentVO> replies;
}
