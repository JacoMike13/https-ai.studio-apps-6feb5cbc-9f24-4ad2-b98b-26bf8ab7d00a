package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.AgendaCompartida
import com.example.data.Audiencia
import com.google.firebase.Timestamp
import java.util.Date

object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_UPSERT = "PENDING_UPSERT"
    const val PENDING_DELETE = "PENDING_DELETE"
}

object SyncOpType {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}

@Entity(
    tableName = "audiencias",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["ownerUid", "syncStatus"])
    ]
)
data class AudienciaEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val userId: String,
    val sharedEmailsSerialized: String,
    val nuc: String,
    val numeroCausa: String,
    val tipoAudiencia: String,
    val fechaHoraMillis: Long,
    val salaOJuzgado: String,
    val juez: String,
    val ministerioPublico: String,
    val asesorJuridico: String,
    val victima: String,
    val defensor: String,
    val imputado: String,
    val tipoDelito: String,
    val clasificacionJuridica: String,
    val estado: String,
    val resultado: String,
    val tieneProximaFecha: Boolean,
    val proximaFechaMillis: Long?,
    val proximoTipoAudiencia: String,
    val audienciaOrigenId: String?,
    val audienciaSiguienteId: String?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val syncStatus: String,
    val localUpdatedAtMillis: Long,
) {
    fun toDomain(): Audiencia {
        val emails = sharedEmailsSerialized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Audiencia(
            id = id,
            userId = userId,
            sharedEmails = emails,
            nuc = nuc,
            numeroCausa = numeroCausa,
            tipoAudiencia = tipoAudiencia,
            fechaHoraMillis = fechaHoraMillis,
            salaOJuzgado = salaOJuzgado,
            juez = juez,
            ministerioPublico = ministerioPublico,
            asesorJuridico = asesorJuridico,
            victima = victima,
            defensor = defensor,
            imputado = imputado,
            tipoDelito = tipoDelito,
            clasificacionJuridica = clasificacionJuridica,
            estado = estado,
            resultado = resultado,
            tieneProximaFecha = tieneProximaFecha,
            proximaFechaMillis = proximaFechaMillis,
            proximoTipoAudiencia = proximoTipoAudiencia,
            audienciaOrigenId = audienciaOrigenId,
            audienciaSiguienteId = audienciaSiguienteId,
            createdAt = createdAtMillis?.let { Timestamp(Date(it)) },
            updatedAt = updatedAtMillis?.let { Timestamp(Date(it)) }
        )
    }
}

fun Audiencia.toRoomEntity(
    ownerUid: String,
    syncStatus: String,
    localUpdatedAtMillis: Long = System.currentTimeMillis(),
): AudienciaEntity {
    return AudienciaEntity(
        id = id,
        ownerUid = ownerUid,
        userId = userId.ifBlank { ownerUid },
        sharedEmailsSerialized = sharedEmails.joinToString("\n") { it.trim().lowercase() },
        nuc = nuc,
        numeroCausa = numeroCausa,
        tipoAudiencia = tipoAudiencia,
        fechaHoraMillis = fechaHoraMillis,
        salaOJuzgado = salaOJuzgado,
        juez = juez,
        ministerioPublico = ministerioPublico,
        asesorJuridico = asesorJuridico,
        victima = victima,
        defensor = defensor,
        imputado = imputado,
        tipoDelito = tipoDelito,
        clasificacionJuridica = clasificacionJuridica,
        estado = estado,
        resultado = resultado,
        tieneProximaFecha = tieneProximaFecha,
        proximaFechaMillis = proximaFechaMillis,
        proximoTipoAudiencia = proximoTipoAudiencia,
        audienciaOrigenId = audienciaOrigenId,
        audienciaSiguienteId = audienciaSiguienteId,
        createdAtMillis = createdAt?.toDate()?.time ?: localUpdatedAtMillis,
        updatedAtMillis = updatedAt?.toDate()?.time ?: localUpdatedAtMillis,
        syncStatus = syncStatus,
        localUpdatedAtMillis = localUpdatedAtMillis
    )
}

@Entity(
    tableName = "agendas_compartidas",
    indices = [Index(value = ["viewerUid", "isOwnAgenda"])]
)
data class AgendaCompartidaEntity(
    @PrimaryKey val cacheKey: String,
    val viewerUid: String,
    val ownerUid: String,
    val ownerEmail: String,
    val ownerName: String,
    val appTitle: String = "",
    val calendarTitle: String = "",
    val directoryTitle: String = "",
    val summaryTitle: String = "",
    val sharedEmailsSerialized: String,
    val isOwnAgenda: Boolean,
    val pendingSync: Boolean = false,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
) {
    fun toDomain(): AgendaCompartida {
        val emails = sharedEmailsSerialized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return AgendaCompartida(
            id = ownerUid,
            ownerUid = ownerUid,
            ownerEmail = ownerEmail,
            ownerName = ownerName,
            appTitle = appTitle,
            calendarTitle = calendarTitle,
            directoryTitle = directoryTitle,
            summaryTitle = summaryTitle,
            sharedEmails = emails,
            createdAt = createdAtMillis?.let { Timestamp(Date(it)) },
            updatedAt = updatedAtMillis?.let { Timestamp(Date(it)) }
        )
    }
}

fun AgendaCompartida.toRoomEntity(
    viewerUid: String,
    isOwnAgenda: Boolean,
    pendingSync: Boolean = false,
): AgendaCompartidaEntity {
    val resolvedOwnerUid = ownerUid.ifBlank { id }
    return AgendaCompartidaEntity(
        cacheKey = "${viewerUid}_${resolvedOwnerUid}_${if (isOwnAgenda) "own" else "shared"}",
        viewerUid = viewerUid,
        ownerUid = resolvedOwnerUid,
        ownerEmail = ownerEmail,
        ownerName = ownerName,
        appTitle = appTitle,
        calendarTitle = calendarTitle,
        directoryTitle = directoryTitle,
        summaryTitle = summaryTitle,
        sharedEmailsSerialized = sharedEmails.joinToString("\n") { it.trim().lowercase() },
        isOwnAgenda = isOwnAgenda,
        pendingSync = pendingSync,
        createdAtMillis = createdAt?.toDate()?.time,
        updatedAtMillis = updatedAt?.toDate()?.time
    )
}

@Entity(
    tableName = "pending_sync_ops",
    indices = [Index(value = ["ownerUid", "createdAtMillis"])]
)
data class PendingSyncOpEntity(
    @PrimaryKey val audienciaId: String,
    val ownerUid: String,
    val opType: String,
    val createdAtMillis: Long,
    val lastAttemptMillis: Long = 0L,
    val lastError: String? = null,
)
