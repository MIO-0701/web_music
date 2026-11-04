package com.shengyu.music.common;

import org.springframework.http.HttpStatus;

/** 公共异常模型：携带业务错误消息和对应 HTTP 状态。 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 获取 HTTP 状态。
     * @return 操作结果
     */
    public HttpStatus status() { return status; }

    /**
     * 创建请求参数错误。
     * @param message 用户输入的消息
     * @return 操作结果
     */
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    /**
     * 创建资源不存在错误。
     * @param message 用户输入的消息
     * @return 操作结果
     */
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    /**
     * 创建禁止访问错误。
     * @param message 用户输入的消息
     * @return 操作结果
     */
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
}
