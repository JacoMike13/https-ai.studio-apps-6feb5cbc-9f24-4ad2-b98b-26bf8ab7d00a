package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudienciaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AudienciaNotificationHelper.EXTRA_TITLE)
            ?: "Audiencia programada próxima"
        val body = intent.getStringExtra(AudienciaNotificationHelper.EXTRA_BODY)
            ?: "Tienes un compromiso judicial pendiente en las próximas horas."
        val notifId = intent.getIntExtra(AudienciaNotificationHelper.EXTRA_NOTIFICATION_ID, 2402)

        AudienciaNotificationHelper.showSingleNotification(
            context = context,
            notificationId = notifId,
            title = title,
            body = body
        )
    }
}
