package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Audiencia
import com.example.data.EstadoAudiencia
import com.example.util.FechaHelper

object AudienciaNotificationHelper {
    const val CHANNEL_ID = "audiencias_24h_channel"
    private const val SUMMARY_NOTIFICATION_ID = 2401
    private const val PREFS_NAME = "agenda_penal_reminders"
    private const val KEY_LAST_SIGNATURE = "last_notified_24h_signature"
    private const val KEY_LAST_NOTIFIED_AT = "last_notified_24h_at"
    private const val REMINDER_COOLDOWN_MS = 60L * 60L * 1000L // 1 hour deduplication

    const val EXTRA_TITLE = "extra_reminder_title"
    const val EXTRA_BODY = "extra_reminder_body"
    const val EXTRA_NOTIFICATION_ID = "extra_reminder_notif_id"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de Audiencias (24h)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones locales para audiencias programadas en las próximas 24 horas."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun filterUpcoming24h(
        audiencias: List<Audiencia>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Audiencia> {
        return audiencias
            .filter {
                it.estadoEnum() == EstadoAudiencia.PROGRAMADA &&
                    FechaHelper.isWithinNext24Hours(it.fechaHoraMillis, nowMillis)
            }
            .sortedBy { it.fechaHoraMillis }
    }

    fun notifyUpcoming24hIfNeeded(
        context: Context,
        upcoming: List<Audiencia>,
        force: Boolean = false,
    ): Boolean {
        if (upcoming.isEmpty() || !hasNotificationPermission(context)) return false

        val signature = upcoming.joinToString("|") { "${it.id}:${it.fechaHoraMillis}" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!force) {
            val lastSig = prefs.getString(KEY_LAST_SIGNATURE, null)
            val lastAt = prefs.getLong(KEY_LAST_NOTIFIED_AT, 0L)
            val elapsed = System.currentTimeMillis() - lastAt
            if (signature == lastSig && elapsed < REMINDER_COOLDOWN_MS) {
                return false
            }
        }

        sendUpcomingNotification(context, upcoming)
        prefs.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putLong(KEY_LAST_NOTIFIED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun notifyMonthlyExcelAvailableIfFirstDay(
        context: Context,
        audiencias: List<Audiencia>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        if (!com.example.export.EstadisticaMensualCalculator.isFirstDayOfMonth(nowMillis)) return false

        val currentMes = com.example.export.EstadisticaMensualCalculator.currentMesKey(nowMillis)
        val monthKey = "${currentMes.year}_${currentMes.month}"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString("monthly_excel_notified_key", null) == monthKey) {
            return false
        }

        val prevMes = com.example.export.EstadisticaMensualCalculator.previousClosedMonth(nowMillis)
        val report = com.example.export.EstadisticaMensualCalculator.buildReport(prevMes, audiencias, nowMillis)
        showSingleNotification(
            context = context,
            notificationId = 2499,
            title = "Estadística mensual lista: ${report.mesLabel}",
            body = "Ya puedes descargar el archivo Excel con ${report.totalAudiencias} audiencias y gráficas del mes anterior desde Resumen de Audiencias."
        )
        prefs.edit().putString("monthly_excel_notified_key", monthKey).apply()
        return true
    }

    fun sendUpcomingNotification(
        context: Context,
        upcoming: List<Audiencia>,
    ) {
        if (upcoming.isEmpty() || !hasNotificationPermission(context)) return
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val first = upcoming.first()
        val remaining = FechaHelper.formatTimeRemaining(first.fechaHoraMillis)
        val title = if (upcoming.size == 1) {
            "Audiencia próxima ($remaining): ${first.tipoAudiencia}"
        } else {
            "${upcoming.size} audiencias en las próximas 24 horas"
        }

        val summaryLine = buildSummaryLine(first)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_audiencia)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (upcoming.size == 1) {
            val detailText = buildString {
                append("${FechaHelper.formatFullDateTime(first.fechaHoraMillis)} ($remaining)")
                if (first.tipoDelito.isNotBlank()) append("\nDelito: ${first.tipoDelito}")
                if (first.salaOJuzgado.isNotBlank()) append(" · ${first.salaOJuzgado}")
                if (first.imputado.isNotBlank()) append("\nImputado: ${first.imputado}")
            }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
        } else {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
            upcoming.take(5).forEach { aud ->
                inbox.addLine("${FechaHelper.formatTime(aud.fechaHoraMillis)} · ${aud.tipoAudiencia} (${aud.tipoDelito.ifBlank { aud.numeroCausa.ifBlank { "Pendiente" } }})")
            }
            if (upcoming.size > 5) {
                inbox.setSummaryText("+${upcoming.size - 5} audiencias más")
            }
            builder.setStyle(inbox)
        }

        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently; fail safely.
        }
    }

    fun showSingleNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_audiencia)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission missing; ignore safely.
        }
    }

    fun scheduleBackgroundReminders(
        context: Context,
        audiencias: List<Audiencia>,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val futurePending = audiencias
            .filter { it.estadoEnum() == EstadoAudiencia.PROGRAMADA && it.fechaHoraMillis > now }
            .sortedBy { it.fechaHoraMillis }
            .take(8)

        futurePending.forEach { aud ->
            val oneDayBefore = aud.fechaHoraMillis - 24L * 60L * 60L * 1000L
            val twoHoursBefore = aud.fechaHoraMillis - 2L * 60L * 60L * 1000L
            val triggerAt = when {
                oneDayBefore > now + 60_000L -> oneDayBefore
                twoHoursBefore > now + 60_000L -> twoHoursBefore
                else -> null
            } ?: return@forEach

            val reqCode = aud.id.hashCode()
            val intent = Intent(context, AudienciaReminderReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, reqCode)
                putExtra(EXTRA_TITLE, "Recordatorio: ${aud.tipoAudiencia}")
                putExtra(EXTRA_BODY, buildSummaryLine(aud))
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Standard inexact alarm (Play Policy compliant without SCHEDULE_EXACT_ALARM)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun buildSummaryLine(audiencia: Audiencia): String {
        val parts = mutableListOf<String>()
        parts.add(FechaHelper.formatFullDateTime(audiencia.fechaHoraMillis))
        if (audiencia.nuc.isNotBlank()) parts.add("NUC: ${audiencia.nuc}")
        if (audiencia.numeroCausa.isNotBlank()) parts.add("CP: ${audiencia.numeroCausa}")
        if (audiencia.tipoDelito.isNotBlank()) parts.add(audiencia.tipoDelito)
        if (audiencia.salaOJuzgado.isNotBlank()) parts.add(audiencia.salaOJuzgado)
        return parts.joinToString(" · ")
    }
}
