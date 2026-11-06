package com.markettrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.markettrade.market.Pagination;

class PaginationTest {
    @Test
    void totalPagesCalculations() {
        assertEquals(1, Pagination.totalPages(0, 45));
        assertEquals(1, Pagination.totalPages(1, 45));
        assertEquals(1, Pagination.totalPages(45, 45));
        assertEquals(2, Pagination.totalPages(46, 45));
        assertEquals(2, Pagination.totalPages(60, 45));
        assertEquals(2, Pagination.totalPages(90, 45));
        assertEquals(3, Pagination.totalPages(91, 45));
        assertThrows(IllegalArgumentException.class, () -> Pagination.totalPages(10, 0));
    }

    @Test
    void clampPageCalculations() {
        assertEquals(0, Pagination.clampPage(-1, 2));
        assertEquals(0, Pagination.clampPage(0, 2));
        assertEquals(1, Pagination.clampPage(1, 2));
        assertEquals(1, Pagination.clampPage(2, 2));
        assertEquals(1, Pagination.clampPage(99, 2));
        assertEquals(0, Pagination.clampPage(5, 0));
    }

    @Test
    void indexSlicing() {
        assertEquals(0, Pagination.startIndex(0, 45));
        assertEquals(45, Pagination.endIndex(0, 45, 60));

        assertEquals(45, Pagination.startIndex(1, 45));
        assertEquals(60, Pagination.endIndex(1, 45, 60));

        assertEquals(90, Pagination.startIndex(2, 45));
        assertEquals(60, Pagination.endIndex(2, 45, 60));
    }
}
