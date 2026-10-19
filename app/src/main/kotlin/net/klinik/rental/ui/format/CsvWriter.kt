package net.klinik.rental.ui.format

/**
 * Writes rows of cells as CSV.
 *
 * ## Why this is hand-written rather than a library
 *
 * Because the app declares **zero** dependencies for this, and CSV is a format with a
 * ten-line specification: quote every cell, double the quotes inside, join with commas.
 * A library would be a dependency whose only job is those ten lines — and the epic context
 * names every added dependency as a cost the owner carries.
 *
 * ## Why every cell is quoted
 *
 * Because the alternative is a rule to remember: quote only when the cell contains a comma,
 * a quote or a newline. A cell that *looks* safe today and grows a comma tomorrow becomes a
 * row that silently shifts every column after it — a spreadsheet with a renter's name in the
 * amount column and no error anywhere. Quoting unconditionally makes that impossible.
 *
 * ## Why the rows end with a newline
 *
 * Because the last line of a file with no trailing newline is read by some tools as a line
 * without its terminator, which is a row that may be dropped. The trailing newline is one
 * character and removes the question.
 */
object CsvWriter {

    /**
     * The byte-order mark the prototype's `CSV.download` writes.
     *
     * `﻿` is U+FEFF, the BOM, encoded in UTF-8. It is here because **Excel** on
     * Windows reads a BOM-less UTF-8 CSV as Latin-1, which turns every Indonesian
     * character into mojibake — and the owner opening the export in Excel is the whole
     * reason they exported it. The prototype has the same line, for the same reason.
     */
    const val BYTE_ORDER_MARK = "﻿"

    /**
     * Renders [rows] as CSV text.
     *
     * @param rows the rows, each a list of cells. A row's cells may be `null` — which
     *   becomes an empty cell, not the word "null".
     */
    fun build(rows: List<List<String?>>): String {
        val out = StringBuilder()
        for (row in rows) {
            out.append(row.joinToString(",") { cell -> quote(cell.orEmpty()) })
            out.append("\n")
        }
        return out.toString()
    }

    /**
     * One cell, quoted and escaped.
     *
     * A quote inside the cell is doubled — the CSV rule — and the whole cell is wrapped in
     * quotes. A cell that already *is* a quoted string is not special: CSV has no way to say
     * "this cell's quotes are part of its text" other than doubling them, and doubling a
     * quoted string's quotes is exactly right.
     */
    private fun quote(cell: String): String = "\"" + cell.replace("\"", "\"\"") + "\""
}
