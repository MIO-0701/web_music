package com.shengyu.music.common;

import java.util.List;

/** 公共分页结果：封装记录列表、总数、页码和每页条数。 */
public record PageResult<T>(List<T> items, long total, int page, int size) {
    /**
     * 规范化页码。
     * @param page 页码
     * @return 操作结果
     */
    public static int safePage(int page) { return Math.max(1, page); }
    /**
     * 规范化每页条数。
     * @param size 每页条数
     * @return 操作结果
     */
    public static int safeSize(int size) { return Math.max(1, Math.min(100, size)); }
}
