package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AgendaCompartida
import com.example.data.AppAppearanceConfig
import com.example.data.Audiencia
import com.example.notifications.AudienciaNotificationHelper
import com.example.ui.components.AgendaSwitcherBar
import com.example.ui.components.AudienciaCard
import com.example.ui.components.CalendarSearchBar
import com.example.ui.components.CalendarioMensualCard
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.ConfiguracionGearButton
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.ActiveAgendaInfo
import com.example.ui.viewmodel.AgendaFiltersState
import com.example.ui.viewmodel.filterAudiencias
import com.example.util.DiaKey
import com.example.util.FechaHelper

@Composable
fun CalendarioScreen(
    allAudiencias: List<Audiencia>,
    filters: AgendaFiltersState,
    activeAgendaInfo: ActiveAgendaInfo,
    appAppearance: AppAppearanceConfig,
    ownAgenda: AgendaCompartida?,
    sharedAgendasWithMe: List<AgendaCompartida>,
    currentUserId: String,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSendReminderNow: (List<Audiencia>) -> Unit,
    onSelectAgenda: (String) -> Unit,
    onOpenShareSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectDia: (DiaKey) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    onSelectTipoFilter: (String?) -> Unit,
    onCalendarSearchChange: (String) -> Unit,
    onOpenDetail: (Audiencia) -> Unit,
    onOpenResultado: (Audiencia) -> Unit,
    onOpenEdit: (Audiencia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActiveSearch = filters.calendarSearchQuery.isNotBlank()
    val filteredAudiencias = remember(
        allAudiencias,
        filters.selectedTipoFilter,
        filters.calendarSearchQuery
    ) {
        filterAudiencias(
            items = allAudiencias,
            tipoFilter = filters.selectedTipoFilter,
            estadoFilter = null,
            searchQuery = filters.calendarSearchQuery
        )
    }

    val audienciasPorDia = remember(filteredAudiencias) {
        filteredAudiencias.groupBy { FechaHelper.diaKeyFromMillis(it.fechaHoraMillis) }
    }

    val dayAudiencias = remember(audienciasPorDia, filters.selectedDia) {
        audienciasPorDia[filters.selectedDia].orEmpty().sortedBy { it.fechaHoraMillis }
    }

    val otherMatchingAudiencias = remember(filteredAudiencias, filters.selectedDia, hasActiveSearch) {
        if (!hasActiveSearch) {
            emptyList()
        } else {
            filteredAudiencias
                .filter { FechaHelper.diaKeyFromMillis(it.fechaHoraMillis) != filters.selectedDia }
                .sortedBy { it.fechaHoraMillis }
        }
    }

    val upcoming24h = remember(allAudiencias) {
        AudienciaNotificationHelper.filterUpcoming24h(allAudiencias)
    }

    val isReadOnly = !activeAgendaInfo.isOwnerMode

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Column(modifier = Modifier.fillMaxSize()) {
                AgendaSwitcherBar(
                    activeAgendaInfo = activeAgendaInfo,
                    ownAgenda = ownAgenda,
                    sharedAgendasWithMe = sharedAgendasWithMe,
                    currentUserId = currentUserId,
                    onSelectAgenda = onSelectAgenda,
                    onOpenShareSheet = onOpenShareSheet,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appAppearance.appTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = appAppearance.calendarTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            if (activeAgendaInfo.isOwnerMode) {
                                Spacer(Modifier.width(8.dp))
                                ConfiguracionGearButton(onClick = onOpenSettings)
                            }
                        }
                        CalendarSearchBar(
                            searchQuery = filters.calendarSearchQuery,
                            onSearchChange = onCalendarSearchChange
                        )
                        if (hasActiveSearch) {
                            CalendarSearchSummaryRow(
                                totalMatches = filteredAudiencias.size,
                                onClearSearch = { onCalendarSearchChange("") }
                            )
                        }
                        CategoryFilterRow(
                            selectedTipo = filters.selectedTipoFilter,
                            allAudiencias = allAudiencias,
                            onSelectTipo = onSelectTipoFilter
                        )
                        CalendarioMensualCard(
                            mesActual = filters.selectedMes,
                            diaSeleccionado = filters.selectedDia,
                            audienciasPorDia = audienciasPorDia,
                            onSelectDia = onSelectDia,
                            onPreviousMonth = onPreviousMonth,
                            onNextMonth = onNextMonth,
                            onJumpToToday = onJumpToToday,
                            onOpenAudiencia = onOpenDetail
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(0.52f),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!hasActiveSearch && (upcoming24h.isNotEmpty() || !hasNotificationPermission)) {
                            item {
                                Upcoming24hSpotlightCard(
                                    upcoming = upcoming24h,
                                    hasPermission = hasNotificationPermission,
                                    onRequestPermission = onRequestNotificationPermission,
                                    onNotifyNow = { onSendReminderNow(upcoming24h) },
                                    onSelectHearing = { aud ->
                                        onSelectDia(FechaHelper.diaKeyFromMillis(aud.fechaHoraMillis))
                                        onOpenDetail(aud)
                                    }
                                )
                            }
                        }
                        if (!hasActiveSearch || dayAudiencias.isNotEmpty()) {
                            item {
                                DayAgendaHeader(
                                    dia = filters.selectedDia,
                                    count = dayAudiencias.size
                                )
                            }
                        }
                        if (hasActiveSearch && filteredAudiencias.isEmpty()) {
                            item {
                                EmptySearchCalendarCard(
                                    query = filters.calendarSearchQuery,
                                    onClearSearch = { onCalendarSearchChange("") }
                                )
                            }
                        } else {
                            if (dayAudiencias.isEmpty() && !hasActiveSearch) {
                                item { EmptyDayCard() }
                            } else if (dayAudiencias.isNotEmpty()) {
                                items(dayAudiencias, key = { it.id }) { audiencia ->
                                    AudienciaCard(
                                        audiencia = audiencia,
                                        showFullDate = true,
                                        isReadOnly = isReadOnly,
                                        onOpenDetail = { onOpenDetail(audiencia) },
                                        onOpenResultado = { onOpenResultado(audiencia) },
                                        onOpenEdit = { onOpenEdit(audiencia) }
                                    )
                                }
                            }

                            if (otherMatchingAudiencias.isNotEmpty()) {
                                item {
                                    OtherDatesMatchesHeader(count = otherMatchingAudiencias.size)
                                }
                                items(otherMatchingAudiencias, key = { "match_${it.id}" }) { audiencia ->
                                    AudienciaCard(
                                        audiencia = audiencia,
                                        showFullDate = true,
                                        isReadOnly = isReadOnly,
                                        onOpenDetail = {
                                            onSelectDia(FechaHelper.diaKeyFromMillis(audiencia.fechaHoraMillis))
                                            onOpenDetail(audiencia)
                                        },
                                        onOpenResultado = { onOpenResultado(audiencia) },
                                        onOpenEdit = { onOpenEdit(audiencia) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = appAppearance.appTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appAppearance.calendarTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (activeAgendaInfo.isOwnerMode) {
                                    "${appAppearance.ownerName} · ${allAudiencias.size} audiencias registradas"
                                } else {
                                    "${allAudiencias.size} audiencias en la agenda de ${activeAgendaInfo.ownerName}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeAgendaInfo.isOwnerMode) {
                            Spacer(Modifier.width(8.dp))
                            ConfiguracionGearButton(onClick = onOpenSettings)
                        }
                    }
                }

                item {
                    AgendaSwitcherBar(
                        activeAgendaInfo = activeAgendaInfo,
                        ownAgenda = ownAgenda,
                        sharedAgendasWithMe = sharedAgendasWithMe,
                        currentUserId = currentUserId,
                        onSelectAgenda = onSelectAgenda,
                        onOpenShareSheet = onOpenShareSheet
                    )
                }

                item {
                    CalendarSearchBar(
                        searchQuery = filters.calendarSearchQuery,
                        onSearchChange = onCalendarSearchChange,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (hasActiveSearch) {
                    item {
                        CalendarSearchSummaryRow(
                            totalMatches = filteredAudiencias.size,
                            onClearSearch = { onCalendarSearchChange("") },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (!hasActiveSearch && (upcoming24h.isNotEmpty() || !hasNotificationPermission)) {
                    item {
                        Upcoming24hSpotlightCard(
                            upcoming = upcoming24h,
                            hasPermission = hasNotificationPermission,
                            onRequestPermission = onRequestNotificationPermission,
                            onNotifyNow = { onSendReminderNow(upcoming24h) },
                            onSelectHearing = { aud ->
                                onSelectDia(FechaHelper.diaKeyFromMillis(aud.fechaHoraMillis))
                                onOpenDetail(aud)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item {
                    CategoryFilterRow(
                        selectedTipo = filters.selectedTipoFilter,
                        allAudiencias = allAudiencias,
                        onSelectTipo = onSelectTipoFilter
                    )
                }

                item {
                    CalendarioMensualCard(
                        mesActual = filters.selectedMes,
                        diaSeleccionado = filters.selectedDia,
                        audienciasPorDia = audienciasPorDia,
                        onSelectDia = onSelectDia,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onJumpToToday = onJumpToToday,
                        onOpenAudiencia = onOpenDetail,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (!hasActiveSearch || dayAudiencias.isNotEmpty()) {
                    item {
                        DayAgendaHeader(
                            dia = filters.selectedDia,
                            count = dayAudiencias.size,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (hasActiveSearch && filteredAudiencias.isEmpty()) {
                    item {
                        EmptySearchCalendarCard(
                            query = filters.calendarSearchQuery,
                            onClearSearch = { onCalendarSearchChange("") },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    if (dayAudiencias.isEmpty() && !hasActiveSearch) {
                        item {
                            EmptyDayCard(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    } else if (dayAudiencias.isNotEmpty()) {
                        items(dayAudiencias, key = { it.id }) { audiencia ->
                            AudienciaCard(
                                audiencia = audiencia,
                                showFullDate = true,
                                isReadOnly = isReadOnly,
                                onOpenDetail = { onOpenDetail(audiencia) },
                                onOpenResultado = { onOpenResultado(audiencia) },
                                onOpenEdit = { onOpenEdit(audiencia) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (otherMatchingAudiencias.isNotEmpty()) {
                        item {
                            OtherDatesMatchesHeader(
                                count = otherMatchingAudiencias.size,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(otherMatchingAudiencias, key = { "match_${it.id}" }) { audiencia ->
                            AudienciaCard(
                                audiencia = audiencia,
                                showFullDate = true,
                                isReadOnly = isReadOnly,
                                onOpenDetail = {
                                    onSelectDia(FechaHelper.diaKeyFromMillis(audiencia.fechaHoraMillis))
                                    onOpenDetail(audiencia)
                                },
                                onOpenResultado = { onOpenResultado(audiencia) },
                                onOpenEdit = { onOpenEdit(audiencia) },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Upcoming24hSpotlightCard(
    upcoming: List<Audiencia>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNotifyNow: () -> Unit,
    onSelectHearing: (Audiencia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val urgentAmber = Color(0xFFF59E0B)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("upcoming_24h_spotlight_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111115)
        ),
        border = BorderStroke(1.5.dp, urgentAmber.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (upcoming.isEmpty()) {
                            "Recordatorios locales desactivados"
                        } else {
                            "Próximas 24 horas (${upcoming.size})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (!hasPermission) {
                    TextButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.testTag("activar_alertas_btn")
                    ) {
                        Text(
                            text = "Activar alertas",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (upcoming.isNotEmpty()) {
                    TextButton(
                        onClick = onNotifyNow,
                        modifier = Modifier.testTag("notificar_ahora_btn")
                    ) {
                        Text(
                            text = "Notificar ahora",
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (upcoming.isEmpty()) {
                Text(
                    text = "Activa las notificaciones para recibir alertas locales de tus audiencias pendientes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4D4D8)
                )
            } else {
                upcoming.take(3).forEach { aud ->
                    Surface(
                        color = Color(0xFF1A1A20),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E2E36)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectHearing(aud) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${aud.tipoAudiencia} · ${aud.tipoDelito.ifBlank { aud.nuc.ifBlank { aud.numeroCausa.ifBlank { "Pendiente" } } }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${FechaHelper.formatFullDateTime(aud.fechaHoraMillis)} · MP: ${aud.ministerioPublico.ifBlank { "Sin asignar" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD4D4D8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF2B2008),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = FechaHelper.formatTimeRemaining(aud.fechaHoraMillis),
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgendaHeader(
    dia: DiaKey,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = FechaHelper.formatDayHeader(dia),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (count == 1) "1 audiencia" else "$count audiencias",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyDayCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.empty_day_title),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_day_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarSearchSummaryRow(
    totalMatches: Int,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_search_summary"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (totalMatches) {
                    0 -> "Sin coincidencias para tu búsqueda"
                    1 -> "1 audiencia encontrada en el calendario"
                    else -> "$totalMatches audiencias encontradas en el calendario"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearSearch) {
                Text("Limpiar")
            }
        }
    }
}

@Composable
private fun OtherDatesMatchesHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Coincidencias en la agenda",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (count == 1) "1 audiencia" else "$count audiencias",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptySearchCalendarCard(
    query: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("calendar_search_empty_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Sin coincidencias en el calendario",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "No hay audiencias con NUC, Causa Penal o partes que coincidan con \"${query.trim()}\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onClearSearch,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Limpiar búsqueda")
            }
        }
    }
}

