package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Audiencia
import com.example.data.TipoAudienciaPreset
import com.example.ui.components.EstadoBadge
import com.example.ui.components.SimpleDatePickerDialog
import com.example.ui.components.SimpleTimePickerDialog
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.FechaHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleAudienciaScreen(
    audiencia: Audiencia?,
    allAudiencias: List<Audiencia>,
    isSaving: Boolean,
    isReadOnly: Boolean = false,
    sharedOwnerEmail: String = "",
    onBack: () -> Unit,
    onOpenFullEdit: (Audiencia) -> Unit,
    onOpenResultado: (Audiencia) -> Unit,
    onQuickUpdate: (Audiencia) -> Unit,
    onAutoScheduleProximaFecha: (Audiencia, Long, String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateToAudiencia: (String) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingDatosAudienciaInline by remember(audiencia?.id) { mutableStateOf(false) }
    var editingPartesInline by remember(audiencia?.id) { mutableStateOf(false) }
    var editingDelitoInline by remember(audiencia?.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ficha de Audiencia") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detalle_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    if (audiencia != null && !isReadOnly) {
                        IconButton(
                            onClick = { onOpenFullEdit(audiencia) },
                            modifier = Modifier.testTag("detalle_edit_btn")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar audiencia completa")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("detalle_delete_btn")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Eliminar audiencia",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (audiencia == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Audiencia no encontrada", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
            return@Scaffold
        }

        val categoryColor = TipoAudienciaPreset.colorFor(audiencia.tipoAudiencia)
        val origen = audiencia.audienciaOrigenId?.let { origId ->
            allAudiencias.firstOrNull { it.id == origId }
        }
        val siguiente = audiencia.audienciaSiguienteId?.let { nextId ->
            allAudiencias.firstOrNull { it.id == nextId }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header card: Tipo de Audiencia, NUC y Causa Penal
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    var tipoEdit by remember(audiencia, editingDatosAudienciaInline) { mutableStateOf(audiencia.tipoAudiencia) }
                    var nucEdit by remember(audiencia, editingDatosAudienciaInline) { mutableStateOf(audiencia.nuc) }
                    var causaEdit by remember(audiencia, editingDatosAudienciaInline) { mutableStateOf(audiencia.numeroCausa) }
                    var salaEdit by remember(audiencia, editingDatosAudienciaInline) { mutableStateOf(audiencia.salaOJuzgado) }

                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = categoryColor.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = audiencia.tipoAudiencia,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EstadoBadge(estado = audiencia.estadoEnum())
                                if (!isReadOnly) {
                                    TextButton(
                                        onClick = { editingDatosAudienciaInline = !editingDatosAudienciaInline },
                                        modifier = Modifier.testTag("detalle_toggle_datos_audiencia_edit")
                                    ) {
                                        Text(if (editingDatosAudienciaInline) "Cancelar" else "Modificar")
                                    }
                                }
                            }
                        }

                        Text(
                            text = FechaHelper.formatFullDateTime(audiencia.fechaHoraMillis),
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (editingDatosAudienciaInline && !isReadOnly) {
                            OutlinedTextField(
                                value = tipoEdit,
                                onValueChange = { tipoEdit = it },
                                label = { Text("Tipo de audiencia") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detalle_edit_tipo_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nucEdit,
                                    onValueChange = { nucEdit = it },
                                    label = { Text("NUC") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detalle_edit_nuc_input")
                                )
                                OutlinedTextField(
                                    value = causaEdit,
                                    onValueChange = { causaEdit = it },
                                    label = { Text("Causa Penal") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detalle_edit_causa_penal_input")
                                )
                            }
                            OutlinedTextField(
                                value = salaEdit,
                                onValueChange = { salaEdit = it },
                                label = { Text("Sala / Juzgado") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (tipoEdit.trim().isNotEmpty()) {
                                        onQuickUpdate(
                                            audiencia.copy(
                                                tipoAudiencia = tipoEdit.trim(),
                                                nuc = nucEdit.trim(),
                                                numeroCausa = causaEdit.trim(),
                                                salaOJuzgado = salaEdit.trim()
                                            )
                                        )
                                        editingDatosAudienciaInline = false
                                    }
                                },
                                enabled = !isSaving && tipoEdit.trim().isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("detalle_guardar_datos_audiencia_btn")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Guardar datos")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NUC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = audiencia.nuc.ifBlank { "Sin registrar" },
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (audiencia.nuc.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                                        color = if (audiencia.nuc.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Causa Penal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = audiencia.numeroCausa.ifBlank { "Sin registrar" },
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (audiencia.numeroCausa.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                                        color = if (audiencia.numeroCausa.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (audiencia.salaOJuzgado.isNotBlank()) {
                                Text(
                                    text = "Sala / Juzgado: ${audiencia.salaOJuzgado}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isReadOnly) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Modo visualización · Compartida por ${sharedOwnerEmail.ifBlank { "cuenta principal" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onOpenResultado(audiencia) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("detalle_resultado_btn")
                                ) {
                                    Icon(Icons.Rounded.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Registrar Resultado")
                                }
                                OutlinedButton(
                                    onClick = { onOpenFullEdit(audiencia) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Editar Todo")
                                }
                            }
                        }
                    }
                }

                // Linked hearings chain (Origin / Auto-scheduled Next)
                if (origen != null || siguiente != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.EventRepeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Trazabilidad de Reagendamiento",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (origen != null) {
                                LinkedHearingRow(
                                    prefix = "Proviene de",
                                    linked = origen,
                                    onClick = { onNavigateToAudiencia(origen.id) }
                                )
                            }
                            if (siguiente != null) {
                                LinkedHearingRow(
                                    prefix = "Próxima auto-agendada",
                                    linked = siguiente,
                                    onClick = { onNavigateToAudiencia(siguiente.id) }
                                )
                            }
                        }
                    }
                }

                // Rubro: Delito y Clasificación Jurídica (editable inline para propietario)
                DelitoCard(
                    audiencia = audiencia,
                    isEditing = editingDelitoInline && !isReadOnly,
                    isReadOnly = isReadOnly,
                    isSaving = isSaving,
                    onToggleEdit = { editingDelitoInline = !editingDelitoInline },
                    onSaveDelito = { delito, clasif ->
                        onQuickUpdate(
                            audiencia.copy(
                                tipoDelito = delito.trim(),
                                clasificacionJuridica = clasif.trim()
                            )
                        )
                        editingDelitoInline = false
                    }
                )

                // Rubro: Las 6 Partes Procesales (observables y editables manualmente por propietario)
                PartesProcesalesCard(
                    audiencia = audiencia,
                    isEditing = editingPartesInline && !isReadOnly,
                    isReadOnly = isReadOnly,
                    isSaving = isSaving,
                    onToggleEdit = { editingPartesInline = !editingPartesInline },
                    onSavePartes = { j, mp, aj, v, d, imp ->
                        onQuickUpdate(
                            audiencia.copy(
                                juez = j.trim(),
                                ministerioPublico = mp.trim(),
                                asesorJuridico = aj.trim(),
                                victima = v.trim(),
                                defensor = d.trim(),
                                imputado = imp.trim()
                            )
                        )
                        editingPartesInline = false
                    }
                )

                // Rubro 1 (Separado): Resultado de la Audiencia
                ResultadoCard(
                    audiencia = audiencia,
                    isReadOnly = isReadOnly,
                    isSaving = isSaving,
                    onSaveResultado = { nuevoResultado ->
                        onQuickUpdate(audiencia.copy(resultado = nuevoResultado.trim()))
                    }
                )

                // Rubro 2 (Separado): Próxima Fecha Programada (auto-agendado inmediato al elegir fecha)
                ProximaFechaProgramadaCard(
                    audiencia = audiencia,
                    siguiente = siguiente,
                    isReadOnly = isReadOnly,
                    isSaving = isSaving,
                    onAutoScheduleDate = { newMillis, nextTipo ->
                        onAutoScheduleProximaFecha(audiencia, newMillis, nextTipo)
                    },
                    onClearProximaFecha = {
                        onQuickUpdate(
                            audiencia.copy(
                                tieneProximaFecha = false,
                                proximaFechaMillis = null,
                                proximoTipoAudiencia = ""
                            )
                        )
                    },
                    onNavigateToAudiencia = onNavigateToAudiencia
                )
            }
        }
    }

    if (showDeleteConfirm && audiencia != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar audiencia") },
            text = { Text("¿Deseas eliminar esta audiencia de tu calendario? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(audiencia.id)
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun LinkedHearingRow(
    prefix: String,
    linked: Audiencia,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${linked.tipoAudiencia} · ${FechaHelper.formatFullDateTime(linked.fechaHoraMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Ir a audiencia vinculada"
            )
        }
    }
}

@Composable
private fun DelitoCard(
    audiencia: Audiencia,
    isEditing: Boolean,
    isReadOnly: Boolean,
    isSaving: Boolean,
    onToggleEdit: () -> Unit,
    onSaveDelito: (String, String) -> Unit,
) {
    var delito by remember(audiencia, isEditing) { mutableStateOf(audiencia.tipoDelito) }
    var clasif by remember(audiencia, isEditing) { mutableStateOf(audiencia.clasificacionJuridica) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Balance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text("Delito y Clasificación Jurídica", style = MaterialTheme.typography.titleMedium)
                }
                if (!isReadOnly) {
                    TextButton(
                        onClick = onToggleEdit,
                        modifier = Modifier.testTag("detalle_toggle_delito_edit")
                    ) {
                        Text(if (isEditing) "Cancelar" else "Modificar")
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = delito,
                    onValueChange = { delito = it },
                    label = { Text("Tipo de delito") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clasif,
                    onValueChange = { clasif = it },
                    label = { Text("Clasificación jurídica del delito") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveDelito(delito, clasif) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar delito y clasificación")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tipo de delito", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = audiencia.tipoDelito.ifBlank { "No especificado" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Clasificación jurídica del delito", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = audiencia.clasificacionJuridica.ifBlank { "No especificada" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PartesProcesalesCard(
    audiencia: Audiencia,
    isEditing: Boolean,
    isReadOnly: Boolean,
    isSaving: Boolean,
    onToggleEdit: () -> Unit,
    onSavePartes: (String, String, String, String, String, String) -> Unit,
) {
    var juez by remember(audiencia, isEditing) { mutableStateOf(audiencia.juez) }
    var mp by remember(audiencia, isEditing) { mutableStateOf(audiencia.ministerioPublico) }
    var asesor by remember(audiencia, isEditing) { mutableStateOf(audiencia.asesorJuridico) }
    var victima by remember(audiencia, isEditing) { mutableStateOf(audiencia.victima) }
    var defensor by remember(audiencia, isEditing) { mutableStateOf(audiencia.defensor) }
    var imputado by remember(audiencia, isEditing) { mutableStateOf(audiencia.imputado) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Partes Procesales", style = MaterialTheme.typography.titleMedium)
                if (!isReadOnly) {
                    TextButton(
                        onClick = onToggleEdit,
                        modifier = Modifier.testTag("detalle_toggle_partes_edit")
                    ) {
                        Text(if (isEditing) "Cancelar" else "Modificar")
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(value = juez, onValueChange = { juez = it }, label = { Text("Juez") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mp, onValueChange = { mp = it }, label = { Text("Ministerio Público") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = asesor, onValueChange = { asesor = it }, label = { Text("Asesor Jurídico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = victima, onValueChange = { victima = it }, label = { Text("Víctima") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = defensor, onValueChange = { defensor = it }, label = { Text("Defensor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = imputado, onValueChange = { imputado = it }, label = { Text("Imputado") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = { onSavePartes(juez, mp, asesor, victima, defensor, imputado) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detalle_guardar_partes_btn")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar Partes Procesales")
                }
            } else {
                ActorReadRow(icon = Icons.Rounded.AccountBalance, role = "Juez", name = audiencia.juez)
                ActorReadRow(icon = Icons.Rounded.Policy, role = "Ministerio Público", name = audiencia.ministerioPublico)
                ActorReadRow(icon = Icons.Rounded.SupportAgent, role = "Asesor Jurídico", name = audiencia.asesorJuridico)
                ActorReadRow(icon = Icons.Rounded.Person, role = "Víctima", name = audiencia.victima)
                ActorReadRow(icon = Icons.Rounded.Shield, role = "Defensor", name = audiencia.defensor)
                ActorReadRow(icon = Icons.Rounded.Person, role = "Imputado", name = audiencia.imputado)
            }
        }
    }
}

@Composable
private fun ActorReadRow(
    icon: ImageVector,
    role: String,
    name: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name.ifBlank { "Sin registrar" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (name.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ResultadoCard(
    audiencia: Audiencia,
    isReadOnly: Boolean,
    isSaving: Boolean,
    onSaveResultado: (String) -> Unit,
) {
    var isEditing by remember(audiencia.id) { mutableStateOf(false) }
    var resultadoText by remember(audiencia, isEditing) { mutableStateOf(audiencia.resultado) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("detalle_resultado_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AssignmentTurnedIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Resultado",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (!isReadOnly) {
                    TextButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.testTag("detalle_toggle_resultado_edit")
                    ) {
                        Text(
                            if (isEditing) "Cancelar"
                            else if (audiencia.resultado.isBlank()) "Capturar" else "Modificar"
                        )
                    }
                }
            }

            if (isEditing && !isReadOnly) {
                OutlinedTextField(
                    value = resultadoText,
                    onValueChange = { resultadoText = it },
                    label = { Text("Resultado de la audiencia") },
                    placeholder = { Text("Registra aquí la resolución o acuerdos obtenidos...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detalle_resultado_input")
                )
                Button(
                    onClick = {
                        onSaveResultado(resultadoText)
                        isEditing = false
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detalle_guardar_resultado_btn")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar resultado")
                }
            } else {
                Text(
                    text = audiencia.resultado.ifBlank {
                        "Aún no se ha registrado el resultado de esta audiencia."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (audiencia.resultado.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun ProximaFechaProgramadaCard(
    audiencia: Audiencia,
    siguiente: Audiencia?,
    isReadOnly: Boolean,
    isSaving: Boolean,
    onAutoScheduleDate: (Long, String) -> Unit,
    onClearProximaFecha: () -> Unit,
    onNavigateToAudiencia: (String) -> Unit,
) {
    val defaultNextMillis = audiencia.proximaFechaMillis
        ?: (audiencia.fechaHoraMillis + 7L * 24L * 60L * 60L * 1000L)
    var proximoTipo by remember(audiencia.id, audiencia.proximoTipoAudiencia, audiencia.tipoAudiencia) {
        mutableStateOf(audiencia.proximoTipoAudiencia.ifBlank { audiencia.tipoAudiencia })
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("detalle_proxima_fecha_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EventRepeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Próxima Fecha Programada",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (!isReadOnly && audiencia.tieneProximaFecha && audiencia.proximaFechaMillis != null) {
                    TextButton(
                        onClick = onClearProximaFecha,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("detalle_quitar_proxima_btn")
                    ) {
                        Text("Quitar")
                    }
                }
            }

            if (audiencia.tieneProximaFecha && audiencia.proximaFechaMillis != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { mod ->
                            if (siguiente != null) mod.clickable { onNavigateToAudiencia(siguiente.id) }
                            else mod
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Agendada automáticamente en el calendario",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${FechaHelper.formatFullDateTime(audiencia.proximaFechaMillis)} · ${audiencia.proximoTipoAudiencia.ifBlank { audiencia.tipoAudiencia }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (siguiente != null) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Ver audiencia programada",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Selecciona la próxima fecha para agendarla y actualizarla automáticamente en tu calendario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isReadOnly) {
                OutlinedTextField(
                    value = proximoTipo,
                    onValueChange = { proximoTipo = it },
                    label = { Text("Tipo de la próxima audiencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showDatePicker = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detalle_proxima_fecha_btn")
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (audiencia.tieneProximaFecha && audiencia.proximaFechaMillis != null) {
                                FechaHelper.formatShortDate(audiencia.proximaFechaMillis)
                            } else {
                                "Elegir fecha"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(0.75f)
                            .height(48.dp)
                            .testTag("detalle_proxima_hora_btn")
                    ) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${FechaHelper.formatTime(defaultNextMillis)} hrs")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialLocalMillis = defaultNextMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { utc ->
                val (h, m) = FechaHelper.hourMinuteFromMillis(defaultNextMillis)
                val newMillis = FechaHelper.combineDateUtcPickerAndTime(utc, h, m)
                showDatePicker = false
                onAutoScheduleDate(newMillis, proximoTipo.trim().ifBlank { audiencia.tipoAudiencia })
            }
        )
    }

    if (showTimePicker) {
        val (h, m) = FechaHelper.hourMinuteFromMillis(defaultNextMillis)
        SimpleTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            onDismiss = { showTimePicker = false },
            onConfirm = { newH, newM ->
                val newMillis = FechaHelper.updateTimeOnMillis(defaultNextMillis, newH, newM)
                showTimePicker = false
                onAutoScheduleDate(newMillis, proximoTipo.trim().ifBlank { audiencia.tipoAudiencia })
            }
        )
    }
}
