package com.shengyu.music.common;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size) {
    public static int safePage(int page) { return Math.max(1, page); }
    public static int safeSize(int size) { return Math.max(1, Math.min(100, size)); }
}
