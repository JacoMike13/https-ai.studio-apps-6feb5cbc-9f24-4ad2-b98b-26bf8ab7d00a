package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Audiencia
import com.example.data.TipoAudienciaPreset
import com.example.export.EstadisticaMensualCalculator
import com.example.export.ExcelEstadisticaExporter
import com.example.export.ExportedExcelResult
import com.example.util.FechaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MonthlyExcelReportCard(
    allAudiencias: List<Audiencia>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectableMonths = remember(allAudiencias) {
        EstadisticaMensualCalculator.selectableMonths(allAudiencias)
    }
    val defaultClosedMonth = remember { EstadisticaMensualCalculator.previousClosedMonth() }
    var selectedMes by remember(defaultClosedMonth) { mutableStateOf(defaultClosedMonth) }

    val report = remember(selectedMes, allAudiencias) {
        EstadisticaMensualCalculator.buildReport(selectedMes, allAudiencias)
    }
    val isFirstDayToday = remember { EstadisticaMensualCalculator.isFirstDayOfMonth() }
    val daysRemaining = remember(selectedMes) {
        EstadisticaMensualCalculator.daysUntilAvailable(selectedMes)
    }

    var isExporting by remember { mutableStateOf(false) }
    var lastExport by remember { mutableStateOf<ExportedExcelResult?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val safCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExcelEstadisticaExporter.MIME_XLSX)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportError = null
            val res = withContext(Dispatchers.IO) {
                ExcelEstadisticaExporter.writeReportToUri(context, uri, report)
            }
            isExporting = false
            res.onSuccess {
                exportMessage = "Archivo Excel guardado correctamente (${ExcelEstadisticaExporter.suggestedFileName(report.mes)})."
            }.onFailure { err ->
                exportError = err.localizedMessage ?: "No se pudo guardar el archivo Excel."
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TableView,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estadística Mensual en Excel (.xlsx)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Disponible el 1° de cada mes con todos los datos y gráficas del mes anterior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isFirstDayToday) {
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hoy es 1° del mes: ya está disponible el corte oficial de ${FechaHelper.formatMonthYear(defaultClosedMonth)}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Selecciona el mes del reporte",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    contentPadding = PaddingValues(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectableMonths, key = { "${it.year}_${it.month}" }) { mes ->
                        val available = EstadisticaMensualCalculator.isMonthAvailableForOfficialReport(mes)
                        val selected = mes == selectedMes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedMes = mes
                                lastExport = null
                                exportMessage = null
                                exportError = null
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (available) Icons.Rounded.BarChart else Icons.Rounded.LockClock,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (available) {
                                        FechaHelper.formatMonthYear(mes)
                                    } else {
                                        "${FechaHelper.formatMonthYear(mes)} (en curso)"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("excel_month_chip_${mes.year}_${mes.month}")
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF141418),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.mesLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = report.periodoLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = if (report.isAvailableOfficial) Color(0xFF064E3B) else Color(0xFF2B2008),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (report.isAvailableOfficial) Color(0xFF34D399).copy(alpha = 0.5f)
                                else Color(0xFFF59E0B).copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = if (report.isAvailableOfficial) "Disponible" else "Disponible en $daysRemaining d",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniStatChip("Audiencias", report.totalAudiencias.toString(), Modifier.weight(1f))
                        MiniStatChip("Tipos", report.porTipoAudiencia.size.toString(), Modifier.weight(1f))
                        MiniStatChip("Celebradas", report.celebradas.toString(), Modifier.weight(1f))
                        MiniStatChip("Auto-ag.", report.autoAgendadas.toString(), Modifier.weight(1f))
                    }

                    if (report.porTipoAudiencia.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "Vista previa gráfica por tipo de audiencia",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        report.porTipoAudiencia.take(4).forEach { stat ->
                            val color = TipoAudienciaPreset.colorFor(stat.tipoAudiencia)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Text(
                                            text = stat.tipoAudiencia,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${stat.total} (${String.format(java.util.Locale("es", "MX"), "%.0f%%", stat.porcentaje * 100.0)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { stat.porcentaje.toFloat().coerceIn(0f, 1f) },
                                    color = color,
                                    trackColor = color.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }

                    Text(
                        text = "Incluye 3 hojas: Resumen Estadístico, Relación Gráfica (gráficas de tipo de audiencia, estado, delito, Ministerio Público y actividad diaria) y Detalle Completo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!report.isAvailableOfficial) {
                Surface(
                    color = Color(0xFF2B2008),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "El archivo Excel de ${report.mesLabel} estará disponible a partir del ${report.availableFromLabel} para incluir todas las audiencias registradas durante todo el mes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFDE68A),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            scope.launch {
                                isExporting = true
                                exportError = null
                                exportMessage = null
                                val res = withContext(Dispatchers.IO) {
                                    ExcelEstadisticaExporter.exportAndSave(context, report)
                                }
                                isExporting = false
                                res.onSuccess { exported ->
                                    lastExport = exported
                                    exportMessage = if (exported.savedToDownloads) {
                                        "Descargado en Descargas/AgendaAudiencias/${exported.fileName}"
                                    } else {
                                        "Archivo Excel generado (${exported.fileName})."
                                    }
                                }.onFailure { err ->
                                    exportError = err.localizedMessage ?: "No se pudo generar el Excel."
                                }
                            }
                        } else {
                            safCreateDocumentLauncher.launch(ExcelEstadisticaExporter.suggestedFileName(report.mes))
                        }
                    },
                    enabled = report.isAvailableOfficial && !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("download_monthly_excel_btn")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generando Excel y gráficas...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Descargar Excel (${report.mesLabel})")
                    }
                }

                OutlinedButton(
                    onClick = {
                        safCreateDocumentLauncher.launch(ExcelEstadisticaExporter.suggestedFileName(report.mes))
                    },
                    enabled = report.isAvailableOfficial && !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_as_monthly_excel_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar en carpeta / Drive...")
                }
            }

            if (exportMessage != null) {
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = exportMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        lastExport?.let { exported ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val openIntent = ExcelEstadisticaExporter.createOpenIntent(exported.shareableUri)
                                        try {
                                            context.startActivity(openIntent)
                                        } catch (_: ActivityNotFoundException) {
                                            Toast.makeText(
                                                context,
                                                "Instala Excel o Google Sheets para abrir el archivo.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Abrir", color = Color.White)
                                }
                                TextButton(
                                    onClick = {
                                        val shareIntent = Intent.createChooser(
                                            ExcelEstadisticaExporter.createShareIntent(exported.shareableUri, report),
                                            "Compartir estadística mensual"
                                        )
                                        context.startActivity(shareIntent)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Compartir", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            if (exportError != null) {
                Text(
                    text = exportError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF1D1D23),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
