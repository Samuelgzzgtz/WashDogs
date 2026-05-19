package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class CsvParserTest {
    @Test fun `parses english headers name and phone`() {
        val csv = "name,phone\nJuan Perez,5512345678"
        val result = CsvParser.parse(csv)
        assertEquals(1, result.size)
        assertEquals("Juan Perez", result[0].name)
        assertEquals("5512345678", result[0].phone)
    }
    @Test fun `parses spanish headers nombre and telefono`() {
        val csv = "nombre,telefono\nAna Lopez,5511111111"
        assertEquals(1, CsvParser.parse(csv).size)
    }
    @Test fun `returns empty when headers not found`() =
        assertEquals(0, CsvParser.parse("col1,col2\nfoo,bar").size)
    @Test fun `skips blank rows`() {
        val csv = "name,phone\nJuan,5512345678\n\nAna,5511111111"
        assertEquals(2, CsvParser.parse(csv).size)
    }
    @Test fun `header lookup is case insensitive`() {
        val csv = "Name,Phone\nCarlos,5533333333"
        assertEquals(1, CsvParser.parse(csv).size)
    }
    @Test fun `returns empty for empty string`() =
        assertEquals(0, CsvParser.parse("").size)
}
