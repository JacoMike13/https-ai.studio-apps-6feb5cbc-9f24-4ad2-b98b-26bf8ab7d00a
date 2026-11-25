package com.example.data

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class EstadoAudiencia(val code: String, val label: String) {
    PROGRAMADA("PROGRAMADA", "Programada"),
    CELEBRADA("CELEBRADA", "Celebrada"),
    DIFERIDA("DIFERIDA", "Diferida"),
    SUSPENDIDA("SUSPENDIDA", "Suspendida"),
    CANCELADA("CANCELADA", "Cancelada");

    companion object {
        fun fromCode(raw: String?): EstadoAudiencia {
            return entries.firstOrNull { it.code == raw } ?: PROGRAMADA
        }
    }
}

enum class TipoAudienciaPreset(
    val label: String,
    val shortLabel: String,
    val colorHex: Long,
) {
    CONTROL_DETENCION("Control de Detención", "Control", 0xFF60A5FA),
    FORMULACION_IMPUTACION("Formulación de Imputación", "Imputación", 0xFF2DD4BF),
    VINCULACION_PROCESO("Vinculación a Proceso", "Vinculación", 0xFFA78BFA),
    MEDIDAS_CAUTELARES("Medidas Cautelares", "Cautelares", 0xFFFBBF24),
    ETAPA_INTERMEDIA("Etapa Intermedia", "Intermedia", 0xFF38BDF8),
    JUICIO_ORAL("Juicio Oral", "Juicio Oral", 0xFFF87171),
    PROCEDIMIENTO_ABREVIADO("Procedimiento Abreviado", "Abreviado", 0xFF4ADE80),
    SUSPENSION_CONDICIONAL("Suspensión Condicional", "Suspensión", 0xFFC084FC),
    INDIVIDUALIZACION_SANCION("Individualización de Sanción", "Sanción", 0xFFFB923C),
    EJECUCION_PENAL("Ejecución Penal", "Ejecución", 0xFF94A3B8);

    companion object {
        private val fallbackColors = listOf(
            0xFF60A5FA, 0xFF34D399, 0xFFA78BFA, 0xFFFBBF24, 0xFFFB7185, 0xFF22D3EE
        )

        fun colorFor(tipoAudiencia: String): Color {
            val match = entries.firstOrNull {
                it.label.equals(tipoAudiencia.trim(), ignoreCase = true)
            }
            if (match != null) return Color(match.colorHex)
            val hash = kotlin.math.abs(tipoAudiencia.trim().lowercase().hashCode())
            return Color(fallbackColors[hash % fallbackColors.size])
        }

        fun shortLabelFor(tipoAudiencia: String): String {
            val match = entries.firstOrNull {
                it.label.equals(tipoAudiencia.trim(), ignoreCase = true)
            }
            return match?.shortLabel ?: tipoAudiencia.trim().take(16).ifBlank { "Audiencia" }
        }
    }
}

val DELITOS_SUGERIDOS = listOf(
    "Robo simple",
    "Robo calificado",
    "Homicidio",
    "Lesiones",
    "Violencia familiar",
    "Fraude",
    "Abuso de confianza",
    "Narcomenudeo",
    "Extorsión",
    "Secuestro",
    "Daño en propiedad ajena",
    "Amenazas",
    "Portación de arma de fuego",
    "Abuso sexual"
)

val CLASIFICACIONES_JURIDICAS_SUGERIDAS = listOf(
    "Doloso · Consumado · Autoría directa",
    "Doloso · Consumado · Coautoría",
    "Doloso · Tentativa · Autoría directa",
    "Culposo · Consumado · Autoría directa",
    "Agravado · Continuado · Participación",
    "Calificado · Instantáneo · Coautoría"
)

object AppTitleDefaults {
    const val DEFAULT_APP_TITLE = "Agenda de Audiencias - Secuestros"
    const val DEFAULT_CALENDAR_TITLE = "Calendario Audiencias Código"
    const val DEFAULT_DIRECTORY_TITLE = "Consulta Dinámica"
    const val DEFAULT_SUMMARY_TITLE = "Resumen de Audiencias"
}

data class AppAppearanceConfig(
    val appTitle: String = AppTitleDefaults.DEFAULT_APP_TITLE,
    val calendarTitle: String = AppTitleDefaults.DEFAULT_CALENDAR_TITLE,
    val directoryTitle: String = AppTitleDefaults.DEFAULT_DIRECTORY_TITLE,
    val summaryTitle: String = AppTitleDefaults.DEFAULT_SUMMARY_TITLE,
    val ownerName: String = "",
) {
    companion object {
        fun resolve(
            ownAgenda: AgendaCompartida?,
            fallbackDisplayName: String,
        ): AppAppearanceConfig {
            val resolvedName = ownAgenda?.ownerName?.trim()?.ifBlank { null }
                ?: fallbackDisplayName.trim().ifBlank { "Usuario principal" }
            return AppAppearanceConfig(
                appTitle = ownAgenda?.appTitle?.trim()?.ifBlank { null }
                    ?: AppTitleDefaults.DEFAULT_APP_TITLE,
                calendarTitle = ownAgenda?.calendarTitle?.trim()?.ifBlank { null }
                    ?: AppTitleDefaults.DEFAULT_CALENDAR_TITLE,
                directoryTitle = ownAgenda?.directoryTitle?.trim()?.ifBlank { null }
                    ?: AppTitleDefaults.DEFAULT_DIRECTORY_TITLE,
                summaryTitle = ownAgenda?.summaryTitle?.trim()?.ifBlank { null }
                    ?: AppTitleDefaults.DEFAULT_SUMMARY_TITLE,
                ownerName = resolvedName
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AgendaCompartida(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val ownerName: String = "",
    val appTitle: String = "",
    val calendarTitle: String = "",
    val directoryTitle: String = "",
    val summaryTitle: String = "",
    val sharedEmails: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Audiencia(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sharedEmails: List<String> = emptyList(),
    val nuc: String = "",
    val numeroCausa: String = "",
    val tipoAudiencia: String = "",
    val fechaHoraMillis: Long = 0L,
    val salaOJuzgado: String = "",
    val juez: String = "",
    val ministerioPublico: String = "",
    val asesorJuridico: String = "",
    val victima: String = "",
    val defensor: String = "",
    val imputado: String = "",
    val tipoDelito: String = "",
    val clasificacionJuridica: String = "",
    val estado: String = EstadoAudiencia.PROGRAMADA.code,
    val resultado: String = "",
    val tieneProximaFecha: Boolean = false,
    val proximaFechaMillis: Long? = null,
    val proximoTipoAudiencia: String = "",
    val audienciaOrigenId: String? = null,
    val audienciaSiguienteId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun estadoEnum(): EstadoAudiencia = EstadoAudiencia.fromCode(estado)
}

data class AutoScheduleResult(
    val autoScheduled: Boolean,
    val nextAudienciaId: String?,
    val updatedExistingNext: Boolean,
    val savedOfflinePendingSync: Boolean = false,
)
