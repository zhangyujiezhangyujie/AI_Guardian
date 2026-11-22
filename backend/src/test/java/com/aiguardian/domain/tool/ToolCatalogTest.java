package com.aiguardian.domain.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCatalogTest {

    private final ToolCatalog catalog = new ToolCatalog();

    @Test
    void searchKeywordIsTrimmedAndLimitedTo50Characters() {
        assertEquals("年假", catalog.validate("search_document", Map.of("keyword", "  年假 ")).orElseThrow().keyword());
        assertTrue(catalog.validate("search_document", Map.of("keyword", "字".repeat(50))).isPresent());
        assertFalse(catalog.validate("search_document", Map.of("keyword", "字".repeat(51))).isPresent());
        assertFalse(catalog.validate("search_document", Map.of("keyword", "   ")).isPresent());
        assertFalse(catalog.validate("search_document", Map.of("keyword", 123)).isPresent());
    }

    @Test
    void recordIdMustBeAPositiveJsonInteger() {
        assertEquals(1001L, catalog.validate("delete_record", Map.of("recordId", 1001)).orElseThrow().recordId());
        assertEquals(1001L, catalog.validate("delete_record", Map.of("recordId", 1001L)).orElseThrow().recordId());
        assertFalse(catalog.validate("delete_record", Map.of("recordId", "1001")).isPresent());
        assertFalse(catalog.validate("delete_record", Map.of("recordId", 1001.0)).isPresent());
        assertFalse(catalog.validate("delete_record", Map.of("recordId", 0)).isPresent());
        assertFalse(catalog.validate("delete_record", Map.of("recordId", -5)).isPresent());
    }

    @Test
    void updateStatusMustBeOneOfTheAllowedValues() {
        assertEquals("CLOSED", catalog.validate("update_record", Map.of("recordId", 1002, "status", "CLOSED")).orElseThrow().status());
        assertFalse(catalog.validate("update_record", Map.of("recordId", 1002, "status", "DELETED")).isPresent());
        assertFalse(catalog.validate("update_record", Map.of("recordId", 1002, "status", "closed")).isPresent());
    }

    @Test
    void argumentsMustContainExactlyTheDeclaredFields() {
        assertFalse(catalog.validate("delete_record", Map.of()).isPresent());
        assertFalse(catalog.validate("delete_record", Map.of("recordId", 1001, "force", true)).isPresent());
        assertFalse(catalog.validate("update_record", Map.of("recordId", 1001)).isPresent());
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("recordId", null);
        assertFalse(catalog.validate("delete_record", withNull).isPresent());
        assertFalse(catalog.validate("delete_record", null).isPresent());
    }

    @Test
    void unknownToolsAreNotKnownAndWriteToolsAreFlagged() {
        assertFalse(catalog.isKnown("drop_table"));
        assertFalse(catalog.isKnown(null));
        assertTrue(catalog.isWrite("update_record"));
        assertTrue(catalog.isWrite("delete_record"));
        assertFalse(catalog.isWrite("search_document"));
    }
}
