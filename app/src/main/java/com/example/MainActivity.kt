package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.data.TipoAudienciaPreset
import com.example.data.UiState
import com.example.notifications.AudienciaNotificationHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.AudienciaFormSheet
import com.example.ui.components.CompartirAgendaSheet
import com.example.ui.components.ConfiguracionSheet
import com.example.ui.components.OfflineSyncStatusBanner
import com.example.ui.components.ResultadoAudienciaSheet
import com.example.ui.screens.CalendarioScreen
import com.example.ui.screens.DetalleAudienciaScreen
import com.example.ui.screens.DirectorioScreen
import com.example.ui.screens.ResumenScreen
import com.example.ui.theme.AgendaPenalTheme
import com.example.ui.viewmodel.AudienciasViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private const val ROUTE_CALENDAR = "calendario"
private const val ROUTE_DIRECTORY = "directorio"
private const val ROUTE_SUMMARY = "resumen"
private const val ROUTE_DETAIL = "detalle/{audienciaId}"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val APP_TABS = listOf(
    NavTab(
        route = ROUTE_CALENDAR,
        labelRes = R.string.nav_calendar,
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_tab_calendario"
    ),
    NavTab(
        route = ROUTE_DIRECTORY,
        labelRes = R.string.nav_directory,
        selectedIcon = Icons.Rounded.FolderCopy,
        unselectedIcon = Icons.Outlined.FolderCopy,
        testTag = "nav_tab_audiencias"
    ),
    NavTab(
        route = ROUTE_SUMMARY,
        labelRes = R.string.nav_summary,
        selectedIcon = Icons.Rounded.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_tab_resumen"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            AgendaPenalTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            AgendaAppShell(currentUser = user)
        }
    }
}

