package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DiaKey(
    val year: Int,
    val month: Int, // 0-indexed (Calendar.JANUARY = 0)
    val dayOfMonth: Int,
) : Comparable<DiaKey> {
    override fun compareTo(other: DiaKey): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return dayOfMonth.compareTo(other.dayOfMonth)
    }

    fun toStartOfDayMillis(hour: Int = 9, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

data class MesKey(
    val year: Int,
    val month: Int, // 0-indexed
) {
    fun plusMonths(delta: Int): MesKey {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        return MesKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun daysInMonth(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Returns 0 = Monday .. 6 = Sunday for the 1st of the month. */
    fun firstDayMondayOffset(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }
}

object FechaHelper {
    private val localeEs = Locale("es", "MX")

    fun todayDiaKey(): DiaKey = diaKeyFromMillis(System.currentTimeMillis())

    fun currentMesKey(): MesKey {
        val cal = Calendar.getInstance()
        return MesKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun diaKeyFromMillis(millis: Long): DiaKey {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return DiaKey(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun mesKeyFromDiaKey(dia: DiaKey): MesKey = MesKey(dia.year, dia.month)

    fun hourMinuteFromMillis(millis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun combineDateUtcPickerAndTime(utcDateMillis: Long, hour: Int, minute: Int): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcDateMillis
        }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }

    fun localMillisToUtcPickerMillis(localMillis: Long): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
        }
        return utcCal.timeInMillis
    }

    fun updateTimeOnMillis(baseMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatMonthYear(mes: MesKey): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, mes.year)
            set(Calendar.MONTH, mes.month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val raw = SimpleDateFormat("MMMM yyyy", localeEs).format(cal.time)
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeEs) else it.toString() }
    }

    fun formatDayHeader(dia: DiaKey): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dia.year)
            set(Calendar.MONTH, dia.month)
            set(Calendar.DAY_OF_MONTH, dia.dayOfMonth)
        }
        val raw = SimpleDateFormat("EEEE d 'de' MMMM", localeEs).format(cal.time)
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeEs) else it.toString() }
    }

    fun formatShortDate(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy", localeEs).format(Date(millis))
    }

    fun formatFullDateTime(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy · HH:mm 'hrs'", localeEs).format(Date(millis))
    }

    fun formatTime(millis: Long): String {
        return SimpleDateFormat("HH:mm", localeEs).format(Date(millis))
    }

    fun isWithinNext24Hours(
        fechaHoraMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val windowEnd = nowMillis + 24L * 60L * 60L * 1000L
        return fechaHoraMillis in nowMillis..windowEnd
    }

    fun formatTimeRemaining(
        fechaHoraMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val diff = (fechaHoraMillis - nowMillis).coerceAtLeast(0L)
        val totalMinutes = diff / (60L * 1000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours == 0L && minutes <= 1L -> "En menos de 1 min"
            hours == 0L -> "En $minutes min"
            minutes == 0L -> "En ${hours} h"
            else -> "En ${hours} h ${minutes} min"
        }
    }
}
