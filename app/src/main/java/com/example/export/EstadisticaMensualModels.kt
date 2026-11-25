package com.example.export

import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.util.FechaHelper
import com.example.util.MesKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TipoAudienciaStats(
    val tipoAudiencia: String,
    val total: Int,
    val programadas: Int,
    val celebradas: Int,
    val diferidas: Int,
    val suspendidas: Int,
    val canceladas: Int,
    val conProximaFecha: Int,
    val autoAgendadas: Int,
    val porcentaje: Double,
)

data class CategoriaCount(
    val nombre: String,
    val cantidad: Int,
    val porcentaje: Double,
)

data class DiaCount(
    val dayOfMonth: Int,
    val fechaLabel: String,
    val cantidad: Int,
)

data class EstadisticaMensualReport(
    val mes: MesKey,
    val mesLabel: String,
    val periodoLabel: String,
    val generatedAtMillis: Long,
    val isAvailableOfficial: Boolean,
    val availableFromLabel: String,
    val audiencias: List<Audiencia>,
    val totalAudiencias: Int,
    val programadas: Int,
    val celebradas: Int,
    val diferidas: Int,
    val suspendidas: Int,
    val canceladas: Int,
    val autoAgendadas: Int,
    val conResultado: Int,
    val conProximaFecha: Int,
    val porTipoAudiencia: List<TipoAudienciaStats>,
    val porEstado: List<CategoriaCount>,
    val porDelito: List<CategoriaCount>,
    val porClasificacionJuridica: List<CategoriaCount>,
    val porMinisterioPublico: List<CategoriaCount>,
    val porJuez: List<CategoriaCount>,
    val porDia: List<DiaCount>,
)

object EstadisticaMensualCalculator {
    private val localeEs = Locale("es", "MX")