@Composable
fun AgendaAppShell(currentUser: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AudienciasViewModel = viewModel(
        key = "${currentUser.uid}_${currentUser.email.orEmpty()}",
        factory = AudienciasViewModel.factory(
            currentUserId = currentUser.uid,
            currentUserEmail = currentUser.email.orEmpty(),
            currentUserDisplayName = currentUser.displayName.orEmpty()
        )
    )
    val audienciasState by viewModel.audienciasState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val ownAgenda by viewModel.ownAgenda.collectAsStateWithLifecycle()
    val sharedAgendasWithMe by viewModel.sharedAgendasWithMe.collectAsStateWithLifecycle()
    val activeAgendaInfo by viewModel.activeAgendaInfo.collectAsStateWithLifecycle()
    val appAppearance by viewModel.appAppearance.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val configSyncState by viewModel.configSyncState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelTab = currentRoute in listOf(ROUTE_CALENDAR, ROUTE_DIRECTORY, ROUTE_SUMMARY)

    val snackbarHostState = remember { SnackbarHostState() }
    var hasNotificationPermission by remember {
        mutableStateOf(AudienciaNotificationHelper.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        val currentItems = (audienciasState as? UiState.Success)?.data.orEmpty()
        val upcoming = AudienciaNotificationHelper.filterUpcoming24h(currentItems)
        if (granted && upcoming.isNotEmpty()) {
            AudienciaNotificationHelper.notifyUpcoming24hIfNeeded(context, upcoming, force = true)
        }
    }

    LaunchedEffect(Unit) {
        AudienciaNotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(audienciasState, hasNotificationPermission) {
        val items = (audienciasState as? UiState.Success)?.data ?: return@LaunchedEffect
        val upcoming24h = AudienciaNotificationHelper.filterUpcoming24h(items)
        if (hasNotificationPermission && upcoming24h.isNotEmpty()) {
            AudienciaNotificationHelper.notifyUpcoming24hIfNeeded(context, upcoming24h)
        }
        AudienciaNotificationHelper.notifyMonthlyExcelAvailableIfFirstDay(context, items)
        AudienciaNotificationHelper.scheduleBackgroundReminders(context, items)
    }

    LaunchedEffect(filters.feedbackMessage) {
        val msg = filters.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    var formTarget by remember { mutableStateOf<Audiencia?>(null) }
    var resultadoTarget by remember { mutableStateOf<Audiencia?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }

    val openNewHearing: () -> Unit = {
        formTarget = Audiencia(
            userId = currentUser.uid,
            tipoAudiencia = TipoAudienciaPreset.CONTROL_DETENCION.label,
            fechaHoraMillis = filters.selectedDia.toStartOfDayMillis(hour = 10, minute = 0),
            estado = EstadoAudiencia.PROGRAMADA.code
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevelTab && !useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        APP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (isTopLevelTab && currentRoute != ROUTE_SUMMARY && activeAgendaInfo.isOwnerMode) {
                    ExtendedFloatingActionButton(
                        onClick = openNewHearing,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.new_hearing_fab)) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("nueva_audiencia_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelTab && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        APP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    OfflineSyncStatusBanner(
                        syncStatus = syncStatus,
                        onSyncNow = { viewModel.syncNow(showNoPendingMessage = true) }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (val state = audienciasState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator()
                                        Text(
                                            text = "Cargando agenda local...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "No se pudo consultar tu agenda",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            if (!activeAgendaInfo.isOwnerMode) {
                                                viewModel.returnToOwnAgenda()
                                            } else {
                                                viewModel.jumpToToday()
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (!activeAgendaInfo.isOwnerMode) "Volver a mi agenda"
                                            else "Reintentar"
                                        )
                                    }
                                }
                            }
                        }

                        is UiState.Success -> {
                            val allAudiencias = state.data
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_CALENDAR
                            ) {
                                composable(ROUTE_CALENDAR) {
                                    CalendarioScreen(
                                        allAudiencias = allAudiencias,
                                        filters = filters,
                                        activeAgendaInfo = activeAgendaInfo,
                                        appAppearance = appAppearance,
                                        ownAgenda = ownAgenda,
                                        sharedAgendasWithMe = sharedAgendasWithMe,
                                        currentUserId = currentUser.uid,
                                        hasNotificationPermission = hasNotificationPermission,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        onSendReminderNow = { upcoming ->
                                            if (upcoming.isNotEmpty()) {
                                                val sent = AudienciaNotificationHelper.notifyUpcoming24hIfNeeded(
                                                    context = context,
                                                    upcoming = upcoming,
                                                    force = true
                                                )
                                                if (sent) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Recordatorio local enviado para tus audiencias próximas.")
                                                    }
                                                }
                                            }
                                        },
                                        onSelectAgenda = { ownerUid ->
                                            viewModel.selectAgenda(ownerUid)
                                        },
                                        onOpenShareSheet = { showShareSheet = true },
                                        onOpenSettings = { showConfigSheet = true },
                                        onSelectDia = viewModel::selectDia,
                                        onPreviousMonth = viewModel::previousMonth,
                                        onNextMonth = viewModel::nextMonth,
                                        onJumpToToday = viewModel::jumpToToday,
                                        onSelectTipoFilter = viewModel::setTipoFilter,
                                        onCalendarSearchChange = viewModel::setCalendarSearchQuery,
                                        onOpenDetail = { aud ->
                                            navController.navigate("detalle/${aud.id}")
                                        },
                                        onOpenResultado = { aud ->
                                            if (activeAgendaInfo.isOwnerMode) resultadoTarget = aud
                                        },
                                        onOpenEdit = { aud ->
                                            if (activeAgendaInfo.isOwnerMode) formTarget = aud
                                        }
                                    )
                                }

                                composable(ROUTE_DIRECTORY) {
                                    DirectorioScreen(
                                        allAudiencias = allAudiencias,
                                        filters = filters,
                                        activeAgendaInfo = activeAgendaInfo,
                                        appAppearance = appAppearance,
                                        ownAgenda = ownAgenda,
                                        sharedAgendasWithMe = sharedAgendasWithMe,
                                        currentUserId = currentUser.uid,
                                        onSelectAgenda = { ownerUid ->
                                            viewModel.selectAgenda(ownerUid)
                                        },
                                        onOpenShareSheet = { showShareSheet = true },
                                        onOpenSettings = { showConfigSheet = true },
                                        onSearchChange = viewModel::setSearchQuery,
                                        onSelectEstado = viewModel::setEstadoFilter,
                                        onSelectTipo = viewModel::setTipoFilter,
                                        onOpenDetail = { aud ->
                                            navController.navigate("detalle/${aud.id}")
                                        },
                                        onOpenResultado = { aud ->
                                            if (activeAgendaInfo.isOwnerMode) resultadoTarget = aud
                                        },
                                        onOpenEdit = { aud ->
                                            if (activeAgendaInfo.isOwnerMode) formTarget = aud
                                        }
                                    )
                                }

                                composable(ROUTE_SUMMARY) {
                                    ResumenScreen(
                                        allAudiencias = allAudiencias,
                                        currentUser = currentUser,
                                        activeAgendaInfo = activeAgendaInfo,
                                        appAppearance = appAppearance,
                                        ownAgenda = ownAgenda,
                                        sharedAgendasWithMe = sharedAgendasWithMe,
                                        onOpenShareSheet = { showShareSheet = true },
                                        onOpenSettings = { showConfigSheet = true },
                                        onSignOut = {
                                            signOutUser(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    )
                                }

                                composable(
                                    route = ROUTE_DETAIL,
                                    arguments = listOf(navArgument("audienciaId") { type = NavType.StringType })
                                ) { entry ->
                                    val audId = entry.arguments?.getString("audienciaId").orEmpty()
                                    val audiencia = allAudiencias.firstOrNull { it.id == audId }
                                    DetalleAudienciaScreen(
                                        audiencia = audiencia,
                                        allAudiencias = allAudiencias,
                                        isSaving = filters.isSaving,
                                        isReadOnly = !activeAgendaInfo.isOwnerMode,
                                        sharedOwnerEmail = activeAgendaInfo.ownerEmail,
                                        onBack = { navController.popBackStack() },
                                        onOpenFullEdit = {
                                            if (activeAgendaInfo.isOwnerMode) formTarget = it
                                        },
                                        onOpenResultado = {
                                            if (activeAgendaInfo.isOwnerMode) resultadoTarget = it
                                        },
                                        onQuickUpdate = { updated ->
                                            if (activeAgendaInfo.isOwnerMode) viewModel.saveAudiencia(updated)
                                        },
                                        onAutoScheduleProximaFecha = { aud, nextMillis, nextTipo ->
                                            if (activeAgendaInfo.isOwnerMode) {
                                                viewModel.autoScheduleProximaFecha(
                                                    audiencia = aud,
                                                    proximaFechaMillis = nextMillis,
                                                    proximoTipoAudiencia = nextTipo
                                                )
                                            }
                                        },
                                        onDelete = { id ->
                                            if (activeAgendaInfo.isOwnerMode) {
                                                viewModel.deleteAudiencia(id) { ok ->
                                                    if (ok) navController.popBackStack()
                                                }
                                            }
                                        },
                                        onNavigateToAudiencia = { targetId ->
                                            navController.navigate("detalle/$targetId")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (activeAgendaInfo.isOwnerMode) {
        formTarget?.let { target ->
            AudienciaFormSheet(
                initialAudiencia = target,
                isSaving = filters.isSaving,
                onDismiss = { formTarget = null },
                onAutoScheduleProximaFecha = { draft, onDone ->
                    val nextMillis = draft.proximaFechaMillis ?: 0L
                    if (draft.id.isNotBlank() && nextMillis > 0L) {
                        viewModel.autoScheduleProximaFecha(
                            audiencia = draft,
                            proximaFechaMillis = nextMillis,
                            proximoTipoAudiencia = draft.proximoTipoAudiencia,
                            resultadoTexto = draft.resultado,
                            nuevoEstado = draft.estadoEnum(),
                            onComplete = onDone
                        )
                    } else {
                        onDone(null)
                    }
                },
                onSave = { updated ->
                    viewModel.saveAudiencia(updated) { ok ->
                        if (ok) formTarget = null
                    }
                }
            )
        }

        resultadoTarget?.let { target ->
            ResultadoAudienciaSheet(
                audiencia = target,
                isSaving = filters.isSaving,
                onDismiss = { resultadoTarget = null },
                onAutoScheduleProximaFecha = { nextMillis, nextTipo, resText, est, onDone ->
                    viewModel.autoScheduleProximaFecha(
                        audiencia = target,
                        proximaFechaMillis = nextMillis,
                        proximoTipoAudiencia = nextTipo,
                        resultadoTexto = resText,
                        nuevoEstado = est,
                        onComplete = onDone
                    )
                },
                onGuardarResultado = { nuevoEstado, resultadoTexto, tieneProxima, proximaFechaMillis, proximoTipo ->
                    viewModel.registrarResultado(
                        audiencia = target,
                        nuevoEstado = nuevoEstado,
                        resultadoTexto = resultadoTexto,
                        tieneProximaFecha = tieneProxima,
                        proximaFechaMillis = proximaFechaMillis,
                        proximoTipoAudiencia = proximoTipo
                    ) { ok ->
                        if (ok) resultadoTarget = null
                    }
                }
            )
        }
    }

    if (showShareSheet) {
        CompartirAgendaSheet(
            currentUserEmail = currentUser.email.orEmpty(),
            currentUserId = currentUser.uid,
            ownAgenda = ownAgenda,
            sharedAgendasWithMe = sharedAgendasWithMe,
            activeOwnerUid = activeAgendaInfo.ownerUid,
            isSaving = filters.isSaving,
            onDismiss = { showShareSheet = false },
            onAddGmail = { email, onDone ->
                viewModel.addSharedGmail(email, onDone)
            },
            onRemoveGmail = { email ->
                viewModel.removeSharedGmail(email)
            },
            onSelectAgenda = { ownerUid ->
                viewModel.selectAgenda(ownerUid)
            }
        )
    }

    if (activeAgendaInfo.isOwnerMode && showConfigSheet) {
        ConfiguracionSheet(
            currentAppearance = appAppearance,
            isSaving = filters.isSaving,
            configSyncState = configSyncState,
            isOnline = syncStatus.isOnline,
            onDismiss = { showConfigSheet = false },
            onAutoSync = { ownerName, appTitle, calendarTitle, directoryTitle, summaryTitle ->
                viewModel.autoSyncConfiguration(
                    ownerName = ownerName,
                    appTitle = appTitle,
                    calendarTitle = calendarTitle,
                    directoryTitle = directoryTitle,
                    summaryTitle = summaryTitle
                )
            },
            onSave = { ownerName, appTitle, calendarTitle, directoryTitle, summaryTitle ->
                viewModel.saveConfiguration(
                    ownerName = ownerName,
                    appTitle = appTitle,
                    calendarTitle = calendarTitle,
                    directoryTitle = directoryTitle,
                    summaryTitle = summaryTitle
                ) { ok ->
                    if (ok) showConfigSheet = false
                }
            }
        )
    }
}
