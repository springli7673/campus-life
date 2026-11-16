package com.li.campuslife.exception;

/**
 * 业务异常：消息直接返回给前端，不打印堆栈。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BizException(String message) {
        super(message);
    }
}
