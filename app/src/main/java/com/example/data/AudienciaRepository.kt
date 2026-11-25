package com.example.data

import android.content.Context
import android.util.Log
import android.util.Patterns
import com.example.R
import com.example.data.local.AgendaDao
import com.example.data.local.AgendaDatabase
import com.example.data.local.SyncOpType
import com.example.data.local.SyncStatus
import com.example.data.local.toRoomEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AudienciaRepository(
    private val db: FirebaseFirestore,
    private val dao: AgendaDao,
    private val networkMonitor: NetworkMonitor? = null,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        dao = AgendaDatabase.getInstance(context.applicationContext).agendaDao(),
        networkMonitor = NetworkMonitor(context.applicationContext)
    )

    private val syncMutex = Mutex()
    private val configSyncMutex = Mutex()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a tu agenda.")
    }

    private fun isNetworkOnline(): Boolean {
        return networkMonitor?.checkCurrentlyOnline() ?: true
    }

    private fun generateValidDocId(): String {
        return "loc_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    }

    private fun agendasCollection() = db.collection("agendas")

    private fun audienciasCollection(userId: String) =
        db.collection("users").document(userId).collection("audiencias")

    fun observePendingSyncCount(ownerUid: String): Flow<Int> = combine(
        dao.observePendingCount(ownerUid),
        dao.observePendingOwnAgendaCount(ownerUid)
    ) { opCount, configPending ->
        opCount + configPending
    }

    fun observeOwnAgenda(userId: String): Flow<AgendaCompartida?> = channelFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("Sesión no autenticada para consultar configuración de agenda.")
        }
        val path = "agendas/$userId"

        launch {
            dao.observeOwnAgenda(userId).collect { entity ->
                send(entity?.toDomain())
            }
        }

        launch {
            try {
                agendasCollection()
                    .document(userId)
                    .snapshots()
                    .collect { snapshot ->
                        val isCacheSnapshot = snapshot.metadata.isFromCache
                        if (!snapshot.metadata.hasPendingWrites() && (!isCacheSnapshot || snapshot.exists())) {
                            val agenda = if (snapshot.exists()) {
                                snapshot.toObject(
                                    AgendaCompartida::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                            } else {
                                null
                            }
                            dao.replaceOwnAgenda(
                                viewerUid = userId,
                                entity = agenda?.toRoomEntity(viewerUid = userId, isOwnAgenda = true)
                            )
                        }
                    }
            } catch (error: Throwable) {
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                if (!isTransientNetworkError(firestoreError ?: error)) {
                    Log.w(TAG, "Non-fatal own agenda sync error", error)
                }
            }
        }
    }

    fun observeSharedAgendasWithMe(userEmail: String): Flow<List<AgendaCompartida>> = channelFlow {
        val normalizedEmail = userEmail.trim().lowercase()
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || normalizedEmail.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }
        val path = "agendas"

        launch {
            dao.observeSharedAgendas(uid).collect { list ->
                send(list.map { it.toDomain() }.filter { it.ownerUid != uid })
            }
        }

        launch {
            try {
                agendasCollection()
                    .whereArrayContains("sharedEmails", normalizedEmail)
                    .snapshots()
                    .collect { snapshot ->
                        val remote = snapshot.toObjects(
                            AgendaCompartida::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).filter { it.ownerUid != uid }
                            .sortedBy { it.ownerEmail }
                        dao.replaceSharedAgendas(
                            viewerUid = uid,
                            entities = remote.map { it.toRoomEntity(viewerUid = uid, isOwnAgenda = false) }
                        )
                    }
            } catch (error: Throwable) {
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                if (!isTransientNetworkError(firestoreError ?: error)) {
                    Log.w(TAG, "Shared agendas sync error", error)
                }
            }
        }
    }

    fun observeAudienciasForAgenda(
        ownerUid: String,
        currentUserId: String,
        currentUserEmail: String,
    ): Flow<List<Audiencia>> = channelFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != currentUserId || ownerUid.isBlank()) {
            throw IllegalStateException("Sesión no autenticada para consultar audiencias.")
        }
        val path = "users/$ownerUid/audiencias"

        launch {
            dao.observeVisibleByOwner(ownerUid)
                .map { entities -> entities.map { it.toDomain() }.sortedBy { it.fechaHoraMillis } }
                .collect { send(it) }
        }

        val query: Query = if (ownerUid == currentUserId) {
            audienciasCollection(ownerUid)
                .orderBy("fechaHoraMillis", Query.Direction.ASCENDING)
        } else {
            val normalizedEmail = currentUserEmail.trim().lowercase()
            if (normalizedEmail.isEmpty()) {
                return@channelFlow
            }
            audienciasCollection(ownerUid)
                .whereArrayContains("sharedEmails", normalizedEmail)
        }

        launch {
            try {
                query.snapshots().collect { snapshot ->
                    val remote = snapshot.toObjects(
                        Audiencia::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fechaHoraMillis }

                    val hasLocalRows = dao.countVisibleByOwner(ownerUid) > 0
                    val isCacheSnapshot = snapshot.metadata.isFromCache
                    if (!snapshot.metadata.hasPendingWrites() && (!isCacheSnapshot || !hasLocalRows)) {
                        val entities = remote.map { aud ->
                            aud.toRoomEntity(ownerUid = ownerUid, syncStatus = SyncStatus.SYNCED)
                        }
                        dao.replaceSyncedFromRemote(ownerUid, entities)
                    }
                }
            } catch (error: Throwable) {
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                if (rootError is Exception && auth.currentUser?.uid == currentUserId) {
                    handleFirestoreError(rootError, OperationType.LIST, path)
                }
                val hasLocal = dao.countVisibleByOwner(ownerUid) > 0
                if (!hasLocal && !isTransientNetworkError(rootError) && isNetworkOnline()) {
                    close(rootError)
                } else {
                    Log.w(TAG, "Serving cached Room audiencias for $ownerUid (sync paused)", rootError)
                }
            }
        }
    }

    suspend fun syncPendingOperations(ownerUid: String): Result<Int> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != ownerUid) {
            return Result.success(0)
        }
        if (!isNetworkOnline()) {
            return Result.success(0)
        }

        return syncMutex.withLock {
            try {
                var syncedCount = 0

                configSyncMutex.withLock {
                    val pendingConfig = dao.getOwnAgenda(ownerUid)?.takeIf { it.pendingSync }
                    if (pendingConfig != null) {
                        pushOwnAgendaConfigToFirestore(
                            ownerUid = ownerUid,
                            ownerEmail = pendingConfig.ownerEmail,
                            ownerName = pendingConfig.ownerName,
                            appTitle = pendingConfig.appTitle,
                            calendarTitle = pendingConfig.calendarTitle,
                            directoryTitle = pendingConfig.directoryTitle,
                            summaryTitle = pendingConfig.summaryTitle,
                            sharedEmails = pendingConfig.toDomain().sharedEmails
                        )
                        syncFirebaseAuthDisplayName(pendingConfig.ownerName)
                        val latestLocal = dao.getOwnAgenda(ownerUid)
                        if (latestLocal != null && matchesConfigValues(latestLocal, pendingConfig)) {
                            dao.upsertAgenda(latestLocal.copy(pendingSync = false))
                        }
                        syncedCount++
                    }
                }

                val pendingOps = dao.getPendingOpsForOwner(ownerUid)
                if (pendingOps.isEmpty()) return@withLock Result.success(syncedCount)

                val colRef = audienciasCollection(ownerUid)
                val sharedEmails = loadOwnerSharedEmails(ownerUid)

                for (op in pendingOps) {
                    when (op.opType) {
                        SyncOpType.CREATE -> {
                            val entity = dao.getAudienciaById(op.audienciaId)
                            if (entity == null) {
                                dao.deletePendingOp(op.audienciaId)
                                continue
                            }
                            val domain = entity.toDomain()
                            val payload = buildCreatePayload(
                                uid = ownerUid,
                                sharedEmails = if (domain.sharedEmails.isNotEmpty()) domain.sharedEmails else sharedEmails,
                                audiencia = domain
                            )
                            colRef.document(entity.id).set(payload).await()
                            dao.markSynced(entity)
                            syncedCount++
                        }

                        SyncOpType.UPDATE -> {
                            val entity = dao.getAudienciaById(op.audienciaId)
                            if (entity == null) {
                                dao.deletePendingOp(op.audienciaId)
                                continue
                            }
                            val domain = entity.toDomain()
                            val resolvedEmails = if (domain.sharedEmails.isNotEmpty()) domain.sharedEmails else sharedEmails
                            val docRef = colRef.document(entity.id)
                            val updateMap = buildUpdateMap(domain, resolvedEmails)
                            try {
                                docRef.update(updateMap).await()
                            } catch (updateError: FirebaseFirestoreException) {
                                if (updateError.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                                    docRef.set(buildCreatePayload(ownerUid, resolvedEmails, domain)).await()
                                } else {
                                    throw updateError
                                }
                            }
                            dao.markSynced(entity)
                            syncedCount++
                        }

                        SyncOpType.DELETE -> {
                            colRef.document(op.audienciaId).delete().await()
                            dao.markDeleteSynced(op.audienciaId)
                            syncedCount++
                        }
                    }
                }
                Result.success(syncedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pending Room-to-Firebase sync interrupted", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateUserConfiguration(
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
    ): Result<Boolean> {
        val uid = auth.currentUser?.uid
        val docPath = "agendas/${uid ?: "unauthenticated"}"
        return try {
            val currentUid = requireUserId()
            val currentUser = auth.currentUser
            val cleanName = ownerName.trim().take(140)
            val cleanAppTitle = appTitle.trim().take(120)
            val cleanCalendarTitle = calendarTitle.trim().take(120)
            val cleanDirectoryTitle = directoryTitle.trim().take(120)
            val cleanSummaryTitle = summaryTitle.trim().take(120)

            require(cleanName.isNotEmpty()) {
                "El nombre de usuario no puede estar vacío."
            }

            val existingLocal = dao.getOwnAgenda(currentUid)
            val resolvedEmail = (currentUser?.email ?: existingLocal?.ownerEmail ?: "")
                .trim()
                .lowercase()
                .ifBlank { "usuario@agenda.local" }
            val resolvedSharedEmails = existingLocal?.toDomain()?.sharedEmails.orEmpty()

            val updatedDomain = AgendaCompartida(
                id = currentUid,
                ownerUid = currentUid,
                ownerEmail = resolvedEmail,
                ownerName = cleanName,
                appTitle = cleanAppTitle,
                calendarTitle = cleanCalendarTitle,
                directoryTitle = cleanDirectoryTitle,
                summaryTitle = cleanSummaryTitle,
                sharedEmails = resolvedSharedEmails
            )
            val pendingEntity = updatedDomain.toRoomEntity(
                viewerUid = currentUid,
                isOwnAgenda = true,
                pendingSync = true
            )
            dao.upsertAgenda(pendingEntity)

            if (!isNetworkOnline()) {
                return Result.success(true)
            }

            try {
                configSyncMutex.withLock {
                    val latestBeforePush = dao.getOwnAgenda(currentUid) ?: pendingEntity
                    val latestEmails = latestBeforePush.toDomain().sharedEmails
                    pushOwnAgendaConfigToFirestore(
                        ownerUid = currentUid,
                        ownerEmail = latestBeforePush.ownerEmail.ifBlank { resolvedEmail },
                        ownerName = latestBeforePush.ownerName,
                        appTitle = latestBeforePush.appTitle,
                        calendarTitle = latestBeforePush.calendarTitle,
                        directoryTitle = latestBeforePush.directoryTitle,
                        summaryTitle = latestBeforePush.summaryTitle,
                        sharedEmails = latestEmails
                    )
                    syncFirebaseAuthDisplayName(latestBeforePush.ownerName)
                    val latestAfterPush = dao.getOwnAgenda(currentUid)
                    if (latestAfterPush != null && matchesConfigValues(latestAfterPush, latestBeforePush)) {
                        dao.upsertAgenda(latestAfterPush.copy(pendingSync = false))
                    }
                }
                Result.success(false)
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    Result.success(true)
                } else {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun matchesConfigValues(
        a: com.example.data.local.AgendaCompartidaEntity,
        b: com.example.data.local.AgendaCompartidaEntity,
    ): Boolean {
        return a.ownerName == b.ownerName &&
            a.appTitle == b.appTitle &&
            a.calendarTitle == b.calendarTitle &&
            a.directoryTitle == b.directoryTitle &&
            a.summaryTitle == b.summaryTitle
    }

    private suspend fun syncFirebaseAuthDisplayName(cleanName: String) {
        val user = auth.currentUser ?: return
        if (cleanName.isBlank() || user.displayName == cleanName) return
        try {
            val profileUpdates = userProfileChangeRequest {
                displayName = cleanName
            }
            user.updateProfile(profileUpdates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal Firebase Auth displayName sync error", e)
        }
    }

    private suspend fun pushOwnAgendaConfigToFirestore(
        ownerUid: String,
        ownerEmail: String,
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
        sharedEmails: List<String>,
    ) {
        val resolvedEmail = ownerEmail.trim().lowercase().ifBlank { "usuario@agenda.local" }
        val agendaRef = agendasCollection().document(ownerUid)
        val updatePayload = mapOf(
            "ownerEmail" to resolvedEmail,
            "ownerName" to ownerName.trim().take(140),
            "appTitle" to appTitle.trim().take(120),
            "calendarTitle" to calendarTitle.trim().take(120),
            "directoryTitle" to directoryTitle.trim().take(120),
            "summaryTitle" to summaryTitle.trim().take(120),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            agendaRef.update(updatePayload).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                agendaRef.set(
                    mapOf(
                        "ownerUid" to ownerUid,
                        "ownerEmail" to resolvedEmail,
                        "ownerName" to ownerName.trim().take(140),
                        "appTitle" to appTitle.trim().take(120),
                        "calendarTitle" to calendarTitle.trim().take(120),
                        "directoryTitle" to directoryTitle.trim().take(120),
                        "summaryTitle" to summaryTitle.trim().take(120),
                        "sharedEmails" to sharedEmails,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                throw e
            }
        }
    }

    suspend fun addSharedGmail(rawEmail: String): Result<Unit> {
        val normalized = rawEmail.trim().lowercase()
        val uid = auth.currentUser?.uid
        val docPath = "agendas/${uid ?: "unauthenticated"}"
        return try {
            val currentUid = requireUserId()
            val currentUser = auth.currentUser
            val ownerEmail = (currentUser?.email ?: "").trim().lowercase()
            val ownerName = (currentUser?.displayName ?: ownerEmail).trim().take(140)

            require(ownerEmail.isNotBlank()) {
                "Tu cuenta de Google no tiene un correo electrónico disponible."
            }
            require(normalized.isNotEmpty() && normalized.length <= 160) {
                "Ingresa un correo electrónico válido."
            }
            require(Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
                "El formato del correo electrónico no es válido."
            }
            require(normalized != ownerEmail) {
                "No es necesario agregar tu propio correo de cuenta principal."
            }
            require(isNetworkOnline()) {
                "Se requiere conexión a internet para compartir tu agenda con otra cuenta Gmail."
            }

            val agendaRef = agendasCollection().document(currentUid)
            val existingAgenda = agendaRef.get().await()
            val localAgenda = dao.getOwnAgenda(currentUid)
            val resolvedOwnerName = existingAgenda.getString("ownerName")?.trim()?.takeIf { it.isNotBlank() }
                ?: localAgenda?.ownerName?.trim()?.takeIf { it.isNotBlank() }
                ?: ownerName
            val existingEmails = (existingAgenda.get("sharedEmails") as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.trim().lowercase() }
                ?.distinct()
                .orEmpty()

            require(!existingEmails.contains(normalized)) {
                "Ese correo ya tiene acceso a tu agenda."
            }
            require(existingEmails.size < 25) {
                "Alcanzaste el límite de 25 cuentas compartidas."
            }

            val updatedEmails = existingEmails + normalized
            syncAgendaAndAudienciasEmails(
                ownerUid = currentUid,
                ownerEmail = ownerEmail,
                ownerName = resolvedOwnerName.take(140),
                agendaExists = existingAgenda.exists(),
                updatedEmails = updatedEmails,
                localConfig = localAgenda
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeSharedGmail(emailToRemove: String): Result<Unit> {
        val normalized = emailToRemove.trim().lowercase()
        val uid = auth.currentUser?.uid
        val docPath = "agendas/${uid ?: "unauthenticated"}"
        return try {
            val currentUid = requireUserId()
            val currentUser = auth.currentUser
            val ownerEmail = (currentUser?.email ?: "").trim().lowercase()
            val ownerName = (currentUser?.displayName ?: ownerEmail).trim().take(140)

            require(isNetworkOnline()) {
                "Se requiere conexión a internet para revocar el acceso compartido."
            }

            val agendaRef = agendasCollection().document(currentUid)
            val existingAgenda = agendaRef.get().await()
            if (!existingAgenda.exists()) return Result.success(Unit)

            val localAgenda = dao.getOwnAgenda(currentUid)
            val resolvedOwnerName = existingAgenda.getString("ownerName")?.trim()?.takeIf { it.isNotBlank() }
                ?: localAgenda?.ownerName?.trim()?.takeIf { it.isNotBlank() }
                ?: ownerName
            val existingEmails = (existingAgenda.get("sharedEmails") as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.trim().lowercase() }
                ?.distinct()
                .orEmpty()
            val updatedEmails = existingEmails.filterNot { it == normalized }

            syncAgendaAndAudienciasEmails(
                ownerUid = currentUid,
                ownerEmail = ownerEmail,
                ownerName = resolvedOwnerName.take(140),
                agendaExists = true,
                updatedEmails = updatedEmails,
                localConfig = localAgenda
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun syncAgendaAndAudienciasEmails(
        ownerUid: String,
        ownerEmail: String,
        ownerName: String,
        agendaExists: Boolean,
        updatedEmails: List<String>,
        localConfig: com.example.data.local.AgendaCompartidaEntity? = null,
    ) {
        val agendaRef = agendasCollection().document(ownerUid)
        val audienciasSnap = audienciasCollection(ownerUid).get().await()
        val docRefs = audienciasSnap.documents.map { it.reference }

        val firstBatch = db.batch()
        if (agendaExists) {
            firstBatch.update(
                agendaRef,
                mapOf(
                    "ownerEmail" to ownerEmail,
                    "ownerName" to ownerName,
                    "sharedEmails" to updatedEmails,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            val createAgendaPayload = mutableMapOf<String, Any>(
                "ownerUid" to ownerUid,
                "ownerEmail" to ownerEmail,
                "ownerName" to ownerName,
                "sharedEmails" to updatedEmails,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            localConfig?.let { cfg ->
                if (cfg.appTitle.isNotBlank()) createAgendaPayload["appTitle"] = cfg.appTitle.trim().take(120)
                if (cfg.calendarTitle.isNotBlank()) createAgendaPayload["calendarTitle"] = cfg.calendarTitle.trim().take(120)
                if (cfg.directoryTitle.isNotBlank()) createAgendaPayload["directoryTitle"] = cfg.directoryTitle.trim().take(120)
                if (cfg.summaryTitle.isNotBlank()) createAgendaPayload["summaryTitle"] = cfg.summaryTitle.trim().take(120)
            }
            firstBatch.set(agendaRef, createAgendaPayload)
        }

        val firstChunk = docRefs.take(400)
        firstChunk.forEach { ref ->
            firstBatch.update(
                ref,
                mapOf(
                    "sharedEmails" to updatedEmails,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        firstBatch.commit().await()

        docRefs.drop(400).chunked(450).forEach { chunk ->
            val extraBatch = db.batch()
            chunk.forEach { ref ->
                extraBatch.update(
                    ref,
                    mapOf(
                        "sharedEmails" to updatedEmails,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            extraBatch.commit().await()
        }
    }

    suspend fun createAudiencia(
        audiencia: Audiencia,
        sharedEmails: List<String> = emptyList(),
    ): Result<AutoScheduleResult> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/audiencias"
        return try {
            val uid = requireUserId()
            validateAudienciaInput(audiencia)

            val resolvedSharedEmails = if (sharedEmails.isNotEmpty()) {
                sharedEmails.map { it.trim().lowercase() }.distinct().take(25)
            } else {
                loadOwnerSharedEmails(uid)
            }

            val shouldAutoSchedule = audiencia.tieneProximaFecha &&
                audiencia.proximaFechaMillis != null &&
                audiencia.proximaFechaMillis > 0L

            val mainId = audiencia.id.takeIf { it.isNotBlank() } ?: generateValidDocId()
            val nextId = if (shouldAutoSchedule) generateValidDocId() else null
            val nowMs = System.currentTimeMillis()

            val mainDomain = audiencia.copy(
                id = mainId,
                userId = uid,
                sharedEmails = resolvedSharedEmails,
                audienciaSiguienteId = nextId ?: audiencia.audienciaSiguienteId
            )
            val mainEntity = mainDomain.toRoomEntity(
                ownerUid = uid,
                syncStatus = SyncStatus.PENDING_UPSERT,
                localUpdatedAtMillis = nowMs
            )
            dao.queueLocalUpsert(mainEntity, SyncOpType.CREATE, nowMs)

            var nextDomain: Audiencia? = null
            var nextEntity: com.example.data.local.AudienciaEntity? = null
            if (shouldAutoSchedule && nextId != null && audiencia.proximaFechaMillis != null) {
                val resolvedNextTipo = audiencia.proximoTipoAudiencia.trim()
                    .ifBlank { audiencia.tipoAudiencia.trim() }
                    .take(120)
                val child = Audiencia(
                    id = nextId,
                    userId = uid,
                    sharedEmails = resolvedSharedEmails,
                    nuc = audiencia.nuc.trim().take(80),
                    numeroCausa = audiencia.numeroCausa.trim().take(80),
                    tipoAudiencia = resolvedNextTipo,
                    fechaHoraMillis = audiencia.proximaFechaMillis,
                    salaOJuzgado = audiencia.salaOJuzgado.trim().take(120),
                    juez = audiencia.juez.trim().take(140),
                    ministerioPublico = audiencia.ministerioPublico.trim().take(140),
                    asesorJuridico = audiencia.asesorJuridico.trim().take(140),
                    victima = audiencia.victima.trim().take(140),
                    defensor = audiencia.defensor.trim().take(140),
                    imputado = audiencia.imputado.trim().take(140),
                    tipoDelito = audiencia.tipoDelito.trim().take(160),
                    clasificacionJuridica = audiencia.clasificacionJuridica.trim().take(400),
                    estado = EstadoAudiencia.PROGRAMADA.code,
                    resultado = "",
                    tieneProximaFecha = false,
                    proximaFechaMillis = null,
                    proximoTipoAudiencia = "",
                    audienciaOrigenId = mainId
                )
                val childEntity = child.toRoomEntity(
                    ownerUid = uid,
                    syncStatus = SyncStatus.PENDING_UPSERT,
                    localUpdatedAtMillis = nowMs + 1L
                )
                dao.queueLocalUpsert(childEntity, SyncOpType.CREATE, nowMs + 1L)
                nextDomain = child
                nextEntity = childEntity
            }

            if (!isNetworkOnline()) {
                return Result.success(
                    AutoScheduleResult(
                        autoScheduled = shouldAutoSchedule,
                        nextAudienciaId = nextId,
                        updatedExistingNext = false,
                        savedOfflinePendingSync = true
                    )
                )
            }

            val colRef = audienciasCollection(uid)
            val mainRef = colRef.document(mainId)
            docPath = mainRef.path

            try {
                val batch = db.batch()
                batch.set(mainRef, buildCreatePayload(uid, resolvedSharedEmails, mainDomain))
                if (shouldAutoSchedule && nextId != null && nextDomain != null) {
                    val nextRef = colRef.document(nextId)
                    batch.set(nextRef, buildCreatePayload(uid, resolvedSharedEmails, nextDomain))
                }
                batch.commit().await()
                dao.markSynced(mainEntity)
                if (nextEntity != null) dao.markSynced(nextEntity)

                Result.success(
                    AutoScheduleResult(
                        autoScheduled = shouldAutoSchedule,
                        nextAudienciaId = nextId,
                        updatedExistingNext = false,
                        savedOfflinePendingSync = false
                    )
                )
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    Result.success(
                        AutoScheduleResult(
                            autoScheduled = shouldAutoSchedule,
                            nextAudienciaId = nextId,
                            updatedExistingNext = false,
                            savedOfflinePendingSync = true
                        )
                    )
                } else {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAudiencia(
        audiencia: Audiencia,
        sharedEmails: List<String>? = null,
    ): Result<AutoScheduleResult> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/audiencias/${audiencia.id}"
        return try {
            val uid = requireUserId()
            require(audiencia.id.isNotBlank()) { "El identificador de la audiencia no puede estar vacío." }
            validateAudienciaInput(audiencia)

            val resolvedSharedEmails = (sharedEmails ?: loadOwnerSharedEmails(uid))
                .map { it.trim().lowercase() }
                .distinct()
                .take(25)

            val shouldAutoSchedule = audiencia.tieneProximaFecha &&
                audiencia.proximaFechaMillis != null &&
                audiencia.proximaFechaMillis > 0L

            val storedMainEntity = dao.getAudienciaById(audiencia.id)
            val existingNextId = audiencia.audienciaSiguienteId?.takeIf { it.isNotBlank() }
                ?: storedMainEntity?.audienciaSiguienteId?.takeIf { it.isNotBlank() }
            var targetNextId: String? = existingNextId
            var updatedExisting = false
            var createNewNext = false

            if (shouldAutoSchedule) {
                if (existingNextId != null) {
                    val localNext = dao.getAudienciaById(existingNextId)
                    if (localNext != null && localNext.syncStatus != SyncStatus.PENDING_DELETE) {
                        updatedExisting = true
                    } else if (isNetworkOnline()) {
                        val existingNextSnap = audienciasCollection(uid).document(existingNextId).get().await()
                        if (existingNextSnap.exists()) {
                            updatedExisting = true
                        } else {
                            targetNextId = generateValidDocId()
                            createNewNext = true
                        }
                    } else {
                        targetNextId = generateValidDocId()
                        createNewNext = true
                    }
                } else {
                    targetNextId = generateValidDocId()
                    createNewNext = true
                }
            }

            val nowMs = System.currentTimeMillis()
            val mainDomain = audiencia.copy(
                userId = uid,
                sharedEmails = resolvedSharedEmails,
                tieneProximaFecha = shouldAutoSchedule,
                proximaFechaMillis = if (shouldAutoSchedule) audiencia.proximaFechaMillis else null,
                proximoTipoAudiencia = if (shouldAutoSchedule) audiencia.proximoTipoAudiencia.trim() else "",
                audienciaSiguienteId = if (shouldAutoSchedule) targetNextId else audiencia.audienciaSiguienteId
            )
            val mainEntity = mainDomain.toRoomEntity(
                ownerUid = uid,
                syncStatus = SyncStatus.PENDING_UPSERT,
                localUpdatedAtMillis = nowMs
            )
            dao.queueLocalUpsert(mainEntity, SyncOpType.UPDATE, nowMs)

            var nextDomain: Audiencia? = null
            var nextEntity: com.example.data.local.AudienciaEntity? = null
            if (shouldAutoSchedule && targetNextId != null && audiencia.proximaFechaMillis != null) {
                val nextTipo = audiencia.proximoTipoAudiencia.trim()
                    .ifBlank { audiencia.tipoAudiencia.trim() }
                    .take(120)
                val existingLocalChild = dao.getAudienciaById(targetNextId)?.toDomain()
                val child = (existingLocalChild ?: Audiencia(
                    id = targetNextId,
                    userId = uid,
                    estado = EstadoAudiencia.PROGRAMADA.code,
                    audienciaOrigenId = audiencia.id
                )).copy(
                    id = targetNextId,
                    userId = uid,
                    sharedEmails = resolvedSharedEmails,
                    nuc = audiencia.nuc.trim().take(80),
                    numeroCausa = audiencia.numeroCausa.trim().take(80),
                    tipoAudiencia = nextTipo,
                    fechaHoraMillis = audiencia.proximaFechaMillis,
                    salaOJuzgado = audiencia.salaOJuzgado.trim().take(120),
                    juez = audiencia.juez.trim().take(140),
                    ministerioPublico = audiencia.ministerioPublico.trim().take(140),
                    asesorJuridico = audiencia.asesorJuridico.trim().take(140),
                    victima = audiencia.victima.trim().take(140),
                    defensor = audiencia.defensor.trim().take(140),
                    imputado = audiencia.imputado.trim().take(140),
                    tipoDelito = audiencia.tipoDelito.trim().take(160),
                    clasificacionJuridica = audiencia.clasificacionJuridica.trim().take(400)
                )
                val childEntity = child.toRoomEntity(
                    ownerUid = uid,
                    syncStatus = SyncStatus.PENDING_UPSERT,
                    localUpdatedAtMillis = nowMs + 1L
                )
                dao.queueLocalUpsert(
                    entity = childEntity,
                    requestedOpType = if (updatedExisting) SyncOpType.UPDATE else SyncOpType.CREATE,
                    nowMillis = nowMs + 1L
                )
                nextDomain = child
                nextEntity = childEntity
            }

            if (!isNetworkOnline()) {
                return Result.success(
                    AutoScheduleResult(
                        autoScheduled = shouldAutoSchedule,
                        nextAudienciaId = if (shouldAutoSchedule) targetNextId else null,
                        updatedExistingNext = updatedExisting,
                        savedOfflinePendingSync = true
                    )
                )
            }

            val colRef = audienciasCollection(uid)
            val docRef = colRef.document(audiencia.id)
            docPath = docRef.path

            try {
                // If main doc was created offline and hasn't reached server yet, flush via syncPendingOperations
                val pendingMainOp = dao.getPendingOp(audiencia.id)
                if (pendingMainOp?.opType == SyncOpType.CREATE) {
                    syncPendingOperations(uid).getOrThrow()
                    return Result.success(
                        AutoScheduleResult(
                            autoScheduled = shouldAutoSchedule,
                            nextAudienciaId = if (shouldAutoSchedule) targetNextId else null,
                            updatedExistingNext = updatedExisting,
                            savedOfflinePendingSync = false
                        )
                    )
                }

                val updateMap = buildUpdateMap(mainDomain, resolvedSharedEmails)
                val batch = db.batch()
                batch.update(docRef, updateMap)

                if (shouldAutoSchedule && targetNextId != null && nextDomain != null) {
                    val nextRef = colRef.document(targetNextId)
                    if (updatedExisting) {
                        val pendingNextOp = dao.getPendingOp(targetNextId)
                        if (pendingNextOp?.opType == SyncOpType.CREATE) {
                            batch.set(nextRef, buildCreatePayload(uid, resolvedSharedEmails, nextDomain))
                        } else {
                            batch.update(nextRef, buildNextUpdateMap(nextDomain, resolvedSharedEmails))
                        }
                    } else if (createNewNext) {
                        batch.set(nextRef, buildCreatePayload(uid, resolvedSharedEmails, nextDomain))
                    }
                }

                batch.commit().await()
                dao.markSynced(mainEntity)
                if (nextEntity != null) dao.markSynced(nextEntity)

                Result.success(
                    AutoScheduleResult(
                        autoScheduled = shouldAutoSchedule,
                        nextAudienciaId = if (shouldAutoSchedule) targetNextId else null,
                        updatedExistingNext = updatedExisting,
                        savedOfflinePendingSync = false
                    )
                )
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    Result.success(
                        AutoScheduleResult(
                            autoScheduled = shouldAutoSchedule,
                            nextAudienciaId = if (shouldAutoSchedule) targetNextId else null,
                            updatedExistingNext = updatedExisting,
                            savedOfflinePendingSync = true
                        )
                    )
                } else {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun registrarResultadoYAutoAgendar(
        audiencia: Audiencia,
        nuevoEstado: EstadoAudiencia,
        resultadoTexto: String,
        tieneProximaFecha: Boolean,
        proximaFechaMillis: Long?,
        proximoTipoAudiencia: String,
        sharedEmails: List<String>? = null,
    ): Result<AutoScheduleResult> {
        val updated = audiencia.copy(
            estado = nuevoEstado.code,
            resultado = resultadoTexto.trim(),
            tieneProximaFecha = tieneProximaFecha && proximaFechaMillis != null && proximaFechaMillis > 0L,
            proximaFechaMillis = if (tieneProximaFecha) proximaFechaMillis else null,
            proximoTipoAudiencia = if (tieneProximaFecha) proximoTipoAudiencia.trim() else ""
        )
        return updateAudiencia(updated, sharedEmails)
    }

    suspend fun deleteAudiencia(audienciaId: String): Result<Boolean> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/audiencias/$audienciaId"
        return try {
            val uid = requireUserId()
            require(audienciaId.isNotBlank()) { "ID inválido para eliminar." }

            dao.queueLocalDelete(audienciaId, uid)
            val stillPendingDelete = dao.getPendingOp(audienciaId) != null
            if (!stillPendingDelete) {
                // Was created offline & never synced to Firestore; completely removed locally.
                return Result.success(false)
            }

            if (!isNetworkOnline()) {
                return Result.success(true)
            }

            val docRef = audienciasCollection(uid).document(audienciaId)
            docPath = docRef.path
            try {
                docRef.delete().await()
                dao.markDeleteSynced(audienciaId)
                Result.success(false)
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    Result.success(true)
                } else {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun loadOwnerSharedEmails(uid: String): List<String> {
        val localAgenda = dao.observeOwnAgenda(uid).firstOrNull()
        if (localAgenda != null) {
            return localAgenda.toDomain().sharedEmails
                .map { it.trim().lowercase() }
                .distinct()
                .take(25)
        }
        if (!isNetworkOnline()) return emptyList()
        return try {
            val snap = agendasCollection().document(uid).get().await()
            (snap.get("sharedEmails") as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.trim().lowercase() }
                ?.distinct()
                ?.take(25)
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun validateAudienciaInput(audiencia: Audiencia) {
        val tipo = audiencia.tipoAudiencia.trim()
        require(tipo.isNotEmpty() && tipo.length <= 120) {
            "El tipo de audiencia es obligatorio (máximo 120 caracteres)."
        }
        require(audiencia.fechaHoraMillis > 0L) {
            "La fecha y hora de la audiencia deben ser válidas."
        }
        if (audiencia.tieneProximaFecha) {
            val nextMillis = audiencia.proximaFechaMillis
            require(nextMillis != null && nextMillis > 0L) {
                "Debes seleccionar una próxima fecha y hora válida."
            }
        }
    }

    private fun buildUpdateMap(
        audiencia: Audiencia,
        resolvedSharedEmails: List<String>,
    ): MutableMap<String, Any> {
        val shouldAutoSchedule = audiencia.tieneProximaFecha &&
            audiencia.proximaFechaMillis != null &&
            audiencia.proximaFechaMillis > 0L
        val targetNextId = audiencia.audienciaSiguienteId?.takeIf { it.isNotBlank() }

        val updateMap = mutableMapOf<String, Any>(
            "sharedEmails" to resolvedSharedEmails,
            "nuc" to audiencia.nuc.trim().take(80),
            "numeroCausa" to audiencia.numeroCausa.trim().take(80),
            "tipoAudiencia" to audiencia.tipoAudiencia.trim().take(120),
            "fechaHoraMillis" to audiencia.fechaHoraMillis,
            "salaOJuzgado" to audiencia.salaOJuzgado.trim().take(120),
            "juez" to audiencia.juez.trim().take(140),
            "ministerioPublico" to audiencia.ministerioPublico.trim().take(140),
            "asesorJuridico" to audiencia.asesorJuridico.trim().take(140),
            "victima" to audiencia.victima.trim().take(140),
            "defensor" to audiencia.defensor.trim().take(140),
            "imputado" to audiencia.imputado.trim().take(140),
            "tipoDelito" to audiencia.tipoDelito.trim().take(160),
            "clasificacionJuridica" to audiencia.clasificacionJuridica.trim().take(400),
            "estado" to EstadoAudiencia.fromCode(audiencia.estado).code,
            "resultado" to audiencia.resultado.trim().take(2000),
            "tieneProximaFecha" to shouldAutoSchedule,
            "proximoTipoAudiencia" to (if (shouldAutoSchedule) audiencia.proximoTipoAudiencia.trim().take(120) else ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (shouldAutoSchedule && audiencia.proximaFechaMillis != null) {
            updateMap["proximaFechaMillis"] = audiencia.proximaFechaMillis
            if (targetNextId != null) {
                updateMap["audienciaSiguienteId"] = targetNextId
            }
        } else {
            updateMap["proximaFechaMillis"] = FieldValue.delete()
        }
        return updateMap
    }

    private fun buildNextUpdateMap(
        child: Audiencia,
        resolvedSharedEmails: List<String>,
    ): Map<String, Any> {
        return mapOf(
            "sharedEmails" to resolvedSharedEmails,
            "nuc" to child.nuc.trim().take(80),
            "numeroCausa" to child.numeroCausa.trim().take(80),
            "tipoAudiencia" to child.tipoAudiencia.trim().take(120),
            "fechaHoraMillis" to child.fechaHoraMillis,
            "salaOJuzgado" to child.salaOJuzgado.trim().take(120),
            "juez" to child.juez.trim().take(140),
            "ministerioPublico" to child.ministerioPublico.trim().take(140),
            "asesorJuridico" to child.asesorJuridico.trim().take(140),
            "victima" to child.victima.trim().take(140),
            "defensor" to child.defensor.trim().take(140),
            "imputado" to child.imputado.trim().take(140),
            "tipoDelito" to child.tipoDelito.trim().take(160),
            "clasificacionJuridica" to child.clasificacionJuridica.trim().take(400),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildCreatePayload(
        uid: String,
        sharedEmails: List<String>,
        audiencia: Audiencia,
    ): Map<String, Any> {
        val shouldAutoSchedule = audiencia.tieneProximaFecha &&
            audiencia.proximaFechaMillis != null &&
            audiencia.proximaFechaMillis > 0L

        return mapOf(
            "userId" to uid,
            "sharedEmails" to sharedEmails,
            "nuc" to audiencia.nuc.trim().take(80),
            "numeroCausa" to audiencia.numeroCausa.trim().take(80),
            "tipoAudiencia" to audiencia.tipoAudiencia.trim().take(120),
            "fechaHoraMillis" to audiencia.fechaHoraMillis,
            "salaOJuzgado" to audiencia.salaOJuzgado.trim().take(120),
            "juez" to audiencia.juez.trim().take(140),
            "ministerioPublico" to audiencia.ministerioPublico.trim().take(140),
            "asesorJuridico" to audiencia.asesorJuridico.trim().take(140),
            "victima" to audiencia.victima.trim().take(140),
            "defensor" to audiencia.defensor.trim().take(140),
            "imputado" to audiencia.imputado.trim().take(140),
            "tipoDelito" to audiencia.tipoDelito.trim().take(160),
            "clasificacionJuridica" to audiencia.clasificacionJuridica.trim().take(400),
            "estado" to EstadoAudiencia.fromCode(audiencia.estado).code,
            "resultado" to audiencia.resultado.trim().take(2000),
            "tieneProximaFecha" to shouldAutoSchedule,
            "proximaFechaMillis" to (if (shouldAutoSchedule) audiencia.proximaFechaMillis else null),
            "proximoTipoAudiencia" to (if (shouldAutoSchedule) audiencia.proximoTipoAudiencia.trim().take(120) else ""),
            "audienciaOrigenId" to audiencia.audienciaOrigenId?.takeIf { it.isNotBlank() },
            "audienciaSiguienteId" to audiencia.audienciaSiguienteId?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun isTransientNetworkError(error: Throwable): Boolean {
        val ffe = unwrapFirestoreException(error) ?: return !isNetworkOnline()
        return ffe.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            ffe.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
            ffe.code == FirebaseFirestoreException.Code.ABORTED ||
            !isNetworkOnline()
    }

    companion object {
        private const val TAG = "AudienciaRepository"
    }
}
