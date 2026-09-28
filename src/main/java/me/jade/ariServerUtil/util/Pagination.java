package me.jade.ariServerUtil.util;

import java.util.ArrayList;
import java.util.List;

public final class Pagination {
    private Pagination() {
    }

    public static <T> List<T> page(List<T> items, int page, int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive");
        }
        int safePage = Math.max(0, page);
        int from = Math.min(items.size(), safePage * perPage);
        int to = Math.min(items.size(), from + perPage);
        return new ArrayList<>(items.subList(from, to));
    }

    public static int totalPages(int totalItems, int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive");
        }
        return Math.max(1, (int) Math.ceil(totalItems / (double) perPage));
    }
}
