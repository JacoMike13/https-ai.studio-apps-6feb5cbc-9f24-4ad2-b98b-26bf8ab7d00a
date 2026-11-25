package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.AgendaCompartida
import com.example.data.AppAppearanceConfig
import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.ui.components.AgendaSwitcherBar
import com.example.ui.components.AudienciaCard
import com.example.ui.components.CategoryFilterRow
import com.example.ui.components.ConfiguracionGearButton
import com.example.ui.components.SearchAndEstadoBar
import com.example.ui.viewmodel.ActiveAgendaInfo
import com.example.ui.viewmodel.AgendaFiltersState
import com.example.ui.viewmodel.filterAudiencias

@Composable
fun DirectorioScreen(
    allAudiencias: List<Audiencia>,
    filters: AgendaFiltersState,
    activeAgendaInfo: ActiveAgendaInfo,
    appAppearance: AppAppearanceConfig,
    ownAgenda: AgendaCompartida?,
    sharedAgendasWithMe: List<AgendaCompartida>,
    currentUserId: String,
    onSelectAgenda: (String) -> Unit,
    onOpenShareSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectEstado: (EstadoAudiencia?) -> Unit,
    onSelectTipo: (String?) -> Unit,
    onOpenDetail: (Audiencia) -> Unit,
    onOpenResultado: (Audiencia) -> Unit,
    onOpenEdit: (Audiencia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReadOnly = !activeAgendaInfo.isOwnerMode
    val filtered = remember(
        allAudiencias,
        filters.selectedTipoFilter,
        filters.selectedEstadoFilter,
        filters.searchQuery
    ) {
        filterAudiencias(
            items = allAudiencias,
            tipoFilter = filters.selectedTipoFilter,
            estadoFilter = filters.selectedEstadoFilter,
            searchQuery = filters.searchQuery
        )
    }

    val now = remember { System.currentTimeMillis() }
    val proximas = remember(filtered, now) {
        filtered.filter {
            it.fechaHoraMillis >= now - 3_600_000L && it.estadoEnum() == EstadoAudiencia.PROGRAMADA
        }.sortedBy { it.fechaHoraMillis }
    }
    val restantes = remember(filtered, proximas) {
        val proxIds = proximas.map { it.id }.toSet()
        filtered.filterNot { it.id in proxIds }.sortedByDescending { it.fechaHoraMillis }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                        text = appAppearance.directoryTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Filtra por tipo, estado, partes procesales, delito o clasificación",
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
                onOpenShareSheet = onOpenShareSheet,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }

        item {
            SearchAndEstadoBar(
                searchQuery = filters.searchQuery,
                selectedEstado = filters.selectedEstadoFilter,
                onSearchChange = onSearchChange,
                onSelectEstado = onSelectEstado,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }

        item {
            CategoryFilterRow(
                selectedTipo = filters.selectedTipoFilter,
                allAudiencias = allAudiencias,
                onSelectTipo = onSelectTipo,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (allAudiencias.isEmpty()) stringResource(R.string.empty_all_title)
                            else "Sin coincidencias",
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (allAudiencias.isEmpty()) stringResource(R.string.empty_all_subtitle)
                            else "Ajusta tu búsqueda o limpia los filtros activos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (proximas.isNotEmpty()) {
                item {
                    Text(
                        text = "Próximas Programadas (${proximas.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                items(proximas, key = { "prox_${it.id}" }) { audiencia ->
                    AudienciaCard(
                        audiencia = audiencia,
                        showFullDate = true,
                        isReadOnly = isReadOnly,
                        onOpenDetail = { onOpenDetail(audiencia) },
                        onOpenResultado = { onOpenResultado(audiencia) },
                        onOpenEdit = { onOpenEdit(audiencia) },
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }

            if (restantes.isNotEmpty()) {
                item {
                    Text(
                        text = "Historial y Otras (${restantes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(restantes, key = { "hist_${it.id}" }) { audiencia ->
                    AudienciaCard(
                        audiencia = audiencia,
                        showFullDate = true,
                        isReadOnly = isReadOnly,
                        onOpenDetail = { onOpenDetail(audiencia) },
                        onOpenResultado = { onOpenResultado(audiencia) },
                        onOpenEdit = { onOpenEdit(audiencia) },
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
