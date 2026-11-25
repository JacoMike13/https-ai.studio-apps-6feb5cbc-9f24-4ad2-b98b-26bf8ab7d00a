package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.data.AgendaCompartida
import com.example.data.AppAppearanceConfig
import com.example.data.Audiencia
import com.example.data.AudienciaRepository
import com.example.data.AutoScheduleResult
import com.example.data.EstadoAudiencia
import com.example.data.NetworkMonitor
import com.example.data.UiState
import com.example.data.local.AgendaDatabase
import com.example.util.DiaKey
import com.example.util.FechaHelper
import com.example.util.MesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgendaFiltersState(
    val selectedMes: MesKey = FechaHelper.currentMesKey(),
    val selectedDia: DiaKey = FechaHelper.todayDiaKey(),
    val selectedTipoFilter: String? = null,
    val selectedEstadoFilter: EstadoAudiencia? = null,
    val searchQuery: String = "",
    val calendarSearchQuery: String = "",
    val isSaving: Boolean = false,
    val feedbackMessage: String? = null,
)

data class ActiveAgendaInfo(
    val ownerUid: String,
    val isOwnerMode: Boolean,
    val ownerEmail: String,
    val ownerName: String,
)

data class SyncStatusUi(
    val isOnline: Boolean = true,
    val pendingCount: Int = 0,
    val isSyncing: Boolean = false,
)

sealed interface ConfigSyncState {
    data object Synced : ConfigSyncState
    data object Syncing : ConfigSyncState
    data object PendingOffline : ConfigSyncState
    data class Error(val message: String) : ConfigSyncState
}