    fun currentMesKey(nowMillis: Long = System.currentTimeMillis()): MesKey {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return MesKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun previousClosedMonth(nowMillis: Long = System.currentTimeMillis()): MesKey {
        return currentMesKey(nowMillis).plusMonths(-1)
    }

    fun isFirstDayOfMonth(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return cal.get(Calendar.DAY_OF_MONTH) == 1
    }

    fun monthStartMillis(mes: MesKey): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, mes.year)
            set(Calendar.MONTH, mes.month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun monthEndMillis(mes: MesKey): Long {
        return monthStartMillis(mes.plusMonths(1)) - 1L
    }

    fun availabilityMillis(targetMes: MesKey): Long {
        return monthStartMillis(targetMes.plusMonths(1))
    }

    fun isMonthAvailableForOfficialReport(
        targetMes: MesKey,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        return nowMillis >= availabilityMillis(targetMes)
    }

    fun availabilityDateLabel(targetMes: MesKey): String {
        val availMes = targetMes.plusMonths(1)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, availMes.year)
            set(Calendar.MONTH, availMes.month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("d 'de' MMMM 'de' yyyy", localeEs).format(cal.time)
    }

    fun daysUntilAvailable(
        targetMes: MesKey,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        val diff = availabilityMillis(targetMes) - nowMillis
        if (diff <= 0L) return 0
        val dayMs = 24L * 60L * 60L * 1000L
        return ((diff + dayMs - 1L) / dayMs).toInt().coerceAtLeast(1)
    }

    fun selectableMonths(
        audiencias: List<Audiencia>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<MesKey> {
        val current = currentMesKey(nowMillis)
        val recentRange = (0..11).map { offset -> current.plusMonths(-offset) }
        val dataMonths = audiencias.flatMap { aud ->
            buildList {
                if (aud.fechaHoraMillis > 0L) {
                    val dia = FechaHelper.diaKeyFromMillis(aud.fechaHoraMillis)
                    add(MesKey(dia.year, dia.month))
                }
                aud.createdAt?.toDate()?.time?.let { createdMs ->
                    val dia = FechaHelper.diaKeyFromMillis(createdMs)
                    add(MesKey(dia.year, dia.month))
                }
            }
        }
        return (recentRange + dataMonths)
            .distinctBy { it.year * 12 + it.month }
            .filter { (it.year * 12 + it.month) <= (current.year * 12 + current.month) }
            .sortedByDescending { it.year * 12 + it.month }
    }

    fun buildReport(
        targetMes: MesKey,
        allAudiencias: List<Audiencia>,
        nowMillis: Long = System.currentTimeMillis(),
    ): EstadisticaMensualReport {
        val startMs = monthStartMillis(targetMes)
        val endMs = monthEndMillis(targetMes)

        val monthAudiencias = allAudiencias
            .filter { aud ->
                val scheduledInMonth = aud.fechaHoraMillis in startMs..endMs
                val uploadedMs = aud.createdAt?.toDate()?.time
                val uploadedInMonth = uploadedMs != null && uploadedMs in startMs..endMs
                scheduledInMonth || uploadedInMonth
            }
            .distinctBy { it.id.ifBlank { "${it.fechaHoraMillis}_${it.tipoAudiencia}_${it.imputado}" } }
            .sortedBy { it.fechaHoraMillis }

        val total = monthAudiencias.size
        fun pct(count: Int): Double = if (total > 0) count.toDouble() / total.toDouble() else 0.0

        val porTipo = monthAudiencias
            .groupBy { it.tipoAudiencia.trim().ifBlank { "Sin tipo especificado" } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Audiencia>>> { it.value.size }.thenBy { it.key })
            .map { (tipo, list) ->
                TipoAudienciaStats(
                    tipoAudiencia = tipo,
                    total = list.size,
                    programadas = list.count { it.estadoEnum() == EstadoAudiencia.PROGRAMADA },
                    celebradas = list.count { it.estadoEnum() == EstadoAudiencia.CELEBRADA },
                    diferidas = list.count { it.estadoEnum() == EstadoAudiencia.DIFERIDA },
                    suspendidas = list.count { it.estadoEnum() == EstadoAudiencia.SUSPENDIDA },
                    canceladas = list.count { it.estadoEnum() == EstadoAudiencia.CANCELADA },
                    conProximaFecha = list.count { it.tieneProximaFecha && it.proximaFechaMillis != null },
                    autoAgendadas = list.count { it.audienciaOrigenId != null },
                    porcentaje = pct(list.size)
                )
            }

        val porEstado = EstadoAudiencia.entries.map { estado ->
            val count = monthAudiencias.count { it.estadoEnum() == estado }
            CategoriaCount(estado.label, count, pct(count))
        }

        val porDelito = groupCounts(
            items = monthAudiencias.map { it.tipoDelito.trim().ifBlank { "Sin especificar" } },
            total = total
        )

        val porClasificacion = groupCounts(
            items = monthAudiencias.map { it.clasificacionJuridica.trim().ifBlank { "Sin especificar" } },
            total = total
        )

        val porMinisterioPublico = groupCounts(
            items = monthAudiencias.map { it.ministerioPublico.trim().ifBlank { "Sin asignar" } },
            total = total
        )

        val porJuez = groupCounts(
            items = monthAudiencias.map { it.juez.trim().ifBlank { "Sin asignar" } },
            total = total
        )

        val daysInMonth = targetMes.daysInMonth()
        val countsByDay = monthAudiencias
            .mapNotNull { aud ->
                val refMs = if (aud.fechaHoraMillis in startMs..endMs) {
                    aud.fechaHoraMillis
                } else {
                    aud.createdAt?.toDate()?.time
                }
                refMs?.let { FechaHelper.diaKeyFromMillis(it) }
            }
            .filter { it.year == targetMes.year && it.month == targetMes.month }
            .groupingBy { it.dayOfMonth }
            .eachCount()

        val monthShortFormat = SimpleDateFormat("dd/MM", localeEs)
        val porDia = (1..daysInMonth).map { day ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, targetMes.year)
                set(Calendar.MONTH, targetMes.month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            DiaCount(
                dayOfMonth = day,
                fechaLabel = monthShortFormat.format(Date(cal.timeInMillis)),
                cantidad = countsByDay[day] ?: 0
            )
        }

        val startFmt = SimpleDateFormat("d 'de' MMMM", localeEs).format(Date(startMs))
        val endFmt = SimpleDateFormat("d 'de' MMMM 'de' yyyy", localeEs).format(Date(endMs))

        return EstadisticaMensualReport(
            mes = targetMes,
            mesLabel = FechaHelper.formatMonthYear(targetMes),
            periodoLabel = "Del $startFmt al $endFmt",
            generatedAtMillis = nowMillis,
            isAvailableOfficial = isMonthAvailableForOfficialReport(targetMes, nowMillis),
            availableFromLabel = availabilityDateLabel(targetMes),
            audiencias = monthAudiencias,
            totalAudiencias = total,
            programadas = monthAudiencias.count { it.estadoEnum() == EstadoAudiencia.PROGRAMADA },
            celebradas = monthAudiencias.count { it.estadoEnum() == EstadoAudiencia.CELEBRADA },
            diferidas = monthAudiencias.count { it.estadoEnum() == EstadoAudiencia.DIFERIDA },
            suspendidas = monthAudiencias.count { it.estadoEnum() == EstadoAudiencia.SUSPENDIDA },
            canceladas = monthAudiencias.count { it.estadoEnum() == EstadoAudiencia.CANCELADA },
            autoAgendadas = monthAudiencias.count { it.audienciaOrigenId != null },
            conResultado = monthAudiencias.count { it.resultado.isNotBlank() },
            conProximaFecha = monthAudiencias.count { it.tieneProximaFecha && it.proximaFechaMillis != null },
            porTipoAudiencia = porTipo,
            porEstado = porEstado,
            porDelito = porDelito,
            porClasificacionJuridica = porClasificacion,
            porMinisterioPublico = porMinisterioPublico,
            porJuez = porJuez,
            porDia = porDia
        )
    }

    private fun groupCounts(items: List<String>, total: Int): List<CategoriaCount> {
        return items
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { (name, count) ->
                CategoriaCount(
                    nombre = name,
                    cantidad = count,
                    porcentaje = if (total > 0) count.toDouble() / total.toDouble() else 0.0
                )
            }
    }
}
