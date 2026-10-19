package net.klinik.rental.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CSV writer's own rules.
 *
 * ## Why this file exists rather than a test of the report's export
 *
 * Because the export's test would have to build a whole report to reach the quoting, and a
 * report that changed shape would break the quoting test for the wrong reason. The format
 * is a small piece of logic with three rules, and it gets its own check.
 *
 * ## Why the quoting rules are the whole of it
 *
 * Because a CSV is a table, and a cell that shifts a column is a table that is silently
 * wrong: the renter's name lands in the amount column and no reader is told.
 */
class CsvWriterTest {

    @Test
    fun `every cell is quoted`() {
        assertEquals("\"a\",\"b\"\n", CsvWriter.build(listOf(listOf("a", "b"))))
    }

    @Test
    fun `a cell with a comma stays in one column`() {
        assertEquals("\"a, b\",\"c\"\n", CsvWriter.build(listOf(listOf("a, b", "c"))))
    }

    @Test
    fun `a quote inside a cell is doubled`() {
        assertEquals("\"say \"\"hi\"\"\"\n", CsvWriter.build(listOf(listOf("say \"hi\""))))
    }

    @Test
    fun `a newline inside a cell is carried inside its quotes`() {
        // The cell's newline is *inside* the quotes, so a parser keeps it in one cell. The
        // raw text is two lines long — which is why the check is on the quoting and not on
        // the line count: splitting the text on newlines is not how a CSV is read.
        assertEquals("\"a\nb\"\n", CsvWriter.build(listOf(listOf("a\nb"))))
    }

    @Test
    fun `a null cell is an empty cell and not the word null`() {
        assertEquals("\"\",\"a\"\n", CsvWriter.build(listOf(listOf(null, "a"))))
    }

    @Test
    fun `every row ends with a newline`() {
        val csv = CsvWriter.build(listOf(listOf("a"), listOf("b")))
        assertTrue("the last row is terminated", csv.endsWith("\n"))
        assertEquals(2, csv.trimEnd().lines().size)
    }

    @Test
    fun `an empty row is an empty line`() {
        assertEquals("\n", CsvWriter.build(listOf(emptyList())))
    }

    @Test
    fun `the byte order mark is the one Excel needs`() {
        // U+FEFF, encoded in UTF-8. A BOM-less UTF-8 CSV reads as Latin-1 in Excel on
        // Windows, which turns every Indonesian character into mojibake.
        assertEquals("﻿", CsvWriter.BYTE_ORDER_MARK)
    }
}
