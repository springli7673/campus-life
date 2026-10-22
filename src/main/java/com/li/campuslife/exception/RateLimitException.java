package com.li.campuslife.exception;

/**
 * 触发限流，由 {@code WebExceptionAdvice} 转成 HTTP 429。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
