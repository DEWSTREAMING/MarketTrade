package com.markettrade.market;

public final class Pagination {
    private Pagination() {
    }

    public static int totalPages(int itemCount, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (itemCount <= 0) {
            return 1;
        }
        return (itemCount + pageSize - 1) / pageSize;
    }

    public static int clampPage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        if (page < 0) {
            return 0;
        }
        if (page >= totalPages) {
            return totalPages - 1;
        }
        return page;
    }

    public static int startIndex(int page, int pageSize) {
        return Math.max(0, page) * pageSize;
    }

    public static int endIndex(int page, int pageSize, int totalCount) {
        int start = startIndex(page, pageSize);
        if (start >= totalCount) {
            return totalCount;
        }
        return Math.min(start + pageSize, totalCount);
    }
}