class AudienciasViewModel(
    private val repository: AudienciaRepository,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserDisplayName: String = "",
) : ViewModel() {

    private val _activeOwnerUid = MutableStateFlow(currentUserId)
    val activeOwnerUid: StateFlow<String> = _activeOwnerUid.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    private val _configSyncState = MutableStateFlow<ConfigSyncState>(ConfigSyncState.Synced)
    val configSyncState: StateFlow<ConfigSyncState> = _configSyncState.asStateFlow()
    private var configAutoSyncJob: Job? = null

    val syncStatus: StateFlow<SyncStatusUi> = combine(
        networkMonitor.isOnline,
        repository.observePendingSyncCount(currentUserId),
        _isSyncing
    ) { isOnline, pendingCount, isSyncing ->
        SyncStatusUi(
            isOnline = isOnline,
            pendingCount = pendingCount,
            isSyncing = isSyncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SyncStatusUi(
            isOnline = networkMonitor.checkCurrentlyOnline(),
            pendingCount = 0,
            isSyncing = false
        )
    )

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    syncNow(showNoPendingMessage = false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }

    val ownAgenda: StateFlow<AgendaCompartida?> =
        repository.observeOwnAgenda(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing own agenda config", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    val sharedAgendasWithMe: StateFlow<List<AgendaCompartida>> =
        repository.observeSharedAgendasWithMe(currentUserEmail)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing shared agendas", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val appAppearance: StateFlow<AppAppearanceConfig> =
        combine(_activeOwnerUid, ownAgenda, sharedAgendasWithMe) { ownerUid, own, sharedList ->
            if (ownerUid == currentUserId) {
                AppAppearanceConfig.resolve(
                    ownAgenda = own,
                    fallbackDisplayName = currentUserDisplayName.ifBlank {
                        currentUserEmail.substringBefore("@")
                    }
                )
            } else {
                val activeShared = sharedList.firstOrNull { it.ownerUid == ownerUid }
                AppAppearanceConfig.resolve(
                    ownAgenda = activeShared,
                    fallbackDisplayName = activeShared?.ownerEmail ?: "Cuenta principal"
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppAppearanceConfig.resolve(
                ownAgenda = null,
                fallbackDisplayName = currentUserDisplayName.ifBlank {
                    currentUserEmail.substringBefore("@")
                }
            )
        )

    val activeAgendaInfo: StateFlow<ActiveAgendaInfo> =
        combine(_activeOwnerUid, sharedAgendasWithMe, appAppearance) { ownerUid, sharedList, appearance ->
            if (ownerUid == currentUserId) {
                ActiveAgendaInfo(
                    ownerUid = currentUserId,
                    isOwnerMode = true,
                    ownerEmail = currentUserEmail,
                    ownerName = appearance.ownerName.ifBlank { "Mi Agenda" }
                )
            } else {
                val match = sharedList.firstOrNull { it.ownerUid == ownerUid }
                ActiveAgendaInfo(
                    ownerUid = ownerUid,
                    isOwnerMode = false,
                    ownerEmail = match?.ownerEmail ?: "Cuenta principal",
                    ownerName = match?.ownerName?.ifBlank { match.ownerEmail } ?: "Agenda compartida"
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ActiveAgendaInfo(
                ownerUid = currentUserId,
                isOwnerMode = true,
                ownerEmail = currentUserEmail,
                ownerName = currentUserDisplayName.ifBlank { "Mi Agenda" }
            )
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val audienciasState: StateFlow<UiState<List<Audiencia>>> =
        _activeOwnerUid
            .flatMapLatest { targetOwnerUid ->
                repository.observeAudienciasForAgenda(
                    ownerUid = targetOwnerUid,
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail
                )
                    .map<List<Audiencia>, UiState<List<Audiencia>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing audiencias", rootError)
                        emit(UiState.Error(rootError.message ?: "Error al cargar las audiencias."))
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _filters = MutableStateFlow(AgendaFiltersState())
    val filters: StateFlow<AgendaFiltersState> = _filters.asStateFlow()

    fun selectAgenda(ownerUid: String) {
        _activeOwnerUid.value = ownerUid.ifBlank { currentUserId }
    }

    fun returnToOwnAgenda() {
        _activeOwnerUid.value = currentUserId
    }

    fun selectDia(dia: DiaKey) {
        _filters.update {
            it.copy(
                selectedDia = dia,
                selectedMes = FechaHelper.mesKeyFromDiaKey(dia)
            )
        }
    }

    fun previousMonth() {
        _filters.update { it.copy(selectedMes = it.selectedMes.plusMonths(-1)) }
    }

    fun nextMonth() {
        _filters.update { it.copy(selectedMes = it.selectedMes.plusMonths(1)) }
    }

    fun jumpToToday() {
        val today = FechaHelper.todayDiaKey()
        _filters.update {
            it.copy(
                selectedDia = today,
                selectedMes = FechaHelper.mesKeyFromDiaKey(today)
            )
        }
    }

    fun setTipoFilter(tipo: String?) {
        _filters.update {
            it.copy(selectedTipoFilter = if (it.selectedTipoFilter == tipo) null else tipo)
        }
    }

    fun setEstadoFilter(estado: EstadoAudiencia?) {
        _filters.update {
            it.copy(selectedEstadoFilter = if (it.selectedEstadoFilter == estado) null else estado)
        }
    }

    fun setSearchQuery(query: String) {
        _filters.update { it.copy(searchQuery = query) }
    }

    fun setCalendarSearchQuery(query: String) {
        _filters.update { it.copy(calendarSearchQuery = query) }
    }

    fun clearFeedback() {
        _filters.update { it.copy(feedbackMessage = null) }
    }

    fun autoSyncConfiguration(
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
    ) {
        if (_activeOwnerUid.value != currentUserId) return
        val cleanName = ownerName.trim()
        val cleanApp = appTitle.trim()
        val cleanCal = calendarTitle.trim()
        val cleanDir = directoryTitle.trim()
        val cleanSum = summaryTitle.trim()
        if (cleanName.isEmpty() || cleanApp.isEmpty() || cleanCal.isEmpty() || cleanDir.isEmpty() || cleanSum.isEmpty()) {
            return
        }

        configAutoSyncJob?.cancel()
        configAutoSyncJob = viewModelScope.launch {
            _configSyncState.value = ConfigSyncState.Syncing
            repository.updateUserConfiguration(
                ownerName = cleanName,
                appTitle = cleanApp,
                calendarTitle = cleanCal,
                directoryTitle = cleanDir,
                summaryTitle = cleanSum
            ).fold(
                onSuccess = { savedOffline ->
                    _configSyncState.value = if (savedOffline) {
                        ConfigSyncState.PendingOffline
                    } else {
                        ConfigSyncState.Synced
                    }
                },
                onFailure = { err ->
                    _configSyncState.value = ConfigSyncState.Error(
                        err.localizedMessage ?: "No se pudo sincronizar la configuración con Firebase."
                    )
                }
            )
        }
    }

    fun saveConfiguration(
        ownerName: String,
        appTitle: String,
        calendarTitle: String,
        directoryTitle: String,
        summaryTitle: String,
        showFeedback: Boolean = true,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_activeOwnerUid.value != currentUserId) {
            if (showFeedback) {
                _filters.update {
                    it.copy(feedbackMessage = "Solo el usuario principal puede modificar la configuración.")
                }
            }
            onComplete(false)
            return
        }
        configAutoSyncJob?.cancel()
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            _configSyncState.value = ConfigSyncState.Syncing
            repository.updateUserConfiguration(
                ownerName = ownerName,
                appTitle = appTitle,
                calendarTitle = calendarTitle,
                directoryTitle = directoryTitle,
                summaryTitle = summaryTitle
            ).fold(
                onSuccess = { savedOffline ->
                    _configSyncState.value = if (savedOffline) {
                        ConfigSyncState.PendingOffline
                    } else {
                        ConfigSyncState.Synced
                    }
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = if (showFeedback) {
                                if (savedOffline) {
                                    "Configuración guardada sin conexión. Se sincronizará automáticamente en Firebase al recuperar la red."
                                } else {
                                    "Configuración sincronizada automáticamente en Firebase."
                                }
                            } else {
                                it.feedbackMessage
                            }
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    val errMsg = err.localizedMessage ?: "No se pudo guardar la configuración."
                    _configSyncState.value = ConfigSyncState.Error(errMsg)
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = if (showFeedback) errMsg else it.feedbackMessage
                        )
                    }
                    onComplete(false)
                }
            )
        }
    }

    fun syncNow(showNoPendingMessage: Boolean = true) {
        if (_isSyncing.value) return
        if (!networkMonitor.checkCurrentlyOnline()) {
            if (showNoPendingMessage) {
                _filters.update {
                    it.copy(feedbackMessage = "Sin conexión a internet. Tus cambios permanecen guardados en Room.")
                }
            }
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.syncPendingOperations(currentUserId)
            _isSyncing.value = false
            result.fold(
                onSuccess = { syncedCount ->
                    if (_configSyncState.value is ConfigSyncState.PendingOffline ||
                        _configSyncState.value is ConfigSyncState.Error
                    ) {
                        _configSyncState.value = ConfigSyncState.Synced
                    }
                    if (syncedCount > 0) {
                        val label = if (syncedCount == 1) "1 cambio pendiente" else "$syncedCount cambios pendientes"
                        _filters.update {
                            it.copy(feedbackMessage = "Se sincronizó $label con Firebase.")
                        }
                    } else if (showNoPendingMessage) {
                        _filters.update {
                            it.copy(feedbackMessage = "La agenda ya está sincronizada con Firebase.")
                        }
                    }
                },
                onFailure = { err ->
                    if (showNoPendingMessage) {
                        _filters.update {
                            it.copy(
                                feedbackMessage = err.localizedMessage
                                    ?: "No se pudieron sincronizar los cambios pendientes."
                            )
                        }
                    }
                }
            )
        }
    }

    fun addSharedGmail(
        email: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            repository.addSharedGmail(email).fold(
                onSuccess = {
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = "Cuenta ${email.trim().lowercase()} agregada con acceso de visualización."
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo agregar el correo."
                        )
                    }
                    onComplete(false)
                }
            )
        }
    }

    fun removeSharedGmail(email: String) {
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            repository.removeSharedGmail(email).fold(
                onSuccess = {
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = "Se revocó el acceso a $email."
                        )
                    }
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo quitar la cuenta."
                        )
                    }
                }
            )
        }
    }

    fun saveAudiencia(
        audiencia: Audiencia,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_activeOwnerUid.value != currentUserId) {
            _filters.update {
                it.copy(feedbackMessage = "Solo la cuenta principal puede modificar esta agenda.")
            }
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            val currentSharedEmails = ownAgenda.value?.sharedEmails.orEmpty()
            val result: Result<AutoScheduleResult> = if (audiencia.id.isBlank()) {
                repository.createAudiencia(audiencia, currentSharedEmails)
            } else {
                repository.updateAudiencia(audiencia, currentSharedEmails)
            }
            result.fold(
                onSuccess = { outcome ->
                    val msg = when {
                        outcome.savedOfflinePendingSync && outcome.autoScheduled ->
                            "Guardado sin conexión en Room con próxima fecha automática. Se sincronizará al recuperar la red."
                        outcome.savedOfflinePendingSync ->
                            "Guardado sin conexión en Room. Se sincronizará automáticamente al recuperar la red."
                        outcome.autoScheduled && outcome.updatedExistingNext ->
                            "Audiencia actualizada y próxima audiencia vinculada actualizada."
                        outcome.autoScheduled ->
                            "Audiencia guardada y próxima fecha agendada en automático."
                        audiencia.id.isBlank() ->
                            "Audiencia registrada en tu calendario."
                        else ->
                            "Audiencia actualizada correctamente."
                    }
                    val diaDestino = FechaHelper.diaKeyFromMillis(audiencia.fechaHoraMillis)
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = msg,
                            selectedDia = diaDestino,
                            selectedMes = FechaHelper.mesKeyFromDiaKey(diaDestino)
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo guardar la audiencia."
                        )
                    }
                    onComplete(false)
                }
            )
        }
    }

    fun autoScheduleProximaFecha(
        audiencia: Audiencia,
        proximaFechaMillis: Long,
        proximoTipoAudiencia: String,
        resultadoTexto: String = audiencia.resultado,
        nuevoEstado: EstadoAudiencia = audiencia.estadoEnum(),
        onComplete: (String?) -> Unit = {},
    ) {
        if (_activeOwnerUid.value != currentUserId) {
            _filters.update {
                it.copy(feedbackMessage = "Solo la cuenta principal puede programar próximas fechas.")
            }
            onComplete(null)
            return
        }
        if (audiencia.id.isBlank() || proximaFechaMillis <= 0L) {
            onComplete(null)
            return
        }
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            val currentSharedEmails = ownAgenda.value?.sharedEmails.orEmpty()
            val resolvedNextTipo = proximoTipoAudiencia.trim().ifBlank { audiencia.tipoAudiencia.trim() }
            val updatedMain = audiencia.copy(
                estado = nuevoEstado.code,
                resultado = resultadoTexto.trim(),
                tieneProximaFecha = true,
                proximaFechaMillis = proximaFechaMillis,
                proximoTipoAudiencia = resolvedNextTipo
            )
            repository.updateAudiencia(updatedMain, currentSharedEmails).fold(
                onSuccess = { outcome ->
                    val formattedDate = FechaHelper.formatFullDateTime(proximaFechaMillis)
                    val msg = when {
                        outcome.savedOfflinePendingSync ->
                            "Próxima fecha guardada sin conexión en Room ($formattedDate). Se sincronizará al recuperar la red."
                        outcome.updatedExistingNext ->
                            "Próxima audiencia actualizada automáticamente en el calendario ($formattedDate)."
                        else ->
                            "Próxima audiencia agendada automáticamente en el calendario ($formattedDate)."
                    }
                    val diaDestino = FechaHelper.diaKeyFromMillis(proximaFechaMillis)
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = msg,
                            selectedDia = diaDestino,
                            selectedMes = FechaHelper.mesKeyFromDiaKey(diaDestino)
                        )
                    }
                    onComplete(outcome.nextAudienciaId)
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo agendar la próxima fecha."
                        )
                    }
                    onComplete(null)
                }
            )
        }
    }

    fun registrarResultado(
        audiencia: Audiencia,
        nuevoEstado: EstadoAudiencia,
        resultadoTexto: String,
        tieneProximaFecha: Boolean,
        proximaFechaMillis: Long?,
        proximoTipoAudiencia: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_activeOwnerUid.value != currentUserId) {
            _filters.update {
                it.copy(feedbackMessage = "Solo la cuenta principal puede registrar resultados en esta agenda.")
            }
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            val currentSharedEmails = ownAgenda.value?.sharedEmails.orEmpty()
            val result = repository.registrarResultadoYAutoAgendar(
                audiencia = audiencia,
                nuevoEstado = nuevoEstado,
                resultadoTexto = resultadoTexto,
                tieneProximaFecha = tieneProximaFecha,
                proximaFechaMillis = proximaFechaMillis,
                proximoTipoAudiencia = proximoTipoAudiencia,
                sharedEmails = currentSharedEmails
            )
            result.fold(
                onSuccess = { outcome ->
                    val msg = when {
                        outcome.savedOfflinePendingSync && outcome.autoScheduled ->
                            "Resultado guardado sin conexión con próxima audiencia en Room. Se sincronizará al recuperar la red."
                        outcome.savedOfflinePendingSync ->
                            "Resultado guardado sin conexión en Room. Se sincronizará automáticamente al recuperar la red."
                        outcome.autoScheduled && outcome.updatedExistingNext ->
                            "Resultado guardado y próxima audiencia actualizada automáticamente."
                        outcome.autoScheduled ->
                            "Resultado guardado. Se agendó en automático la próxima audiencia."
                        else ->
                            "Resultado de la audiencia actualizado."
                    }
                    _filters.update { it.copy(isSaving = false, feedbackMessage = msg) }
                    onComplete(true)
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo registrar el resultado."
                        )
                    }
                    onComplete(false)
                }
            )
        }
    }

    fun deleteAudiencia(
        audienciaId: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (_activeOwnerUid.value != currentUserId) {
            _filters.update {
                it.copy(feedbackMessage = "Solo la cuenta principal puede eliminar audiencias.")
            }
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _filters.update { it.copy(isSaving = true) }
            repository.deleteAudiencia(audienciaId).fold(
                onSuccess = { pendingSync ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = if (pendingSync) {
                                "Audiencia eliminada localmente. Se sincronizará con Firebase al recuperar la red."
                            } else {
                                "Audiencia eliminada."
                            }
                        )
                    }
                    onComplete(true)
                },
                onFailure = { err ->
                    _filters.update {
                        it.copy(
                            isSaving = false,
                            feedbackMessage = err.localizedMessage ?: "No se pudo eliminar la audiencia."
                        )
                    }
                    onComplete(false)
                }
            )
        }
    }

    companion object {
        private const val TAG = "AudienciasVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun factory(
            currentUserId: String,
            currentUserEmail: String,
            currentUserDisplayName: String = "",
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    val dao = AgendaDatabase.getInstance(app).agendaDao()
                    val networkMonitor = NetworkMonitor(app)
                    return AudienciasViewModel(
                        repository = AudienciaRepository(
                            db = db,
                            dao = dao,
                            networkMonitor = networkMonitor
                        ),
                        networkMonitor = networkMonitor,
                        currentUserId = currentUserId,
                        currentUserEmail = currentUserEmail,
                        currentUserDisplayName = currentUserDisplayName
                    ) as T
                }
            }
    }
}

private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

private fun String.normalizeForSearch(): String {
    val decomposed = java.text.Normalizer.normalize(trim().lowercase(), java.text.Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(decomposed, "")
}

fun filterAudiencias(
    items: List<Audiencia>,
    tipoFilter: String?,
    estadoFilter: EstadoAudiencia?,
    searchQuery: String,
): List<Audiencia> {
    val q = searchQuery.normalizeForSearch()
    return items.filter { item ->
        val matchesTipo = tipoFilter == null || item.tipoAudiencia.equals(tipoFilter, ignoreCase = true)
        val matchesEstado = estadoFilter == null || item.estadoEnum() == estadoFilter
        val matchesQuery = q.isEmpty() || listOf(
            item.nuc,
            item.numeroCausa,
            item.tipoAudiencia,
            item.salaOJuzgado,
            item.juez,
            item.ministerioPublico,
            item.asesorJuridico,
            item.victima,
            item.defensor,
            item.imputado,
            item.tipoDelito,
            item.clasificacionJuridica,
            item.resultado
        ).any { it.normalizeForSearch().contains(q) }
        matchesTipo && matchesEstado && matchesQuery
    }
}
