package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.SyncStatusUi

@Composable
fun OfflineSyncStatusBanner(
    syncStatus: SyncStatusUi,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (syncStatus.isOnline && syncStatus.pendingCount == 0 && !syncStatus.isSyncing) {
        return
    }

    val accentColor = when {
        !syncStatus.isOnline -> Color(0xFFF59E0B)
        syncStatus.isSyncing -> Color(0xFF38BDF8)
        else -> Color(0xFFA3E635)
    }

    val titleText = when {
        !syncStatus.isOnline && syncStatus.pendingCount > 0 ->
            "Modo sin conexión · ${syncStatus.pendingCount} cambio(s) en Room"
        !syncStatus.isOnline ->
            "Modo sin conexión (agenda disponible en Room)"
        syncStatus.isSyncing ->
            "Sincronizando cambios pendientes con Firebase..."
        else ->
            "${syncStatus.pendingCount} cambio(s) guardado(s) listo(s) para sincronizar"
    }

    val subtitleText = when {
        !syncStatus.isOnline ->
            "Puedes consultar y editar el calendario. Se sincronizará automáticamente al recuperar la red."
        syncStatus.isSyncing ->
            "Actualizando respaldo en la nube..."
        else ->
            "Toca Sincronizar para enviar tus cambios locales de Room a Firebase."
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("offline_sync_banner"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141418),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (syncStatus.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = if (!syncStatus.isOnline) Icons.Rounded.CloudOff else Icons.Rounded.CloudSync,
                    contentDescription = titleText,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4D4D8)
                )
            }

            if (syncStatus.isOnline && syncStatus.pendingCount > 0 && !syncStatus.isSyncing) {
                TextButton(
                    onClick = onSyncNow,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Sincronizar",
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
