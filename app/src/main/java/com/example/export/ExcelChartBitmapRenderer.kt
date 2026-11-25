package com.example.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelChartBitmapRenderer {
    private val barPalette = intArrayOf(
        Color.rgb(37, 99, 235),
        Color.rgb(16, 185, 129),
        Color.rgb(139, 92, 246),
        Color.rgb(245, 158, 11),
        Color.rgb(239, 68, 68),
        Color.rgb(6, 182, 212),
        Color.rgb(236, 72, 153),
        Color.rgb(100, 116, 139)
    )

    fun renderDashboardPng(report: EstadisticaMensualReport): ByteArray {
        val width = 1440
        val height = 1660
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248, 250, 252))

        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = 20f
        }
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val sectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 18f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val kpiValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val kpiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }

        // Top Banner
        canvas.drawRect(0f, 0f, width.toFloat(), 132f, headerBg)
        canvas.drawText(
            "RELACIÓN GRÁFICA DE ESTADÍSTICA MENSUAL — ${report.mesLabel.uppercase(Locale("es", "MX"))}",
            40f,
            56f,
            titlePaint
        )
        val genDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX")).format(Date(report.generatedAtMillis))
        canvas.drawText(
            "${report.periodoLabel}  ·  Generado: $genDate  ·  Total: ${report.totalAudiencias} audiencias",
            40f,
            96f,
            subTitlePaint
        )

        // KPI Row
        val kpis = listOf(
            "Total" to report.totalAudiencias.toString(),
            "Programadas" to report.programadas.toString(),
            "Celebradas" to report.celebradas.toString(),
            "Diferidas/Susp." to (report.diferidas + report.suspendidas).toString(),
            "Auto-agendadas" to report.autoAgendadas.toString()
        )
        val kpiGap = 18f
        val kpiMargin = 40f
        val kpiWidth = (width - kpiMargin * 2 - kpiGap * (kpis.size - 1)) / kpis.size
        kpis.forEachIndexed { idx, (label, value) ->
            val left = kpiMargin + idx * (kpiWidth + kpiGap)
            val rect = RectF(left, 156f, left + kpiWidth, 258f)
            canvas.drawRoundRect(rect, 14f, 14f, cardBg)
            canvas.drawRoundRect(rect, 14f, 14f, cardBorder)
            val cx = left + kpiWidth / 2f
            canvas.drawText(value, cx, 210f, kpiValuePaint)
            canvas.drawText(label, cx, 240f, kpiLabelPaint)
        }

        // Panel 1 (Left Top): Tipo de Audiencia
        drawHorizontalBarPanel(
            canvas = canvas,
            bounds = RectF(40f, 282f, 704f, 760f),
            title = "1. Distribución por Tipo de Audiencia",
            items = report.porTipoAudiencia.take(8).map {
                Triple(it.tipoAudiencia, it.total, it.porcentaje)
            },
            emptyMessage = "Sin registros en este mes",
            cardBg = cardBg,
            cardBorder = cardBorder,
            sectionTitle = sectionTitle,
            labelPaint = labelPaint,
            valuePaint = valuePaint,
            trackPaint = trackPaint,
            barPaint = barPaint
        )

        // Panel 2 (Right Top): Estado de Audiencias
        drawHorizontalBarPanel(
            canvas = canvas,
            bounds = RectF(736f, 282f, 1400f, 760f),
            title = "2. Distribución por Estado",
            items = report.porEstado.map {
                Triple(it.nombre, it.cantidad, it.porcentaje)
            },
            emptyMessage = "Sin registros en este mes",
            cardBg = cardBg,
            cardBorder = cardBorder,
            sectionTitle = sectionTitle,
            labelPaint = labelPaint,
            valuePaint = valuePaint,
            trackPaint = trackPaint,
            barPaint = barPaint
        )

        // Panel 3 (Left Middle): Tipo de Delito
        drawHorizontalBarPanel(
            canvas = canvas,
            bounds = RectF(40f, 784f, 704f, 1210f),
            title = "3. Incidencia por Tipo de Delito",
            items = report.porDelito.take(7).map {
                Triple(it.nombre, it.cantidad, it.porcentaje)
            },
            emptyMessage = "Sin delitos registrados",
            cardBg = cardBg,
            cardBorder = cardBorder,
            sectionTitle = sectionTitle,
            labelPaint = labelPaint,
            valuePaint = valuePaint,
            trackPaint = trackPaint,
            barPaint = barPaint
        )

        // Panel 4 (Right Middle): Ministerio Público & Juez
        drawHorizontalBarPanel(
            canvas = canvas,
            bounds = RectF(736f, 784f, 1400f, 1210f),
            title = "4. Carga por Ministerio Público",
            items = report.porMinisterioPublico.take(7).map {
                Triple(it.nombre, it.cantidad, it.porcentaje)
            },
            emptyMessage = "Sin Ministerio Público asignado",
            cardBg = cardBg,
            cardBorder = cardBorder,
            sectionTitle = sectionTitle,
            labelPaint = labelPaint,
            valuePaint = valuePaint,
            trackPaint = trackPaint,
            barPaint = barPaint
        )

        // Panel 5 (Bottom Full Width): Actividad Diaria del Mes
        drawDailyColumnPanel(
            canvas = canvas,
            bounds = RectF(40f, 1234f, 1400f, 1620f),
            title = "5. Relación Diaria de Audiencias en ${report.mesLabel}",
            days = report.porDia,
            cardBg = cardBg,
            cardBorder = cardBorder,
            sectionTitle = sectionTitle,
            labelPaint = labelPaint,
            valuePaint = valuePaint,
            barPaint = barPaint
        )

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun drawHorizontalBarPanel(
        canvas: Canvas,
        bounds: RectF,
        title: String,
        items: List<Triple<String, Int, Double>>,
        emptyMessage: String,
        cardBg: Paint,
        cardBorder: Paint,
        sectionTitle: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        trackPaint: Paint,
        barPaint: Paint,
    ) {
        canvas.drawRoundRect(bounds, 16f, 16f, cardBg)
        canvas.drawRoundRect(bounds, 16f, 16f, cardBorder)
        canvas.drawText(title, bounds.left + 24f, bounds.top + 40f, sectionTitle)

        if (items.isEmpty() || items.all { it.second == 0 }) {
            canvas.drawText(emptyMessage, bounds.left + 24f, bounds.top + 92f, labelPaint)
            return
        }

        val maxCount = items.maxOf { it.second }.coerceAtLeast(1)
        val startY = bounds.top + 74f
        val availableHeight = bounds.bottom - startY - 20f
        val rowHeight = (availableHeight / items.size.coerceAtLeast(1)).coerceAtMost(54f)
        val barLeft = bounds.left + 24f
        val barWidth = bounds.width() - 48f

        items.forEachIndexed { idx, (name, count, pct) ->
            val top = startY + idx * rowHeight
            val clippedName = if (name.length > 34) name.take(33) + "…" else name
            val pctText = String.format(Locale("es", "MX"), "%.1f%%", pct * 100.0)
            canvas.drawText(clippedName, barLeft, top + 16f, labelPaint)
            val rightText = "$count ($pctText)"
            val rightWidth = valuePaint.measureText(rightText)
            canvas.drawText(rightText, bounds.right - 24f - rightWidth, top + 16f, valuePaint)

            val trackRect = RectF(barLeft, top + 24f, barLeft + barWidth, top + 38f)
            canvas.drawRoundRect(trackRect, 7f, 7f, trackPaint)

            val fillRatio = count.toFloat() / maxCount.toFloat()
            val fillWidth = (barWidth * fillRatio).coerceAtLeast(if (count > 0) 8f else 0f)
            if (fillWidth > 0f) {
                barPaint.color = barPalette[idx % barPalette.size]
                val fillRect = RectF(barLeft, top + 24f, barLeft + fillWidth, top + 38f)
                canvas.drawRoundRect(fillRect, 7f, 7f, barPaint)
            }
        }
    }

    private fun drawDailyColumnPanel(
        canvas: Canvas,
        bounds: RectF,
        title: String,
        days: List<DiaCount>,
        cardBg: Paint,
        cardBorder: Paint,
        sectionTitle: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
        barPaint: Paint,
    ) {
        canvas.drawRoundRect(bounds, 16f, 16f, cardBg)
        canvas.drawRoundRect(bounds, 16f, 16f, cardBorder)
        canvas.drawText(title, bounds.left + 24f, bounds.top + 40f, sectionTitle)

        if (days.isEmpty()) return

        val chartLeft = bounds.left + 44f
        val chartRight = bounds.right - 28f
        val chartTop = bounds.top + 78f
        val chartBottom = bounds.bottom - 52f
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(40f)
        val slotWidth = (chartRight - chartLeft) / days.size.toFloat()
        val maxVal = days.maxOf { it.cantidad }.coerceAtLeast(1)

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 2f
        }
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        val dayTextPaint = Paint(labelPaint).apply {
            textSize = 13f
            textAlign = Paint.Align.CENTER
        }
        val countTextPaint = Paint(valuePaint).apply {
            textSize = 13f
            textAlign = Paint.Align.CENTER
        }

        days.forEachIndexed { idx, dia ->
            val cx = chartLeft + idx * slotWidth + slotWidth / 2f
            val colWidth = (slotWidth * 0.62f).coerceIn(8f, 26f)
            if (dia.cantidad > 0) {
                val h = (dia.cantidad.toFloat() / maxVal.toFloat()) * (chartHeight - 16f)
                barPaint.color = Color.rgb(37, 99, 235)
                val rect = RectF(cx - colWidth / 2f, chartBottom - h, cx + colWidth / 2f, chartBottom)
                canvas.drawRoundRect(rect, 4f, 4f, barPaint)
                canvas.drawText(dia.cantidad.toString(), cx, chartBottom - h - 6f, countTextPaint)
            }
            if (days.size <= 31) {
                canvas.drawText(dia.dayOfMonth.toString(), cx, chartBottom + 22f, dayTextPaint)
            }
        }
    }
}
