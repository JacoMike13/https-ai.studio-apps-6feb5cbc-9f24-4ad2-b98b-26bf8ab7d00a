package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.data.TipoAudienciaPreset
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DiaKey
import com.example.util.FechaHelper
import com.example.util.MesKey

private val DIAS_SEMANA = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@Composable
fun CalendarioMensualCard(
    mesActual: MesKey,
    diaSeleccionado: DiaKey,
    audienciasPorDia: Map<DiaKey, List<Audiencia>>,
    onSelectDia: (DiaKey) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAudiencia: ((Audiencia) -> Unit)? = null,
) {
    val today = remember { FechaHelper.todayDiaKey() }
    val daysInMonth = remember(mesActual) { mesActual.daysInMonth() }
    val leadingOffset = remember(mesActual) { mesActual.firstDayMondayOffset() }
    val totalCells = remember(daysInMonth, leadingOffset) {
        val raw = leadingOffset + daysInMonth
        ((raw + 6) / 7) * 7
    }
    val weeks = remember(totalCells) { (0 until totalCells).chunked(7) }
    val audienciasPorDiaDelMes = remember(audienciasPorDia, mesActual) {
        audienciasPorDia.entries
            .filter { (dia, _) -> dia.year == mesActual.year && dia.month == mesActual.month }
            .sortedBy { it.key }
            .map { (dia, list) -> dia to list.sortedBy { it.fechaHoraMillis } }
    }
    val audienciasDelMes = remember(audienciasPorDiaDelMes) {
        audienciasPorDiaDelMes.flatMap { it.second }
    }
    var showPorFechas by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Mes anterior"
                        )
                    }
                    Text(
                        text = FechaHelper.formatMonthYear(mesActual),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Mes siguiente"
                        )
                    }
                }
                TextButton(
                    onClick = onJumpToToday,
                    modifier = Modifier.testTag("jump_today_button")
                ) {
                    Text(
                        text = "Hoy",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showPorFechas,
                    onClick = { showPorFechas = false },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Cuadrícula") },
                    modifier = Modifier.testTag("calendar_mode_grid")
                )
                FilterChip(
                    selected = showPorFechas,
                    onClick = { showPorFechas = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ViewAgenda,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Por fechas (${audienciasDelMes.size})") },
                    modifier = Modifier.testTag("calendar_mode_by_date")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!showPorFechas) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DIAS_SEMANA.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                weeks.forEach { weekIndices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        weekIndices.forEach { cellIndex ->
                            val dayNum = cellIndex - leadingOffset + 1
                            if (dayNum in 1..daysInMonth) {
                                val diaKey = DiaKey(mesActual.year, mesActual.month, dayNum)
                                val dayItems = audienciasPorDia[diaKey].orEmpty()
                                val hasUrgent24h = dayItems.any {
                                    it.estadoEnum() == EstadoAudiencia.PROGRAMADA &&
                                        FechaHelper.isWithinNext24Hours(it.fechaHoraMillis)
                                }
                                val isSelected = diaKey == diaSeleccionado
                                val isToday = diaKey == today

                                DiaCelda(
                                    dayNumber = dayNum,
                                    items = dayItems,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    hasUrgent24h = hasUrgent24h,
                                    onClick = { onSelectDia(diaKey) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f).height(58.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD97706))
                        )
                        Text(
                            text = "Próximas 24h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (audienciasDelMes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Fechas programadas del mes",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("calendar_month_dates_strip")
                    ) {
                        items(audienciasDelMes, key = { it.id }) { aud ->
                            val audDia = FechaHelper.diaKeyFromMillis(aud.fechaHoraMillis)
                            val isAudDaySelected = audDia == diaSeleccionado
                            CalendarioFechaResumenCard(
                                audiencia = aud,
                                isSelectedDay = isAudDaySelected,
                                onSelect = { onSelectDia(audDia) },
                                onOpenDetail = onOpenAudiencia?.let { open -> { open(aud) } }
                            )
                        }
                    }
                }
            } else {
                if (audienciasPorDiaDelMes.isEmpty()) {
                    Text(
                        text = "No hay audiencias programadas en ${FechaHelper.formatMonthYear(mesActual)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("calendar_by_date_list")
                    ) {
                        audienciasPorDiaDelMes.forEach { (diaKey, itemsDelDia) ->
                            val isDiaSelected = diaKey == diaSeleccionado
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = if (isDiaSelected) Color(0xFF27272E) else Color(0xFF151519),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDiaSelected) Color.White else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectDia(diaKey) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = FechaHelper.formatDayHeader(diaKey),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (itemsDelDia.size == 1) "1 audiencia" else "${itemsDelDia.size} audiencias",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                itemsDelDia.forEach { aud ->
                                    CalendarioFechaDetalleRow(
                                        audiencia = aud,
                                        onClick = {
                                            onSelectDia(diaKey)
                                            onOpenAudiencia?.invoke(aud)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarioFechaResumenCard(
    audiencia: Audiencia,
    isSelectedDay: Boolean,
    onSelect: () -> Unit,
    onOpenDetail: (() -> Unit)?,
) {
    val categoryColor = TipoAudienciaPreset.colorFor(audiencia.tipoAudiencia)
    Surface(
        color = if (isSelectedDay) Color(0xFF1E1E24) else Color(0xFF121216),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelectedDay) 1.5.dp else 1.dp,
            color = if (isSelectedDay) Color.White else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .width(248.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .testTag("calendar_date_summary_${audiencia.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = FechaHelper.formatFullDateTime(audiencia.fechaHoraMillis),
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onOpenDetail != null) {
                    IconButton(onClick = onOpenDetail) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Abrir audiencia",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Text(
                    text = audiencia.tipoAudiencia,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Policy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "MP: ${audiencia.ministerioPublico.ifBlank { "Sin asignar" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CalendarioFechaDetalleRow(
    audiencia: Audiencia,
    onClick: () -> Unit,
) {
    val categoryColor = TipoAudienciaPreset.colorFor(audiencia.tipoAudiencia)
    Surface(
        color = Color(0xFF101014),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("calendar_by_date_row_${audiencia.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FechaHelper.formatFullDateTime(audiencia.fechaHoraMillis),
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                EstadoBadge(estado = audiencia.estadoEnum())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Text(
                    text = audiencia.tipoAudiencia,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Ministerio Público: ${audiencia.ministerioPublico.ifBlank { "Sin asignar" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiaCelda(
    dayNumber: Int,
    items: List<Audiencia>,
    isSelected: Boolean,
    isToday: Boolean,
    hasUrgent24h: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urgentAmber = Color(0xFFF59E0B)
    val sortedItems = remember(items) { items.sortedBy { it.fechaHoraMillis } }
    val distinctColors = remember(sortedItems) {
        sortedItems.map { TipoAudienciaPreset.colorFor(it.tipoAudiencia) }.distinct().take(3)
    }
    val earliestTime = remember(sortedItems) {
        sortedItems.firstOrNull()?.let { FechaHelper.formatTime(it.fechaHoraMillis) }
    }

    val bgColor = when {
        isSelected -> Color(0xFF27272E)
        hasUrgent24h -> Color(0xFF2B2008)
        distinctColors.isNotEmpty() -> Color(0xFF151519)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        hasUrgent24h -> Color(0xFFFDE68A)
        isToday -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderMod = when {
        isSelected -> Modifier.border(1.5.dp, Color.White, RoundedCornerShape(12.dp))
        hasUrgent24h -> Modifier.border(1.5.dp, urgentAmber, RoundedCornerShape(12.dp))
        isToday -> Modifier.border(1.5.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
        else -> Modifier
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(borderMod)
            .clickable(onClick = onClick)
            .testTag("calendar_day_$dayNumber"),
        contentAlignment = Alignment.Center
    ) {
        if (hasUrgent24h) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFFBBF24) else urgentAmber)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday || hasUrgent24h || sortedItems.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (earliestTime != null) {
                Text(
                    text = earliestTime,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
            if (distinctColors.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    distinctColors.forEach { dotColor ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else dotColor)
                        )
                    }
                    if (sortedItems.size > 1) {
                        Text(
                            text = "+${sortedItems.size - 1}",
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun CategoryFilterRow(
    selectedTipo: String?,
    allAudiencias: List<Audiencia>,
    onSelectTipo: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableCategories = remember(allAudiencias) {
        val presets = TipoAudienciaPreset.entries.map { it.label }
        val customTypes = allAudiencias.map { it.tipoAudiencia.trim() }
            .filter { it.isNotBlank() && presets.none { p -> p.equals(it, ignoreCase = true) } }
            .distinct()
        presets + customTypes
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedTipo == null,
                onClick = { onSelectTipo(null) },
                label = { Text("Todas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("filter_tipo_todas")
            )
        }
        items(availableCategories) { tipo ->
            val selected = selectedTipo != null && selectedTipo.equals(tipo, ignoreCase = true)
            val dotColor = TipoAudienciaPreset.colorFor(tipo)
            FilterChip(
                selected = selected,
                onClick = { onSelectTipo(tipo) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                },
                label = {
                    Text(
                        text = TipoAudienciaPreset.shortLabelFor(tipo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = Color.White,
                    selectedContainerColor = Color(0xFF27272E),
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun CalendarSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Buscar audiencias"
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchChange("") },
                    modifier = Modifier.testTag("calendar_search_clear_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Limpiar búsqueda"
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = "Buscar por NUC, Causa Penal o partes...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_search_input")
    )
}

@Composable
fun SearchAndEstadoBar(
    searchQuery: String,
    selectedEstado: EstadoAudiencia?,
    onSearchChange: (String) -> Unit,
    onSelectEstado: (EstadoAudiencia?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Buscar"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Limpiar búsqueda"
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = "Buscar NUC, causa penal, delito, juez, imputado...",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("search_audiencias_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedEstado == null,
                    onClick = { onSelectEstado(null) },
                    label = { Text("Todos los estados") }
                )
            }
            items(EstadoAudiencia.entries) { estado ->
                FilterChip(
                    selected = selectedEstado == estado,
                    onClick = { onSelectEstado(estado) },
                    label = { Text(estado.label) }
                )
            }
        }
    }
}

@Composable
fun EstadoBadge(
    estado: EstadoAudiencia,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (estado) {
        EstadoAudiencia.PROGRAMADA -> Color(0xFF172554) to Color.White
        EstadoAudiencia.CELEBRADA -> Color(0xFF064E3B) to Color.White
        EstadoAudiencia.DIFERIDA -> Color(0xFF451A03) to Color.White
        EstadoAudiencia.SUSPENDIDA -> Color(0xFF450A0A) to Color.White
        EstadoAudiencia.CANCELADA -> Color(0xFF27272A) to Color.White
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Text(
            text = estado.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
