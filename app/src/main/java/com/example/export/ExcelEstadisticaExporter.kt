package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.util.FechaHelper
import com.example.util.MesKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportedExcelResult(
    val fileName: String,
    val savedToDownloads: Boolean,
    val downloadsUri: Uri?,
    val shareableUri: Uri,
)

object ExcelEstadisticaExporter {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val localeEs = Locale("es", "MX")

    fun suggestedFileName(mes: MesKey): String {
        val rawMonth = FechaHelper.formatMonthYear(mes)
        val normalized = Normalizer.normalize(rawMonth, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
        return "Estadistica_Audiencias_${normalized}.xlsx"
    }

    fun exportAndSave(context: Context, report: EstadisticaMensualReport): Result<ExportedExcelResult> {
        return runCatching {
            val fileName = suggestedFileName(report.mes)
            val chartPng = runCatching { ExcelChartBitmapRenderer.renderDashboardPng(report) }.getOrNull()
            val workbookBytes = buildWorkbookBytes(report, chartPng)

            val shareFile = File(File(context.cacheDir, "exports").apply { mkdirs() }, fileName)
            shareFile.writeBytes(workbookBytes)
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            var downloadsUri: Uri? = null
            var savedToDownloads = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, MIME_XLSX)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AgendaAudiencias")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(workbookBytes)
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    downloadsUri = uri
                    savedToDownloads = true
                }
            }

