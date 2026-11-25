package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {

    @Query(
        "SELECT * FROM audiencias WHERE ownerUid = :ownerUid AND syncStatus != 'PENDING_DELETE' ORDER BY fechaHoraMillis ASC"
    )
    fun observeVisibleByOwner(ownerUid: String): Flow<List<AudienciaEntity>>

    @Query(
        "SELECT COUNT(*) FROM audiencias WHERE ownerUid = :ownerUid AND syncStatus != 'PENDING_DELETE'"
    )
    suspend fun countVisibleByOwner(ownerUid: String): Int

    @Query("SELECT * FROM audiencias WHERE id = :id LIMIT 1")
    suspend fun getAudienciaById(id: String): AudienciaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudiencia(entity: AudienciaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudiencias(entities: List<AudienciaEntity>)

    @Query("DELETE FROM audiencias WHERE id = :id")
    suspend fun hardDeleteAudienciaById(id: String)

    @Query("DELETE FROM audiencias WHERE ownerUid = :ownerUid AND syncStatus = 'SYNCED'")
    suspend fun deleteAllSyncedForOwner(ownerUid: String)

    @Query("SELECT audienciaId FROM pending_sync_ops WHERE ownerUid = :ownerUid")
    suspend fun getPendingAudienciaIds(ownerUid: String): List<String>

    @Query("SELECT * FROM pending_sync_ops WHERE ownerUid = :ownerUid ORDER BY createdAtMillis ASC")
    suspend fun getPendingOpsForOwner(ownerUid: String): List<PendingSyncOpEntity>

    @Query("SELECT * FROM pending_sync_ops WHERE audienciaId = :audienciaId LIMIT 1")
    suspend fun getPendingOp(audienciaId: String): PendingSyncOpEntity?

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE ownerUid = :ownerUid")
    fun observePendingCount(ownerUid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingOp(op: PendingSyncOpEntity)

    @Query("DELETE FROM pending_sync_ops WHERE audienciaId = :audienciaId")
    suspend fun deletePendingOp(audienciaId: String)

    @Transaction
    suspend fun replaceSyncedFromRemote(
        ownerUid: String,
        remoteEntities: List<AudienciaEntity>,
    ) {
        val pendingIds = getPendingAudienciaIds(ownerUid).toHashSet()
        deleteAllSyncedForOwner(ownerUid)
        val nonConflicting = remoteEntities.filter { it.id !in pendingIds }
        if (nonConflicting.isNotEmpty()) {
            upsertAudiencias(nonConflicting)
        }
    }

    @Transaction
    suspend fun queueLocalUpsert(
        entity: AudienciaEntity,
        requestedOpType: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val existingOp = getPendingOp(entity.id)
        val resolvedOpType = when (existingOp?.opType) {
            SyncOpType.CREATE -> SyncOpType.CREATE
            else -> requestedOpType
        }
        upsertAudiencia(entity.copy(syncStatus = SyncStatus.PENDING_UPSERT, localUpdatedAtMillis = nowMillis))
        upsertPendingOp(
            PendingSyncOpEntity(
                audienciaId = entity.id,
                ownerUid = entity.ownerUid,
                opType = resolvedOpType,
                createdAtMillis = existingOp?.createdAtMillis ?: nowMillis
            )
        )
    }

    @Transaction
    suspend fun queueLocalDelete(
        audienciaId: String,
        ownerUid: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val existingOp = getPendingOp(audienciaId)
        if (existingOp?.opType == SyncOpType.CREATE) {
            // Created offline and never pushed to Firestore: simply remove locally.
            deletePendingOp(audienciaId)
            hardDeleteAudienciaById(audienciaId)
            return
        }
        val existingEntity = getAudienciaById(audienciaId)
        if (existingEntity != null) {
            upsertAudiencia(
                existingEntity.copy(
                    syncStatus = SyncStatus.PENDING_DELETE,
                    localUpdatedAtMillis = nowMillis
                )
            )
        }
        upsertPendingOp(
            PendingSyncOpEntity(
                audienciaId = audienciaId,
                ownerUid = ownerUid,
                opType = SyncOpType.DELETE,
                createdAtMillis = existingOp?.createdAtMillis ?: nowMillis
            )
        )
    }

    @Transaction
    suspend fun markSynced(entity: AudienciaEntity) {
        upsertAudiencia(entity.copy(syncStatus = SyncStatus.SYNCED))
        deletePendingOp(entity.id)
    }

    @Transaction
    suspend fun markDeleteSynced(audienciaId: String) {
        deletePendingOp(audienciaId)
        hardDeleteAudienciaById(audienciaId)
    }

    @Query("SELECT * FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 1 LIMIT 1")
    fun observeOwnAgenda(viewerUid: String): Flow<AgendaCompartidaEntity?>

    @Query("SELECT * FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 1 LIMIT 1")
    suspend fun getOwnAgenda(viewerUid: String): AgendaCompartidaEntity?

    @Query("SELECT COUNT(*) FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 1 AND pendingSync = 1")
    fun observePendingOwnAgendaCount(viewerUid: String): Flow<Int>

    @Query("SELECT * FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 0 ORDER BY ownerEmail ASC")
    fun observeSharedAgendas(viewerUid: String): Flow<List<AgendaCompartidaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgenda(entity: AgendaCompartidaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgendas(entities: List<AgendaCompartidaEntity>)

    @Query("DELETE FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 1")
    suspend fun deleteOwnAgenda(viewerUid: String)

    @Query("DELETE FROM agendas_compartidas WHERE viewerUid = :viewerUid AND isOwnAgenda = 0")
    suspend fun deleteSharedAgendas(viewerUid: String)

    @Transaction
    suspend fun replaceOwnAgenda(viewerUid: String, entity: AgendaCompartidaEntity?) {
        val current = getOwnAgenda(viewerUid)
        if (current != null && current.pendingSync) {
            return
        }
        deleteOwnAgenda(viewerUid)
        if (entity != null) upsertAgenda(entity.copy(pendingSync = false))
    }

    @Transaction
    suspend fun replaceSharedAgendas(viewerUid: String, entities: List<AgendaCompartidaEntity>) {
        deleteSharedAgendas(viewerUid)
        if (entities.isNotEmpty()) upsertAgendas(entities)
    }
}
