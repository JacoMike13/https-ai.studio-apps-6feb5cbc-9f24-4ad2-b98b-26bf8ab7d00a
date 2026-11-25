package com.example

import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.export.EstadisticaMensualCalculator
import com.example.export.ExcelEstadisticaExporter
import com.example.util.MesKey
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.Date
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExcelEstadisticaExporterTest {

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 10, min: Int = 0, sec: Int = 0): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, sec)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `month becomes officially available on the 1st day of the following month`() {
        val oct2026 = MesKey(2026, Calendar.OCTOBER)
        val octEnd = millisAt(2026, Calendar.OCTOBER, 31, 23, 59, 59)
        val novFirst = millisAt(2026, Calendar.NOVEMBER, 1, 0, 0, 0)

        assertFalse(EstadisticaMensualCalculator.isMonthAvailableForOfficialReport(oct2026, octEnd))
        assertTrue(EstadisticaMensualCalculator.isMonthAvailableForOfficialReport(oct2026, novFirst))
        assertTrue(EstadisticaMensualCalculator.isFirstDayOfMonth(novFirst))
        assertEquals(oct2026, EstadisticaMensualCalculator.previousClosedMonth(novFirst))
    }

    @Test
    fun `builds valid xlsx workbook with statistics, detail sheet, and native charts`() {
        val oct10 = millisAt(2026, Calendar.OCTOBER, 10, 9, 30)
        val oct20 = millisAt(2026, Calendar.OCTOBER, 20, 14, 0)
        val nov05 = millisAt(2026, Calendar.NOVEMBER, 5, 11, 0)
        val dec01 = millisAt(2026, Calendar.DECEMBER, 1, 8, 0)

        val audiencias = listOf(
            Audiencia(
                id = "aud_oct_1",
                userId = "u1",
                nuc = "NUC-100/2026",
                numeroCausa = "CP-10/2026",
                tipoAudiencia = "Control de Detención",
                fechaHoraMillis = oct10,
                salaOJuzgado = "Sala 1",
                juez = "Lic. Roberto Mendoza",
                ministerioPublico = "Lic. Laura Vázquez",
                tipoDelito = "Secuestro",
                clasificacionJuridica = "Doloso & Agravado",
                estado = EstadoAudiencia.CELEBRADA.code,
                resultado = "Legal detención",
                createdAt = Timestamp(Date(oct10))
            ),
            Audiencia(
                id = "aud_oct_2",
                userId = "u1",
                nuc = "NUC-101/2026",
                numeroCausa = "CP-11/2026",
                tipoAudiencia = "Vinculación a Proceso",
                fechaHoraMillis = oct20,
                salaOJuzgado = "Sala 2",
                juez = "Lic. Roberto Mendoza",
                ministerioPublico = "Lic. Laura Vázquez",
                tipoDelito = "Secuestro",
                clasificacionJuridica = "Coautoría <Art. 9>",
                estado = EstadoAudiencia.PROGRAMADA.code,
                createdAt = Timestamp(Date(oct20))
            ),
            Audiencia(
                id = "aud_nov_1",
                userId = "u1",
                tipoAudiencia = "Juicio Oral",
                fechaHoraMillis = nov05,
                createdAt = Timestamp(Date(nov05))
            )
        )

        val octReport = EstadisticaMensualCalculator.buildReport(
            targetMes = MesKey(2026, Calendar.OCTOBER),
            allAudiencias = audiencias,
            nowMillis = dec01
        )
        assertEquals(2, octReport.totalAudiencias)
        assertEquals(1, octReport.celebradas)
        assertEquals(1, octReport.programadas)
        assertEquals(2, octReport.porTipoAudiencia.size)
        assertEquals("Secuestro", octReport.porDelito.first().nombre)
        assertEquals(2, octReport.porDelito.first().cantidad)
        assertEquals("Lic. Laura Vázquez", octReport.porMinisterioPublico.first().nombre)

        val xlsxBytes = ExcelEstadisticaExporter.buildWorkbookBytes(octReport, chartPngBytes = byteArrayOf(1, 2, 3, 4))
        assertTrue(xlsxBytes.isNotEmpty())

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val expectedParts = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
            "xl/worksheets/sheet2.xml",
            "xl/worksheets/_rels/sheet2.xml.rels",
            "xl/worksheets/sheet3.xml",
            "xl/drawings/drawing1.xml",
            "xl/drawings/_rels/drawing1.xml.rels",
            "xl/charts/chart1.xml",
            "xl/charts/chart2.xml",
            "xl/charts/chart3.xml",
            "xl/charts/chart4.xml",
            "xl/media/graficas_estadisticas.png"
        )
        expectedParts.forEach { part ->
            assertTrue("Missing OOXML part: $part", entries.containsKey(part))
        }

        // Parse every XML/rels file to ensure 100% valid well-formed XML
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, bytes) ->
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            assertTrue("Empty XML document: $name", doc.documentElement != null)
        }

        val sheet1Str = entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet1Str.contains("Control de Detenci"))
        assertTrue(sheet1Str.contains("Lic. Laura V"))

        val sheet3Str = entries.getValue("xl/worksheets/sheet3.xml").toString(Charsets.UTF_8)
        assertTrue(sheet3Str.contains("NUC-100/2026"))
        assertFalse(sheet3Str.contains("Juicio Oral"))

        val chart1Str = entries.getValue("xl/charts/chart1.xml").toString(Charsets.UTF_8)
        assertTrue(chart1Str.contains("Resumen Estadístico"))
    }
}