            ExportedExcelResult(
                fileName = fileName,
                savedToDownloads = savedToDownloads,
                downloadsUri = downloadsUri,
                shareableUri = shareUri
            )
        }
    }

    fun writeReportToUri(
        context: Context,
        uri: Uri,
        report: EstadisticaMensualReport,
    ): Result<Unit> {
        return runCatching {
            val chartPng = runCatching { ExcelChartBitmapRenderer.renderDashboardPng(report) }.getOrNull()
            val bytes = buildWorkbookBytes(report, chartPng)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: error("No se pudo abrir el destino para guardar el archivo Excel.")
            Unit
        }
    }

    fun createOpenIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_XLSX)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareIntent(uri: Uri, report: EstadisticaMensualReport): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Estadística Mensual de Audiencias - ${report.mesLabel}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Adjunto el archivo Excel con la estadística mensual y relación gráfica de audiencias correspondiente a ${report.mesLabel} (${report.periodoLabel})."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildWorkbookBytes(
        report: EstadisticaMensualReport,
        chartPngBytes: ByteArray? = null,
    ): ByteArray {
        val sheet1Result = buildResumenSheetXml(report)
        val hasPng = chartPngBytes != null && chartPngBytes.isNotEmpty()
        val sheet2Xml = buildGraficaSheetXml(report)
        val sheet3Xml = buildDetalleSheetXml(report)

        val chart1Xml = buildBarChartXml(
            title = "Audiencias por Tipo (${report.mesLabel})",
            seriesName = "Audiencias",
            sheetName = "Resumen Estadístico",
            catStartRow = sheet1Result.tipoStartRow,
            catEndRow = sheet1Result.tipoEndRow,
            valCol = "B",
            categories = sheet1Result.tipoChartItems.map { it.first },
            values = sheet1Result.tipoChartItems.map { it.second },
            colorHex = "2563EB"
        )
        val chart2Xml = buildPieChartXml(
            title = "Distribución por Estado",
            seriesName = "Cantidad",
            sheetName = "Resumen Estadístico",
            catStartRow = sheet1Result.estadoStartRow,
            catEndRow = sheet1Result.estadoEndRow,
            valCol = "B",
            categories = sheet1Result.estadoChartItems.map { it.first },
            values = sheet1Result.estadoChartItems.map { it.second }
        )
        val chart3Xml = buildBarChartXml(
            title = "Incidencia por Tipo de Delito",
            seriesName = "Audiencias",
            sheetName = "Resumen Estadístico",
            catStartRow = sheet1Result.delitoStartRow,
            catEndRow = sheet1Result.delitoEndRow,
            valCol = "B",
            categories = sheet1Result.delitoChartItems.map { it.first },
            values = sheet1Result.delitoChartItems.map { it.second },
            colorHex = "10B981"
        )
        val chart4Xml = buildBarChartXml(
            title = "Audiencias por Ministerio Público",
            seriesName = "Audiencias",
            sheetName = "Resumen Estadístico",
            catStartRow = sheet1Result.mpStartRow,
            catEndRow = sheet1Result.mpEndRow,
            valCol = "B",
            categories = sheet1Result.mpChartItems.map { it.first },
            values = sheet1Result.mpChartItems.map { it.second },
            colorHex = "8B5CF6"
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml(hasPng))
            writeZipEntry(zip, "_rels/.rels", buildRootRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            writeZipEntry(zip, "xl/styles.xml", buildStylesXml())
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", sheet1Result.xml)
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", sheet2Xml)
            writeZipEntry(zip, "xl/worksheets/_rels/sheet2.xml.rels", buildSheet2RelsXml())
            writeZipEntry(zip, "xl/worksheets/sheet3.xml", sheet3Xml)
            writeZipEntry(zip, "xl/drawings/drawing1.xml", buildDrawingXml(hasPng))
            writeZipEntry(zip, "xl/drawings/_rels/drawing1.xml.rels", buildDrawingRelsXml(hasPng))
            writeZipEntry(zip, "xl/charts/chart1.xml", chart1Xml)
            writeZipEntry(zip, "xl/charts/chart2.xml", chart2Xml)
            writeZipEntry(zip, "xl/charts/chart3.xml", chart3Xml)
            writeZipEntry(zip, "xl/charts/chart4.xml", chart4Xml)
            if (hasPng && chartPngBytes != null) {
                zip.putNextEntry(ZipEntry("xl/media/graficas_estadisticas.png"))
                zip.write(chartPngBytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private data class ResumenSheetBuildResult(
        val xml: String,
        val tipoStartRow: Int,
        val tipoEndRow: Int,
        val tipoChartItems: List<Pair<String, Int>>,
        val estadoStartRow: Int,
        val estadoEndRow: Int,
        val estadoChartItems: List<Pair<String, Int>>,
        val delitoStartRow: Int,
        val delitoEndRow: Int,
        val delitoChartItems: List<Pair<String, Int>>,
        val mpStartRow: Int,
        val mpEndRow: Int,
        val mpChartItems: List<Pair<String, Int>>,
    )

    private fun buildResumenSheetXml(report: EstadisticaMensualReport): ResumenSheetBuildResult {
        val sb = StringBuilder()
        val merges = mutableListOf<String>()
        var row = 1

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="34" customWidth="1"/>""")
        sb.append("""<col min="2" max="9" width="15" customWidth="1"/>""")
        sb.append("""<col min="10" max="10" width="16" customWidth="1"/>""")
        sb.append("</cols>")
        sb.append("<sheetData>")

        // Title Banner
        appendRow(sb, row, listOf(CellData("A", "ESTADÍSTICA MENSUAL DE AUDIENCIAS — ${report.mesLabel.uppercase(localeEs)}", 1)))
        merges.add("A$row:J$row")
        row++

        val genText = SimpleDateFormat("dd/MM/yyyy HH:mm", localeEs).format(Date(report.generatedAtMillis))
        val statusTag = if (report.isAvailableOfficial) {
            "Corte oficial disponible desde el ${report.availableFromLabel}"
        } else {
            "Corte preliminar en curso (corte oficial el ${report.availableFromLabel})"
        }
        appendRow(sb, row, listOf(CellData("A", "Periodo: ${report.periodoLabel}  |  $statusTag  |  Generado: $genText", 2)))
        merges.add("A$row:J$row")
        row += 2

        // KPI Summary Block
        appendRow(sb, row, listOf(CellData("A", "INDICADORES GENERALES DEL MES", 3)))
        merges.add("A$row:D$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Concepto", 4),
            CellData("B", "Cantidad", 4),
            CellData("C", "Porcentaje", 4)
        ))
        row++
        val totalD = report.totalAudiencias.coerceAtLeast(1).toDouble()
        val kpis = listOf(
            Triple("Total de audiencias registradas en el mes", report.totalAudiencias, if (report.totalAudiencias > 0) 1.0 else 0.0),
            Triple("Programadas", report.programadas, report.programadas / totalD),
            Triple("Celebradas", report.celebradas, report.celebradas / totalD),
            Triple("Diferidas", report.diferidas, report.diferidas / totalD),
            Triple("Suspendidas", report.suspendidas, report.suspendidas / totalD),
            Triple("Canceladas", report.canceladas, report.canceladas / totalD),
            Triple("Con resultado capturado", report.conResultado, report.conResultado / totalD),
            Triple("Con próxima fecha programada", report.conProximaFecha, report.conProximaFecha / totalD),
            Triple("Auto-agendadas desde resultado previo", report.autoAgendadas, report.autoAgendadas / totalD)
        )
        kpis.forEach { (label, qty, pct) ->
            appendRow(sb, row, listOf(
                CellData("A", label, 5),
                CellData.num("B", qty.toDouble(), 6),
                CellData.num("C", pct, 7)
            ))
            row++
        }
        row++

        // Table 1: Tipo de Audiencia
        appendRow(sb, row, listOf(CellData("A", "1. ESTADÍSTICA COMPLETA POR TIPO DE AUDIENCIA", 3)))
        merges.add("A$row:J$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Tipo de Audiencia", 4),
            CellData("B", "Total", 4),
            CellData("C", "Programadas", 4),
            CellData("D", "Celebradas", 4),
            CellData("E", "Diferidas", 4),
            CellData("F", "Suspendidas", 4),
            CellData("G", "Canceladas", 4),
            CellData("H", "Con Próx. Fecha", 4),
            CellData("I", "Auto-agendadas", 4),
            CellData("J", "% del Total", 4)
        ))
        row++
        val tipoStartRow = row
        val tipoRows = if (report.porTipoAudiencia.isEmpty()) {
            listOf(TipoAudienciaStats("Sin registros en el mes", 0, 0, 0, 0, 0, 0, 0, 0, 0.0))
        } else {
            report.porTipoAudiencia
        }
        tipoRows.forEach { t ->
            appendRow(sb, row, listOf(
                CellData("A", t.tipoAudiencia, 5),
                CellData.num("B", t.total.toDouble(), 6),
                CellData.num("C", t.programadas.toDouble(), 6),
                CellData.num("D", t.celebradas.toDouble(), 6),
                CellData.num("E", t.diferidas.toDouble(), 6),
                CellData.num("F", t.suspendidas.toDouble(), 6),
                CellData.num("G", t.canceladas.toDouble(), 6),
                CellData.num("H", t.conProximaFecha.toDouble(), 6),
                CellData.num("I", t.autoAgendadas.toDouble(), 6),
                CellData.num("J", t.porcentaje, 7)
            ))
            row++
        }
        val tipoEndRow = row - 1
        appendRow(sb, row, listOf(
            CellData("A", "TOTAL GENERAL", 8),
            CellData.num("B", report.totalAudiencias.toDouble(), 9),
            CellData.num("C", report.programadas.toDouble(), 9),
            CellData.num("D", report.celebradas.toDouble(), 9),
            CellData.num("E", report.diferidas.toDouble(), 9),
            CellData.num("F", report.suspendidas.toDouble(), 9),
            CellData.num("G", report.canceladas.toDouble(), 9),
            CellData.num("H", report.conProximaFecha.toDouble(), 9),
            CellData.num("I", report.autoAgendadas.toDouble(), 9),
            CellData.num("J", if (report.totalAudiencias > 0) 1.0 else 0.0, 10)
        ))
        row += 2

        // Table 2: Estado
        appendRow(sb, row, listOf(CellData("A", "2. ESTADÍSTICA POR ESTADO DE AUDIENCIA", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Estado", 4),
            CellData("B", "Cantidad", 4),
            CellData("C", "% del Total", 4)
        ))
        row++
        val estadoStartRow = row
        val estadoRows = report.porEstado.ifEmpty { listOf(CategoriaCount("Sin datos", 0, 0.0)) }
        estadoRows.forEach { item ->
            appendRow(sb, row, listOf(
                CellData("A", item.nombre, 5),
                CellData.num("B", item.cantidad.toDouble(), 6),
                CellData.num("C", item.porcentaje, 7)
            ))
            row++
        }
        val estadoEndRow = row - 1
        row++

        // Table 3: Tipo de Delito
        appendRow(sb, row, listOf(CellData("A", "3. ESTADÍSTICA POR TIPO DE DELITO", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Tipo de Delito", 4),
            CellData("B", "Cantidad", 4),
            CellData("C", "% del Total", 4)
        ))
        row++
        val delitoStartRow = row
        val delitoRows = report.porDelito.ifEmpty { listOf(CategoriaCount("Sin delitos registrados", 0, 0.0)) }
        delitoRows.forEach { item ->
            appendRow(sb, row, listOf(
                CellData("A", item.nombre, 5),
                CellData.num("B", item.cantidad.toDouble(), 6),
                CellData.num("C", item.porcentaje, 7)
            ))
            row++
        }
        val delitoEndRow = row - 1
        row++

        // Table 4: Clasificación Jurídica
        appendRow(sb, row, listOf(CellData("A", "4. ESTADÍSTICA POR CLASIFICACIÓN JURÍDICA", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Clasificación Jurídica", 4),
            CellData("B", "Cantidad", 4),
            CellData("C", "% del Total", 4)
        ))
        row++
        val clasifRows = report.porClasificacionJuridica.ifEmpty { listOf(CategoriaCount("Sin especificar", 0, 0.0)) }
        clasifRows.forEach { item ->
            appendRow(sb, row, listOf(
                CellData("A", item.nombre, 5),
                CellData.num("B", item.cantidad.toDouble(), 6),
                CellData.num("C", item.porcentaje, 7)
            ))
            row++
        }
        row++

        // Table 5: Ministerio Público
        appendRow(sb, row, listOf(CellData("A", "5. ESTADÍSTICA POR MINISTERIO PÚBLICO", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Ministerio Público", 4),
            CellData("B", "Audiencias", 4),
            CellData("C", "% del Total", 4)
        ))
        row++
        val mpStartRow = row
        val mpRows = report.porMinisterioPublico.ifEmpty { listOf(CategoriaCount("Sin asignar", 0, 0.0)) }
        mpRows.forEach { item ->
            appendRow(sb, row, listOf(
                CellData("A", item.nombre, 5),
                CellData.num("B", item.cantidad.toDouble(), 6),
                CellData.num("C", item.porcentaje, 7)
            ))
            row++
        }
        val mpEndRow = row - 1
        row++

        // Table 6: Juez
        appendRow(sb, row, listOf(CellData("A", "6. ESTADÍSTICA POR JUEZ", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Juez", 4),
            CellData("B", "Audiencias", 4),
            CellData("C", "% del Total", 4)
        ))
        row++
        val juezRows = report.porJuez.ifEmpty { listOf(CategoriaCount("Sin asignar", 0, 0.0)) }
        juezRows.forEach { item ->
            appendRow(sb, row, listOf(
                CellData("A", item.nombre, 5),
                CellData.num("B", item.cantidad.toDouble(), 6),
                CellData.num("C", item.porcentaje, 7)
            ))
            row++
        }
        row++

        // Table 7: Distribución Diaria
        appendRow(sb, row, listOf(CellData("A", "7. RELACIÓN DIARIA DEL MES", 3)))
        merges.add("A$row:C$row")
        row++
        appendRow(sb, row, listOf(
            CellData("A", "Fecha", 4),
            CellData("B", "Día del mes", 4),
            CellData("C", "Audiencias", 4)
        ))
        row++
        report.porDia.forEach { dia ->
            appendRow(sb, row, listOf(
                CellData("A", dia.fechaLabel, 5),
                CellData.num("B", dia.dayOfMonth.toDouble(), 6),
                CellData.num("C", dia.cantidad.toDouble(), 6)
            ))
            row++
        }

        sb.append("</sheetData>")
        if (merges.isNotEmpty()) {
            sb.append("""<mergeCells count="${merges.size}">""")
            merges.forEach { ref -> sb.append("""<mergeCell ref="$ref"/>""") }
            sb.append("</mergeCells>")
        }
        sb.append("</worksheet>")

        return ResumenSheetBuildResult(
            xml = sb.toString(),
            tipoStartRow = tipoStartRow,
            tipoEndRow = tipoEndRow,
            tipoChartItems = tipoRows.map { it.tipoAudiencia to it.total },
            estadoStartRow = estadoStartRow,
            estadoEndRow = estadoEndRow,
            estadoChartItems = estadoRows.map { it.nombre to it.cantidad },
            delitoStartRow = delitoStartRow,
            delitoEndRow = delitoEndRow,
            delitoChartItems = delitoRows.map { it.nombre to it.cantidad },
            mpStartRow = mpStartRow,
            mpEndRow = mpEndRow,
            mpChartItems = mpRows.map { it.nombre to it.cantidad }
        )
    }

    private fun buildGraficaSheetXml(report: EstadisticaMensualReport): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("<cols>")
        sb.append("""<col min="1" max="18" width="14" customWidth="1"/>""")
        sb.append("</cols>")
        sb.append("<sheetData>")
        appendRow(sb, 1, listOf(CellData("A", "RELACIÓN GRÁFICA DE ESTADÍSTICAS — ${report.mesLabel.uppercase(localeEs)}", 1)))
        appendRow(sb, 2, listOf(CellData("A", "Gráficas dinámicas vinculadas a 'Resumen Estadístico' + tablero visual completo.", 2)))
        sb.append("</sheetData>")
        sb.append("""<mergeCells count="2"><mergeCell ref="A1:P1"/><mergeCell ref="A2:P2"/></mergeCells>""")
        sb.append("""<drawing r:id="rId1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun buildDetalleSheetXml(report: EstadisticaMensualReport): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="7" customWidth="1"/>""")
        sb.append("""<col min="2" max="3" width="20" customWidth="1"/>""")
        sb.append("""<col min="4" max="6" width="24" customWidth="1"/>""")
        sb.append("""<col min="7" max="8" width="18" customWidth="1"/>""")
        sb.append("""<col min="9" max="10" width="26" customWidth="1"/>""")
        sb.append("""<col min="11" max="16" width="22" customWidth="1"/>""")
        sb.append("""<col min="17" max="17" width="34" customWidth="1"/>""")
        sb.append("""<col min="18" max="19" width="24" customWidth="1"/>""")
        sb.append("</cols>")
        sb.append("<sheetData>")

        var row = 1
        appendRow(sb, row, listOf(CellData("A", "DETALLE DE AUDIENCIAS REGISTRADAS — ${report.mesLabel.uppercase(localeEs)}", 1)))
        row++
        appendRow(sb, row, listOf(CellData("A", "${report.periodoLabel}  |  Total: ${report.totalAudiencias} registros", 2)))
        row++

        val headers = listOf(
            "N°", "NUC", "Causa Penal", "Tipo de Audiencia",
            "Fecha y Hora Programada", "Fecha de Registro (Subida)", "Sala o Juzgado",
            "Estado", "Tipo de Delito", "Clasificación Jurídica",
            "Juez", "Ministerio Público", "Asesor Jurídico",
            "Víctima", "Defensor", "Imputado",
            "Resultado", "Próxima Fecha Programada", "Próximo Tipo de Audiencia"
        )
        appendRow(sb, row, headers.mapIndexed { idx, h -> CellData(colName(idx), h, 4) })
        row++

        if (report.audiencias.isEmpty()) {
            appendRow(sb, row, listOf(CellData("A", "No se registraron audiencias durante ${report.mesLabel}.", 5)))
        } else {
            report.audiencias.forEachIndexed { idx, aud ->
                val createdStr = aud.createdAt?.toDate()?.time?.let { FechaHelper.formatFullDateTime(it) } ?: "Sin fecha de carga"
                val proximaStr = if (aud.tieneProximaFecha && aud.proximaFechaMillis != null) {
                    FechaHelper.formatFullDateTime(aud.proximaFechaMillis)
                } else {
                    ""
                }
                val values = listOf(
                    CellData.num("A", (idx + 1).toDouble(), 6),
                    CellData("B", aud.nuc, 5),
                    CellData("C", aud.numeroCausa, 5),
                    CellData("D", aud.tipoAudiencia, 5),
                    CellData("E", FechaHelper.formatFullDateTime(aud.fechaHoraMillis), 5),
                    CellData("F", createdStr, 5),
                    CellData("G", aud.salaOJuzgado, 5),
                    CellData("H", aud.estadoEnum().label, 5),
                    CellData("I", aud.tipoDelito, 5),
                    CellData("J", aud.clasificacionJuridica, 5),
                    CellData("K", aud.juez, 5),
                    CellData("L", aud.ministerioPublico, 5),
                    CellData("M", aud.asesorJuridico, 5),
                    CellData("N", aud.victima, 5),
                    CellData("O", aud.defensor, 5),
                    CellData("P", aud.imputado, 5),
                    CellData("Q", aud.resultado, 5),
                    CellData("R", proximaStr, 5),
                    CellData("S", aud.proximoTipoAudiencia, 5)
                )
                appendRow(sb, row, values)
                row++
            }
        }

        sb.append("</sheetData>")
        sb.append("""<mergeCells count="2"><mergeCell ref="A1:S1"/><mergeCell ref="A2:S2"/></mergeCells>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun buildBarChartXml(
        title: String,
        seriesName: String,
        sheetName: String,
        catStartRow: Int,
        catEndRow: Int,
        valCol: String,
        categories: List<String>,
        values: List<Int>,
        colorHex: String,
    ): String {
        val catFormula = "'${sheetName}'!\$A\$$catStartRow:\$A\$$catEndRow"
        val valFormula = "'${sheetName}'!\$$valCol\$$catStartRow:\$$valCol\$$catEndRow"
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("""<c:lang val="es-MX"/>""")
            append("<c:chart>")
            appendChartTitle(this, title)
            append("<c:autoTitleDeleted val=\"0\"/>")
            append("<c:plotArea><c:layout/>")
            append("<c:barChart>")
            append("<c:barDir val=\"bar\"/>")
            append("<c:grouping val=\"clustered\"/>")
            append("<c:varyColors val=\"0\"/>")
            append("<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>")
            append("<c:tx><c:v>${escapeXml(seriesName)}</c:v></c:tx>")
            append("<c:spPr><a:solidFill><a:srgbClr val=\"$colorHex\"/></a:solidFill></c:spPr>")
            appendStrCat(this, catFormula, categories)
            appendNumVal(this, valFormula, values)
            append("</c:ser>")
            append("<c:dLbls><c:showVal val=\"1\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/></c:dLbls>")
            append("<c:axId val=\"1\"/><c:axId val=\"2\"/>")
            append("</c:barChart>")
            append("<c:catAx><c:axId val=\"1\"/><c:scaling><c:orientation val=\"maxMin\"/></c:scaling><c:delete val=\"0\"/><c:axPos val=\"l\"/><c:crossAx val=\"2\"/><c:crosses val=\"autoZero\"/></c:catAx>")
            append("<c:valAx><c:axId val=\"2\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"0\"/><c:axPos val=\"b\"/><c:majorGridlines/><c:crossAx val=\"1\"/><c:crosses val=\"autoZero\"/></c:valAx>")
            append("</c:plotArea>")
            append("<c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend>")
            append("<c:plotVisOnly val=\"1\"/>")
            append("</c:chart></c:chartSpace>")
        }
    }

    private fun buildPieChartXml(
        title: String,
        seriesName: String,
        sheetName: String,
        catStartRow: Int,
        catEndRow: Int,
        valCol: String,
        categories: List<String>,
        values: List<Int>,
    ): String {
        val catFormula = "'${sheetName}'!\$A\$$catStartRow:\$A\$$catEndRow"
        val valFormula = "'${sheetName}'!\$$valCol\$$catStartRow:\$$valCol\$$catEndRow"
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("""<c:lang val="es-MX"/>""")
            append("<c:chart>")
            appendChartTitle(this, title)
            append("<c:autoTitleDeleted val=\"0\"/>")
            append("<c:plotArea><c:layout/>")
            append("<c:pieChart>")
            append("<c:varyColors val=\"1\"/>")
            append("<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>")
            append("<c:tx><c:v>${escapeXml(seriesName)}</c:v></c:tx>")
            appendStrCat(this, catFormula, categories)
            appendNumVal(this, valFormula, values)
            append("</c:ser>")
            append("<c:dLbls><c:showPercent val=\"1\"/><c:showVal val=\"1\"/><c:showCatName val=\"1\"/><c:showSerName val=\"0\"/></c:dLbls>")
            append("<c:firstSliceAng val=\"0\"/>")
            append("</c:pieChart>")
            append("</c:plotArea>")
            append("<c:legend><c:legendPos val=\"r\"/><c:overlay val=\"0\"/></c:legend>")
            append("<c:plotVisOnly val=\"1\"/>")
            append("</c:chart></c:chartSpace>")
        }
    }

    private fun appendChartTitle(sb: StringBuilder, title: String) {
        sb.append("<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"es-MX\" sz=\"1200\" b=\"1\"/><a:t>")
        sb.append(escapeXml(title))
        sb.append("</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title>")
    }

    private fun appendStrCat(sb: StringBuilder, formula: String, categories: List<String>) {
        sb.append("<c:cat><c:strRef><c:f>${escapeXml(formula)}</c:f>")
        sb.append("<c:strCache><c:ptCount val=\"${categories.size}\"/>")
        categories.forEachIndexed { i, cat ->
            sb.append("<c:pt idx=\"$i\"><c:v>${escapeXml(cat)}</c:v></c:pt>")
        }
        sb.append("</c:strCache></c:strRef></c:cat>")
    }

    private fun appendNumVal(sb: StringBuilder, formula: String, values: List<Int>) {
        sb.append("<c:val><c:numRef><c:f>${escapeXml(formula)}</c:f>")
        sb.append("<c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>")
        values.forEachIndexed { i, v ->
            sb.append("<c:pt idx=\"$i\"><c:v>$v</c:v></c:pt>")
        }
        sb.append("</c:numCache></c:numRef></c:val>")
    }

    private fun buildDrawingXml(hasPng: Boolean): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            appendChartAnchor(this, id = 2, name = "GraficaTipoAudiencia", rId = "rId1", fromCol = 0, fromRow = 3, toCol = 8, toRow = 21)
            appendChartAnchor(this, id = 3, name = "GraficaEstado", rId = "rId2", fromCol = 9, fromRow = 3, toCol = 17, toRow = 21)
            appendChartAnchor(this, id = 4, name = "GraficaDelito", rId = "rId3", fromCol = 0, fromRow = 22, toCol = 8, toRow = 40)
            appendChartAnchor(this, id = 5, name = "GraficaMinisterioPublico", rId = "rId4", fromCol = 9, fromRow = 22, toCol = 17, toRow = 40)
            if (hasPng) {
                appendPictureAnchor(this, id = 6, name = "TableroVisualEstadisticas", rId = "rId5", fromCol = 0, fromRow = 42, toCol = 15, toRow = 88)
            }
            append("</xdr:wsDr>")
        }
    }

    private fun appendChartAnchor(
        sb: StringBuilder,
        id: Int,
        name: String,
        rId: String,
        fromCol: Int,
        fromRow: Int,
        toCol: Int,
        toRow: Int,
    ) {
        sb.append("<xdr:twoCellAnchor>")
        sb.append("<xdr:from><xdr:col>$fromCol</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$fromRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
        sb.append("<xdr:to><xdr:col>$toCol</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$toRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
        sb.append("<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"$id\" name=\"$name\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>")
        sb.append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>")
        sb.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">")
        sb.append("<c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" r:id=\"$rId\"/>")
        sb.append("</a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>")
    }

    private fun appendPictureAnchor(
        sb: StringBuilder,
        id: Int,
        name: String,
        rId: String,
        fromCol: Int,
        fromRow: Int,
        toCol: Int,
        toRow: Int,
    ) {
        sb.append("<xdr:twoCellAnchor editAs=\"oneCell\">")
        sb.append("<xdr:from><xdr:col>$fromCol</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$fromRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
        sb.append("<xdr:to><xdr:col>$toCol</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$toRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
        sb.append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"$id\" name=\"$name\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
        sb.append("<xdr:blipFill><a:blip r:embed=\"$rId\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
        sb.append("<xdr:spPr><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor>")
    }

    private fun buildContentTypesXml(hasPng: Boolean): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            if (hasPng) {
                append("""<Default Extension="png" ContentType="image/png"/>""")
            }
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            append("""<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            append("""<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            append("""<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            append("""<Override PartName="/xl/charts/chart1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
            append("""<Override PartName="/xl/charts/chart2.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
            append("""<Override PartName="/xl/charts/chart3.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
            append("""<Override PartName="/xl/charts/chart4.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>""")
            append("</Types>")
        }
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Resumen Estadístico" sheetId="1" r:id="rId1"/>
    <sheet name="Relación Gráfica" sheetId="2" r:id="rId2"/>
    <sheet name="Detalle de Audiencias" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildSheet2RelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>"""
    }

    private fun buildDrawingRelsXml(hasPng: Boolean): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart1.xml"/>""")
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart2.xml"/>""")
            append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart3.xml"/>""")
            append("""<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart4.xml"/>""")
            if (hasPng) {
                append("""<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/graficas_estadisticas.png"/>""")
            }
            append("</Relationships>")
        }
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="6">
    <font><sz val="10"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><i/><sz val="10"/><color rgb="FF475569"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="7">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E293B"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF334155"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE2E8F0"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="11">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <xf numFmtId="0" fontId="4" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
    <xf numFmtId="1" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="10" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="5" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
    <xf numFmtId="1" fontId="5" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="10" fontId="5" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
    }

    private data class CellData(
        val col: String,
        val value: String,
        val styleId: Int,
        val isNumeric: Boolean = false,
    ) {
        companion object {
            fun num(col: String, number: Double, styleId: Int): CellData {
                val formatted = if (number == number.toLong().toDouble()) {
                    number.toLong().toString()
                } else {
                    String.format(Locale.US, "%.4f", number)
                }
                return CellData(col, formatted, styleId, isNumeric = true)
            }
        }
    }

    private fun appendRow(sb: StringBuilder, rowNum: Int, cells: List<CellData>) {
        sb.append("<row r=\"$rowNum\">")
        cells.forEach { cell ->
            if (cell.isNumeric) {
                sb.append("<c r=\"${cell.col}$rowNum\" s=\"${cell.styleId}\"><v>${cell.value}</v></c>")
            } else {
                sb.append("<c r=\"${cell.col}$rowNum\" s=\"${cell.styleId}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(cell.value)}</t></is></c>")
            }
        }
        sb.append("</row>")
    }

    private fun colName(index: Int): String {
        var idx = index
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A' + (idx % 26)))
            idx = idx / 26 - 1
        }
        return sb.toString()
    }

    private fun escapeXml(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    if (code == 0x9 || code == 0xA || code == 0xD || code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
