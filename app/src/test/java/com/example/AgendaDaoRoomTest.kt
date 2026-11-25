package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.data.local.AgendaDao
import com.example.data.local.AgendaDatabase
import com.example.data.local.SyncOpType
import com.example.data.local.SyncStatus
import com.example.data.local.toRoomEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgendaDaoRoomTest {

    private lateinit var db: AgendaDatabase
    private lateinit var dao: AgendaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AgendaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.agendaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `offline create followed by offline edit coalesces to single CREATE op`() = runBlocking {
        val initial = Audiencia(
            id = "loc_123456",
            userId = "user_1",
            tipoAudiencia = "Control de detención",
            ministerioPublico = "Lic. Ramos",
            fechaHoraMillis = 1_770_000_000_000L,
            estado = EstadoAudiencia.PROGRAMADA.code
        ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.PENDING_UPSERT)

        dao.queueLocalUpsert(initial, SyncOpType.CREATE, nowMillis = 100L)

        val edited = initial.copy(
            ministerioPublico = "Lic. Morales",
            resultado = "Vinculado"
        )
        dao.queueLocalUpsert(edited, SyncOpType.UPDATE, nowMillis = 200L)

        val ops = dao.getPendingOpsForOwner("user_1")
        assertEquals(1, ops.size)
        assertEquals(SyncOpType.CREATE, ops.first().opType)
        assertEquals("Lic. Morales", dao.getAudienciaById("loc_123456")?.ministerioPublico)
    }

    @Test
    fun `remote sync does not overwrite pending offline edits or deletes`() = runBlocking {
        val pendingEdit = Audiencia(
            id = "aud_remote_1",
            userId = "user_1",
            tipoAudiencia = "Juicio oral",
            ministerioPublico = "Local Offline MP",
            fechaHoraMillis = 1_770_000_000_000L
        ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.PENDING_UPSERT)

        dao.queueLocalUpsert(pendingEdit, SyncOpType.UPDATE, nowMillis = 100L)

        val pendingDelete = Audiencia(
            id = "aud_remote_delete",
            userId = "user_1",
            tipoAudiencia = "Inicial",
            fechaHoraMillis = 1_770_050_000_000L
        ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.SYNCED)
        dao.upsertAudiencia(pendingDelete)
        dao.queueLocalDelete("aud_remote_delete", "user_1", nowMillis = 150L)

        val staleRemoteRows = listOf(
            Audiencia(
                id = "aud_remote_1",
                userId = "user_1",
                tipoAudiencia = "Juicio oral",
                ministerioPublico = "Stale Cloud MP",
                fechaHoraMillis = 1_770_000_000_000L
            ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.SYNCED),
            Audiencia(
                id = "aud_remote_delete",
                userId = "user_1",
                tipoAudiencia = "Inicial",
                fechaHoraMillis = 1_770_050_000_000L
            ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.SYNCED),
            Audiencia(
                id = "aud_remote_fresh",
                userId = "user_1",
                tipoAudiencia = "Intermedia",
                fechaHoraMillis = 1_770_090_000_000L
            ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.SYNCED)
        )

        dao.replaceSyncedFromRemote("user_1", staleRemoteRows)

        val visible = dao.observeVisibleByOwner("user_1").first()
        assertEquals(listOf("aud_remote_1", "aud_remote_fresh"), visible.map { it.id })
        assertEquals("Local Offline MP", visible.first { it.id == "aud_remote_1" }.ministerioPublico)
    }

    @Test
    fun `offline delete of local-only row removes completely while synced row queues DELETE`() = runBlocking {
        val localOnly = Audiencia(
            id = "loc_only",
            userId = "user_1",
            tipoAudiencia = "Inicial",
            fechaHoraMillis = 1_770_000_000_000L
        ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.PENDING_UPSERT)
        dao.queueLocalUpsert(localOnly, SyncOpType.CREATE, nowMillis = 100L)

        dao.queueLocalDelete("loc_only", "user_1", nowMillis = 200L)
        assertNull(dao.getAudienciaById("loc_only"))
        assertNull(dao.getPendingOp("loc_only"))

        val synced = Audiencia(
            id = "cloud_doc_1",
            userId = "user_1",
            tipoAudiencia = "Intermedia",
            fechaHoraMillis = 1_770_000_000_000L
        ).toRoomEntity(ownerUid = "user_1", syncStatus = SyncStatus.SYNCED)
        dao.upsertAudiencia(synced)

        dao.queueLocalDelete("cloud_doc_1", "user_1", nowMillis = 300L)
        val op = dao.getPendingOp("cloud_doc_1")
        assertNotNull(op)
        assertEquals(SyncOpType.DELETE, op?.opType)
        assertEquals(0, dao.observeVisibleByOwner("user_1").first().size)
        assertEquals(1, dao.observePendingCount("user_1").first())

        dao.markDeleteSynced("cloud_doc_1")
        assertNull(dao.getAudienciaById("cloud_doc_1"))
        assertEquals(0, dao.observePendingCount("user_1").first())
    }

    @Test
    fun `pending own agenda configuration preserves custom titles and username until synced`() = runBlocking {
        val localConfig = com.example.data.AgendaCompartida(
            id = "user_1",
            ownerUid = "user_1",
            ownerEmail = "titular@gmail.com",
            ownerName = "Lic. Miguel Jacobo",
            appTitle = "Mi App Penal",
            calendarTitle = "Calendario Personalizado",
            directoryTitle = "Audiencias Activas",
            summaryTitle = "Métricas"
        ).toRoomEntity(viewerUid = "user_1", isOwnAgenda = true, pendingSync = true)

        dao.upsertAgenda(localConfig)
        assertEquals(1, dao.observePendingOwnAgendaCount("user_1").first())

        val staleRemote = com.example.data.AgendaCompartida(
            id = "user_1",
            ownerUid = "user_1",
            ownerEmail = "titular@gmail.com",
            ownerName = "Old Name",
            appTitle = "",
            calendarTitle = "",
            directoryTitle = "",
            summaryTitle = ""
        ).toRoomEntity(viewerUid = "user_1", isOwnAgenda = true, pendingSync = false)

        dao.replaceOwnAgenda("user_1", staleRemote)
        val stored = dao.getOwnAgenda("user_1")
        assertNotNull(stored)
        assertEquals("Lic. Miguel Jacobo", stored?.ownerName)
        assertEquals("Mi App Penal", stored?.appTitle)
        assertEquals("Calendario Personalizado", stored?.calendarTitle)
        assertEquals(true, stored?.pendingSync)
    }
}
