package me.jade.ariServerUtil.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {
    @Test
    void returnsExpectedPageAndTotal() {
        List<Integer> values = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(3, 4), Pagination.page(values, 1, 2));
        assertEquals(List.of(5), Pagination.page(values, 2, 2));
        assertEquals(3, Pagination.totalPages(values.size(), 2));
    }
}
