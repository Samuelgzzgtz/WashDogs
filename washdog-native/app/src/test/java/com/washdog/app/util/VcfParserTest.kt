package com.washdog.app.util

import org.junit.Assert.*
import org.junit.Test

class VcfParserTest {
    @Test fun `parses single contact`() {
        val vcf = "BEGIN:VCARD\nVERSION:3.0\nFN:Juan Perez\nTEL;TYPE=CELL:5512345678\nEND:VCARD"
        val result = VcfParser.parse(vcf)
        assertEquals(1, result.size)
        assertEquals("Juan Perez", result[0].name)
        assertEquals("5512345678", result[0].phone)
    }
    @Test fun `parses multiple contacts`() {
        val vcf = "BEGIN:VCARD\nFN:Ana Lopez\nTEL:5511111111\nEND:VCARD\nBEGIN:VCARD\nFN:Carlos Rios\nTEL:5522222222\nEND:VCARD"
        assertEquals(2, VcfParser.parse(vcf).size)
    }
    @Test fun `skips contact without name`() {
        val vcf = "BEGIN:VCARD\nTEL:5512345678\nEND:VCARD"
        assertEquals(0, VcfParser.parse(vcf).size)
    }
    @Test fun `skips contact without phone`() {
        val vcf = "BEGIN:VCARD\nFN:Sin Telefono\nEND:VCARD"
        assertEquals(0, VcfParser.parse(vcf).size)
    }
    @Test fun `trims whitespace from fields`() {
        val vcf = "BEGIN:VCARD\nFN: Maria Garcia \nTEL: +525512345678 \nEND:VCARD"
        val result = VcfParser.parse(vcf)
        assertEquals("Maria Garcia", result[0].name)
        assertEquals("+525512345678", result[0].phone)
    }
}
