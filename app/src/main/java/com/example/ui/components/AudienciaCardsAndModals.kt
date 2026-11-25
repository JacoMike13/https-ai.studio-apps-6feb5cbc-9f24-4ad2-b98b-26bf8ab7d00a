package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Audiencia
import com.example.data.CLASIFICACIONES_JURIDICAS_SUGERIDAS
import com.example.data.DELITOS_SUGERIDOS
import com.example.data.EstadoAudiencia
import com.example.data.TipoAudienciaPreset
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.FechaHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudienciaCard(
    audiencia: Audiencia,
    showFullDate: Boolean = false,
    isReadOnly: Boolean = false,
    onOpenDetail: () -> Unit,
    onOpenResultado: () -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = TipoAudienciaPreset.colorFor(audiencia.tipoAudiencia)
    val isUrgent24h = audiencia.estadoEnum() == EstadoAudiencia.PROGRAMADA &&
        FechaHelper.isWithinNext24Hours(audiencia.fechaHoraMillis)
    val urgentAmber = Color(0xFFD97706)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenDetail)
            .testTag("audiencia_card_${audiencia.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent24h) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isUrgent24h) {
            BorderStroke(2.dp, urgentAmber)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUrgent24h) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(categoryColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showFullDate) {
                            FechaHelper.formatFullDateTime(audiencia.fechaHoraMillis)
                        } else {
                            "${FechaHelper.formatTime(audiencia.fechaHoraMillis)} hrs"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EstadoBadge(estado = audiencia.estadoEnum())
                }
                Surface(
                    color = categoryColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = audiencia.tipoAudiencia,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (isUrgent24h) {
                    Surface(
                        color = Color(0xFF2B2008),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Próxima en 24 horas",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Próximas 24h · ${FechaHelper.formatTimeRemaining(audiencia.fechaHoraMillis)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            )
                        }
                    }
                }

                if (audiencia.audienciaOrigenId != null || audiencia.nuc.isNotBlank() || audiencia.numeroCausa.isNotBlank() || audiencia.salaOJuzgado.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (audiencia.nuc.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "NUC: ${audiencia.nuc}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (audiencia.numeroCausa.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Causa Penal: ${audiencia.numeroCausa}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (audiencia.salaOJuzgado.isNotBlank()) {
                            Text(
                                text = audiencia.salaOJuzgado,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (audiencia.audienciaOrigenId != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EventRepeat,
                                        contentDescription = "Auto-agendada",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Auto-agendada",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = audiencia.tipoDelito.ifBlank { "Delito por especificar" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (audiencia.clasificacionJuridica.isNotBlank()) {
                        Text(
                            text = audiencia.clasificacionJuridica,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActorMiniItem(
                        label = "Ministerio Público",
                        value = audiencia.ministerioPublico,
                        modifier = Modifier.weight(1f)
                    )
                    ActorMiniItem(
                        label = "Juez",
                        value = audiencia.juez,
                        modifier = Modifier.weight(1f)
                    )
                }
                ActorMiniItem(
                    label = "Imputado",
                    value = audiencia.imputado,
                    modifier = Modifier.fillMaxWidth()
                )

                if (audiencia.resultado.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Resultado: ${audiencia.resultado}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (audiencia.tieneProximaFecha && audiencia.proximaFechaMillis != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Próxima fecha: ${FechaHelper.formatFullDateTime(audiencia.proximaFechaMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (!isReadOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenResultado,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("card_resultado_btn_${audiencia.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AssignmentTurnedIn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (audiencia.resultado.isBlank()) "Resultado" else "Actualizar resultado",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(
                            onClick = onOpenEdit,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("card_editar_btn_${audiencia.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActorMiniItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "Sin asignar" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultadoAudienciaSheet(
    audiencia: Audiencia,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onAutoScheduleProximaFecha: ((
        proximaFechaMillis: Long,
        proximoTipoAudiencia: String,
        resultadoTexto: String,
        nuevoEstado: EstadoAudiencia,
        onComplete: (String?) -> Unit,
    ) -> Unit)? = null,
    onGuardarResultado: (
        nuevoEstado: EstadoAudiencia,
        resultadoTexto: String,
        tieneProximaFecha: Boolean,
        proximaFechaMillis: Long?,
        proximoTipoAudiencia: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var estado by remember(audiencia) {
        mutableStateOf(
            if (audiencia.estadoEnum() == EstadoAudiencia.PROGRAMADA) {
                EstadoAudiencia.CELEBRADA
            } else {
                audiencia.estadoEnum()
            }
        )
    }
    var resultado by remember(audiencia) { mutableStateOf(audiencia.resultado) }
    var tieneProxima by remember(audiencia) { mutableStateOf(audiencia.tieneProximaFecha) }
    var proximaFechaMillis by remember(audiencia) {
        mutableLongStateOf(
            audiencia.proximaFechaMillis ?: (audiencia.fechaHoraMillis + 7L * 24L * 60L * 60L * 1000L)
        )
    }
    var proximoTipo by remember(audiencia) {
        mutableStateOf(audiencia.proximoTipoAudiencia.ifBlank { audiencia.tipoAudiencia })
    }
    var autoScheduledFeedback by remember(audiencia) { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel(icon = Icons.Rounded.AssignmentTurnedIn, title = "Resultado")
            Text(
                text = "${audiencia.tipoAudiencia} · ${audiencia.numeroCausa.ifBlank { audiencia.tipoDelito.ifBlank { "Sin causa" } }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Estado de la audiencia",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EstadoAudiencia.entries.forEach { option ->
                    FilterChip(
                        selected = estado == option,
                        onClick = { estado = option },
                        label = { Text(option.label) }
                    )
                }
            }

            OutlinedTextField(
                value = resultado,
                onValueChange = { resultado = it },
                label = { Text("Resultado / Resolución de la audiencia") },
                placeholder = {
                    Text("Ej. Se califica legal la detención, se vincula a proceso, se difiere audiencia...")
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resultado_texto_input")
            )

            HorizontalDivider()

            SectionLabel(icon = Icons.Rounded.EventRepeat, title = "Próxima Fecha Programada")

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (tieneProxima) {
                                    "Programada para ${FechaHelper.formatFullDateTime(proximaFechaMillis)}"
                                } else {
                                    "Al elegir una fecha se agendará automáticamente en el calendario"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tieneProxima) {
                            TextButton(
                                onClick = {
                                    tieneProxima = false
                                    autoScheduledFeedback = false
                                },
                                modifier = Modifier.testTag("resultado_quitar_proxima_btn")
                            ) {
                                Text("Quitar")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("next_date_picker_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (tieneProxima) FechaHelper.formatShortDate(proximaFechaMillis)
                                else "Elegir fecha"
                            )
                        }
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier
                                .weight(0.75f)
                                .height(48.dp)
                                .testTag("next_time_picker_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${FechaHelper.formatTime(proximaFechaMillis)} hrs")
                        }
                    }

                    OutlinedTextField(
                        value = proximoTipo,
                        onValueChange = { proximoTipo = it },
                        label = { Text("Tipo de la próxima audiencia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TipoAudienciaPreset.entries.take(6).forEach { preset ->
                            FilterChip(
                                selected = proximoTipo.equals(preset.label, ignoreCase = true),
                                onClick = { proximoTipo = preset.label },
                                label = { Text(preset.shortLabel) }
                            )
                        }
                    }

                    if (tieneProxima) {
                        Text(
                            text = if (autoScheduledFeedback) {
                                "Agendada y actualizada automáticamente en el calendario para el ${FechaHelper.formatFullDateTime(proximaFechaMillis)}."
                            } else {
                                "Se agendará automáticamente en el calendario para el ${FechaHelper.formatFullDateTime(proximaFechaMillis)} heredando juez, partes, delito y clasificación jurídica."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onGuardarResultado(
                            estado,
                            resultado,
                            tieneProxima,
                            if (tieneProxima) proximaFechaMillis else null,
                            proximoTipo
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("guardar_resultado_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (tieneProxima) "Guardar y Auto-agendar" else "Guardar Resultado")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialLocalMillis = proximaFechaMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { selectedUtcMillis ->
                val (h, m) = FechaHelper.hourMinuteFromMillis(proximaFechaMillis)
                val newMillis = FechaHelper.combineDateUtcPickerAndTime(selectedUtcMillis, h, m)
                proximaFechaMillis = newMillis
                tieneProxima = true
                showDatePicker = false
                onAutoScheduleProximaFecha?.invoke(newMillis, proximoTipo, resultado, estado) { nextId ->
                    if (nextId != null) {
                        autoScheduledFeedback = true
                    }
                }
            }
        )
    }

    if (showTimePicker) {
        val (h, m) = FechaHelper.hourMinuteFromMillis(proximaFechaMillis)
        SimpleTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            onDismiss = { showTimePicker = false },
            onConfirm = { newH, newM ->
                val newMillis = FechaHelper.updateTimeOnMillis(proximaFechaMillis, newH, newM)
                proximaFechaMillis = newMillis
                showTimePicker = false
                if (tieneProxima) {
                    onAutoScheduleProximaFecha?.invoke(newMillis, proximoTipo, resultado, estado) { nextId ->
                        if (nextId != null) {
                            autoScheduledFeedback = true
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudienciaFormSheet(
    initialAudiencia: Audiencia,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onAutoScheduleProximaFecha: ((Audiencia, onComplete: (String?) -> Unit) -> Unit)? = null,
    onSave: (Audiencia) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = initialAudiencia.id.isNotBlank()

    var tipoAudiencia by remember(initialAudiencia) {
        mutableStateOf(initialAudiencia.tipoAudiencia.ifBlank { TipoAudienciaPreset.CONTROL_DETENCION.label })
    }
    var fechaHoraMillis by remember(initialAudiencia) {
        mutableLongStateOf(
            if (initialAudiencia.fechaHoraMillis > 0L) initialAudiencia.fechaHoraMillis
            else System.currentTimeMillis()
        )
    }
    var nuc by remember(initialAudiencia) { mutableStateOf(initialAudiencia.nuc) }
    var numeroCausa by remember(initialAudiencia) { mutableStateOf(initialAudiencia.numeroCausa) }
    var salaOJuzgado by remember(initialAudiencia) { mutableStateOf(initialAudiencia.salaOJuzgado) }
    var estado by remember(initialAudiencia) { mutableStateOf(initialAudiencia.estadoEnum()) }

    var tipoDelito by remember(initialAudiencia) { mutableStateOf(initialAudiencia.tipoDelito) }
    var clasificacionJuridica by remember(initialAudiencia) { mutableStateOf(initialAudiencia.clasificacionJuridica) }

    var juez by remember(initialAudiencia) { mutableStateOf(initialAudiencia.juez) }
    var ministerioPublico by remember(initialAudiencia) { mutableStateOf(initialAudiencia.ministerioPublico) }
    var asesorJuridico by remember(initialAudiencia) { mutableStateOf(initialAudiencia.asesorJuridico) }
    var victima by remember(initialAudiencia) { mutableStateOf(initialAudiencia.victima) }
    var defensor by remember(initialAudiencia) { mutableStateOf(initialAudiencia.defensor) }
    var imputado by remember(initialAudiencia) { mutableStateOf(initialAudiencia.imputado) }

    var resultado by remember(initialAudiencia) { mutableStateOf(initialAudiencia.resultado) }
    var tieneProximaFecha by remember(initialAudiencia) { mutableStateOf(initialAudiencia.tieneProximaFecha) }
    var proximaFechaMillis by remember(initialAudiencia) {
        mutableLongStateOf(
            initialAudiencia.proximaFechaMillis ?: (fechaHoraMillis + 7L * 24L * 60L * 60L * 1000L)
        )
    }
    var proximoTipoAudiencia by remember(initialAudiencia) {
        mutableStateOf(initialAudiencia.proximoTipoAudiencia.ifBlank { tipoAudiencia })
    }
    var trackedSiguienteId by remember(initialAudiencia.id) {
        mutableStateOf(initialAudiencia.audienciaSiguienteId)
    }
    var autoScheduledLive by remember(initialAudiencia.id) { mutableStateOf(false) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showMainDatePicker by remember { mutableStateOf(false) }
    var showMainTimePicker by remember { mutableStateOf(false) }
    var showNextDatePicker by remember { mutableStateOf(false) }
    var showNextTimePicker by remember { mutableStateOf(false) }

    fun buildFormDraft(
        nextActive: Boolean = tieneProximaFecha,
        nextMillis: Long = proximaFechaMillis,
        nextId: String? = trackedSiguienteId,
    ): Audiencia {
        return initialAudiencia.copy(
            tipoAudiencia = tipoAudiencia.trim().ifBlank { TipoAudienciaPreset.CONTROL_DETENCION.label },
            fechaHoraMillis = fechaHoraMillis,
            nuc = nuc.trim(),
            numeroCausa = numeroCausa.trim(),
            salaOJuzgado = salaOJuzgado.trim(),
            estado = estado.code,
            tipoDelito = tipoDelito.trim(),
            clasificacionJuridica = clasificacionJuridica.trim(),
            juez = juez.trim(),
            ministerioPublico = ministerioPublico.trim(),
            asesorJuridico = asesorJuridico.trim(),
            victima = victima.trim(),
            defensor = defensor.trim(),
            imputado = imputado.trim(),
            resultado = resultado.trim(),
            tieneProximaFecha = nextActive,
            proximaFechaMillis = if (nextActive) nextMillis else null,
            proximoTipoAudiencia = if (nextActive) proximoTipoAudiencia.trim() else "",
            audienciaSiguienteId = nextId
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isEditing) "Editar Audiencia" else "Nueva Audiencia",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            SectionLabel(icon = Icons.Rounded.Gavel, title = "Tipo de Audiencia y Categoría")

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TipoAudienciaPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = tipoAudiencia.equals(preset.label, ignoreCase = true),
                        onClick = { tipoAudiencia = preset.label },
                        label = { Text(preset.label) }
                    )
                }
            }

            OutlinedTextField(
                value = tipoAudiencia,
                onValueChange = { tipoAudiencia = it },
                label = { Text("Tipo de audiencia (editable / personalizado) *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_tipo_audiencia_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showMainDatePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("form_fecha_btn")
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(FechaHelper.formatShortDate(fechaHoraMillis))
                }
                OutlinedButton(
                    onClick = { showMainTimePicker = true },
                    modifier = Modifier
                        .weight(0.75f)
                        .height(48.dp)
                        .testTag("form_hora_btn")
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${FechaHelper.formatTime(fechaHoraMillis)} hrs")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = nuc,
                    onValueChange = { nuc = it },
                    label = { Text("NUC") },
                    placeholder = { Text("NUC-2026-00142") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_nuc_input")
                )
                OutlinedTextField(
                    value = numeroCausa,
                    onValueChange = { numeroCausa = it },
                    label = { Text("Causa Penal") },
                    placeholder = { Text("CP-104/2026") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_causa_penal_input")
                )
            }

            OutlinedTextField(
                value = salaOJuzgado,
                onValueChange = { salaOJuzgado = it },
                label = { Text("Sala / Juzgado") },
                placeholder = { Text("Sala 3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "Estado", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EstadoAudiencia.entries.forEach { option ->
                    FilterChip(
                        selected = estado == option,
                        onClick = { estado = option },
                        label = { Text(option.label) }
                    )
                }
            }

            HorizontalDivider()

            SectionLabel(icon = Icons.Rounded.Balance, title = "Delito y Clasificación Jurídica")

            OutlinedTextField(
                value = tipoDelito,
                onValueChange = { tipoDelito = it },
                label = { Text("Tipo de delito") },
                placeholder = { Text("Ej. Robo calificado, Lesiones, Violencia familiar...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_tipo_delito_input")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DELITOS_SUGERIDOS.take(8).forEach { delito ->
                    FilterChip(
                        selected = tipoDelito.equals(delito, ignoreCase = true),
                        onClick = { tipoDelito = delito },
                        label = { Text(delito) }
                    )
                }
            }

            OutlinedTextField(
                value = clasificacionJuridica,
                onValueChange = { clasificacionJuridica = it },
                label = { Text("Clasificación jurídica del delito") },
                placeholder = { Text("Ej. Doloso, consumado instantáneamente, coautoría directa (Art. 220)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_clasificacion_juridica_input")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CLASIFICACIONES_JURIDICAS_SUGERIDAS.forEach { preset ->
                    FilterChip(
                        selected = clasificacionJuridica.equals(preset, ignoreCase = true),
                        onClick = { clasificacionJuridica = preset },
                        label = { Text(preset) }
                    )
                }
            }

            HorizontalDivider()

            SectionLabel(icon = Icons.Rounded.Badge, title = "Partes Procesales (Observables y Editables)")

            ActorInputField(
                value = juez,
                onValueChange = { juez = it },
                label = "Nombre del Juez",
                icon = Icons.Rounded.AccountBalance,
                tag = "form_juez_input"
            )
            ActorInputField(
                value = ministerioPublico,
                onValueChange = { ministerioPublico = it },
                label = "Ministerio Público",
                icon = Icons.Rounded.Policy,
                tag = "form_mp_input"
            )
            ActorInputField(
                value = asesorJuridico,
                onValueChange = { asesorJuridico = it },
                label = "Asesor Jurídico",
                icon = Icons.Rounded.SupportAgent,
                tag = "form_asesor_input"
            )
            ActorInputField(
                value = victima,
                onValueChange = { victima = it },
                label = "Víctima u Ofendido",
                icon = Icons.Rounded.Person,
                tag = "form_victima_input"
            )
            ActorInputField(
                value = defensor,
                onValueChange = { defensor = it },
                label = "Defensor",
                icon = Icons.Rounded.Shield,
                tag = "form_defensor_input"
            )
            ActorInputField(
                value = imputado,
                onValueChange = { imputado = it },
                label = "Imputado",
                icon = Icons.Rounded.LocationOn,
                tag = "form_imputado_input"
            )

            HorizontalDivider()

            SectionLabel(icon = Icons.Rounded.AssignmentTurnedIn, title = "Resultado")

            OutlinedTextField(
                value = resultado,
                onValueChange = { resultado = it },
                label = { Text("Resultado de la audiencia") },
                placeholder = { Text("Registra aquí la resolución o acuerdos obtenidos...") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_resultado_input")
            )

            HorizontalDivider()

            SectionLabel(icon = Icons.Rounded.EventRepeat, title = "Próxima Fecha Programada")

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (tieneProximaFecha) {
                                "Fecha seleccionada: ${FechaHelper.formatFullDateTime(proximaFechaMillis)}"
                            } else {
                                "Elige una fecha para agendarla y actualizarla automáticamente en el calendario"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (tieneProximaFecha) {
                            TextButton(
                                onClick = {
                                    tieneProximaFecha = false
                                    autoScheduledLive = false
                                },
                                modifier = Modifier.testTag("form_quitar_proxima_btn")
                            ) {
                                Text("Quitar")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNextDatePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("form_proxima_fecha_btn")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (tieneProximaFecha) FechaHelper.formatShortDate(proximaFechaMillis)
                                else "Elegir fecha"
                            )
                        }
                        OutlinedButton(
                            onClick = { showNextTimePicker = true },
                            modifier = Modifier
                                .weight(0.75f)
                                .height(48.dp)
                                .testTag("form_proxima_hora_btn")
                        ) {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${FechaHelper.formatTime(proximaFechaMillis)} hrs")
                        }
                    }

                    OutlinedTextField(
                        value = proximoTipoAudiencia,
                        onValueChange = { proximoTipoAudiencia = it },
                        label = { Text("Tipo de la próxima audiencia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (tieneProximaFecha) {
                        Text(
                            text = when {
                                autoScheduledLive ->
                                    "Actualizada y agendada automáticamente en el calendario para el ${FechaHelper.formatFullDateTime(proximaFechaMillis)}."
                                isEditing ->
                                    "Al cambiar la fecha se actualizará automáticamente en el calendario."
                                else ->
                                    "Se agendará automáticamente en el calendario para el ${FechaHelper.formatFullDateTime(proximaFechaMillis)} al guardar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (tipoAudiencia.trim().isEmpty()) {
                            validationError = "Ingresa el tipo de audiencia."
                            return@Button
                        }
                        validationError = null
                        onSave(buildFormDraft())
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("form_guardar_audiencia_btn")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isEditing) "Actualizar Audiencia" else "Guardar Audiencia")
                    }
                }
            }
        }
    }

    if (showMainDatePicker) {
        SimpleDatePickerDialog(
            initialLocalMillis = fechaHoraMillis,
            onDismiss = { showMainDatePicker = false },
            onConfirm = { utc ->
                val (h, m) = FechaHelper.hourMinuteFromMillis(fechaHoraMillis)
                fechaHoraMillis = FechaHelper.combineDateUtcPickerAndTime(utc, h, m)
                showMainDatePicker = false
            }
        )
    }

    if (showMainTimePicker) {
        val (h, m) = FechaHelper.hourMinuteFromMillis(fechaHoraMillis)
        SimpleTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            onDismiss = { showMainTimePicker = false },
            onConfirm = { newH, newM ->
                fechaHoraMillis = FechaHelper.updateTimeOnMillis(fechaHoraMillis, newH, newM)
                showMainTimePicker = false
            }
        )
    }

    if (showNextDatePicker) {
        SimpleDatePickerDialog(
            initialLocalMillis = proximaFechaMillis,
            onDismiss = { showNextDatePicker = false },
            onConfirm = { utc ->
                val (h, m) = FechaHelper.hourMinuteFromMillis(proximaFechaMillis)
                val newMillis = FechaHelper.combineDateUtcPickerAndTime(utc, h, m)
                proximaFechaMillis = newMillis
                tieneProximaFecha = true
                showNextDatePicker = false
                if (isEditing && tipoAudiencia.trim().isNotEmpty()) {
                    onAutoScheduleProximaFecha?.invoke(
                        buildFormDraft(nextActive = true, nextMillis = newMillis)
                    ) { nextId ->
                        if (nextId != null) {
                            trackedSiguienteId = nextId
                            autoScheduledLive = true
                        }
                    }
                }
            }
        )
    }

    if (showNextTimePicker) {
        val (h, m) = FechaHelper.hourMinuteFromMillis(proximaFechaMillis)
        SimpleTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            onDismiss = { showNextTimePicker = false },
            onConfirm = { newH, newM ->
                val newMillis = FechaHelper.updateTimeOnMillis(proximaFechaMillis, newH, newM)
                proximaFechaMillis = newMillis
                showNextTimePicker = false
                if (isEditing && tieneProximaFecha && tipoAudiencia.trim().isNotEmpty()) {
                    onAutoScheduleProximaFecha?.invoke(
                        buildFormDraft(nextActive = true, nextMillis = newMillis)
                    ) { nextId ->
                        if (nextId != null) {
                            trackedSiguienteId = nextId
                            autoScheduledLive = true
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(
    icon: ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ActorInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePickerDialog(
    initialLocalMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = FechaHelper.localMillisToUtcPickerMillis(initialLocalMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis
                    if (selected != null) onConfirm(selected) else onDismiss()
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar hora") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
