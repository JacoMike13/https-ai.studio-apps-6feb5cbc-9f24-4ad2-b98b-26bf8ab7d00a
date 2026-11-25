package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppAppearanceConfig
import com.example.data.AppTitleDefaults
import com.example.ui.viewmodel.ConfigSyncState
import kotlinx.coroutines.delay

@Composable
fun ConfiguracionGearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("configuracion_gear_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Configuración",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionSheet(
    currentAppearance: AppAppearanceConfig,
    isSaving: Boolean,
    configSyncState: ConfigSyncState = ConfigSyncState.Synced,
    isOnline: Boolean = true,
    onDismiss: () -> Unit,
    onAutoSync: (
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
    ) -> Unit = { _, _, _, _, _ -> },
    onSave: (
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ownerName by remember { mutableStateOf(currentAppearance.ownerName) }
    var appTitle by remember { mutableStateOf(currentAppearance.appTitle) }
    var calendarTitle by remember { mutableStateOf(currentAppearance.calendarTitle) }
    var directoryTitle by remember { mutableStateOf(currentAppearance.directoryTitle) }
    var summaryTitle by remember { mutableStateOf(currentAppearance.summaryTitle) }
    var lastDispatched by remember { mutableStateOf(currentAppearance) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun currentTrimmedCandidate(): AppAppearanceConfig = AppAppearanceConfig(
        ownerName = ownerName.trim(),
        appTitle = appTitle.trim(),
        calendarTitle = calendarTitle.trim(),
        directoryTitle = directoryTitle.trim(),
        summaryTitle = summaryTitle.trim()
    )

    fun isCandidateValid(candidate: AppAppearanceConfig): Boolean {
        return candidate.ownerName.isNotEmpty() &&
            candidate.appTitle.isNotEmpty() &&
            candidate.calendarTitle.isNotEmpty() &&
            candidate.directoryTitle.isNotEmpty() &&
            candidate.summaryTitle.isNotEmpty()
    }

    LaunchedEffect(ownerName, appTitle, calendarTitle, directoryTitle, summaryTitle) {
        val candidate = currentTrimmedCandidate()
        if (!isCandidateValid(candidate) || candidate == lastDispatched) {
            return@LaunchedEffect
        }
        delay(550L)
        validationError = null
        lastDispatched = candidate
        onAutoSync(
            candidate.ownerName,
            candidate.appTitle,
            candidate.calendarTitle,
            candidate.directoryTitle,
            candidate.summaryTitle
        )
    }

    fun flushPendingAndDismiss() {
        val candidate = currentTrimmedCandidate()
        if (isCandidateValid(candidate) && candidate != lastDispatched) {
            lastDispatched = candidate
            onAutoSync(
                candidate.ownerName,
                candidate.appTitle,
                candidate.calendarTitle,
                candidate.directoryTitle,
                candidate.summaryTitle
            )
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) flushPendingAndDismiss() },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Los cambios en usuario y títulos se sincronizan automáticamente en Firebase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FirebaseConfigSyncStatusBanner(
                configSyncState = configSyncState,
                isOnline = isOnline,
                hasUnsentEdits = isCandidateValid(currentTrimmedCandidate()) &&
                    currentTrimmedCandidate() != lastDispatched
            )

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Usuario principal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = ownerName,
                onValueChange = { if (it.length <= 140) ownerName = it },
                label = { Text("Nombre de usuario") },
                placeholder = { Text("Ej. Lic. Miguel Jacobo") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_username_input")
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Títulos de la aplicación",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = {
                        appTitle = AppTitleDefaults.DEFAULT_APP_TITLE
                        calendarTitle = AppTitleDefaults.DEFAULT_CALENDAR_TITLE
                        directoryTitle = AppTitleDefaults.DEFAULT_DIRECTORY_TITLE
                        summaryTitle = AppTitleDefaults.DEFAULT_SUMMARY_TITLE
                        val trimmedName = ownerName.trim()
                        if (trimmedName.isNotEmpty()) {
                            val resetCandidate = AppAppearanceConfig(
                                ownerName = trimmedName,
                                appTitle = AppTitleDefaults.DEFAULT_APP_TITLE,
                                calendarTitle = AppTitleDefaults.DEFAULT_CALENDAR_TITLE,
                                directoryTitle = AppTitleDefaults.DEFAULT_DIRECTORY_TITLE,
                                summaryTitle = AppTitleDefaults.DEFAULT_SUMMARY_TITLE
                            )
                            lastDispatched = resetCandidate
                            validationError = null
                            onAutoSync(
                                resetCandidate.ownerName,
                                resetCandidate.appTitle,
                                resetCandidate.calendarTitle,
                                resetCandidate.directoryTitle,
                                resetCandidate.summaryTitle
                            )
                        }
                    },
                    modifier = Modifier.testTag("config_reset_titles_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Restablecer")
                }
            }

            OutlinedTextField(
                value = appTitle,
                onValueChange = { if (it.length <= 120) appTitle = it },
                label = { Text("Título principal de la aplicación") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_app_title_input")
            )

            OutlinedTextField(
                value = calendarTitle,
                onValueChange = { if (it.length <= 120) calendarTitle = it },
                label = { Text("Título de la sección Calendario") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_calendar_title_input")
            )

            OutlinedTextField(
                value = directoryTitle,
                onValueChange = { if (it.length <= 120) directoryTitle = it },
                label = { Text("Título de la sección Audiencias") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_directory_title_input")
            )

            OutlinedTextField(
                value = summaryTitle,
                onValueChange = { if (it.length <= 120) summaryTitle = it },
                label = { Text("Título de la sección Resumen") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_summary_title_input")
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { flushPendingAndDismiss() },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cerrar")
                }
                Button(
                    onClick = {
                        val candidate = currentTrimmedCandidate()
                        if (candidate.ownerName.isEmpty()) {
                            validationError = "Ingresa tu nombre de usuario."
                            return@Button
                        }
                        if (candidate.appTitle.isEmpty() ||
                            candidate.calendarTitle.isEmpty() ||
                            candidate.directoryTitle.isEmpty() ||
                            candidate.summaryTitle.isEmpty()
                        ) {
                            validationError = "Ningún título puede quedar vacío."
                            return@Button
                        }
                        validationError = null
                        lastDispatched = candidate
                        onSave(
                            candidate.ownerName,
                            candidate.appTitle,
                            candidate.calendarTitle,
                            candidate.directoryTitle,
                            candidate.summaryTitle
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("config_save_btn")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Guardar y cerrar",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirebaseConfigSyncStatusBanner(
    configSyncState: ConfigSyncState,
    isOnline: Boolean,
    hasUnsentEdits: Boolean,
) {
    val isSyncingNow = configSyncState is ConfigSyncState.Syncing || hasUnsentEdits
    val isOfflinePending = !isOnline || configSyncState is ConfigSyncState.PendingOffline
    val errorMsg = (configSyncState as? ConfigSyncState.Error)?.message

    val (containerColor, borderColor, contentColor, labelText) = when {
        errorMsg != null -> Quad(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onErrorContainer,
            errorMsg
        )
        isSyncingNow -> Quad(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Sincronizando automáticamente con Firebase…"
        )
        isOfflinePending -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Sin conexión · Se sincronizará automáticamente en Firebase al reconectar"
        )
        else -> Quad(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Sincronizado automáticamente en Firebase"
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_firebase_sync_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                errorMsg != null -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                isSyncingNow -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
                isOfflinePending -> Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                else -> Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

