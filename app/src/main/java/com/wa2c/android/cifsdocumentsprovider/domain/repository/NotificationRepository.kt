package com.wa2c.android.cifsdocumentsprovider.domain.repository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wa2c.android.cifsdocumentsprovider.R
import com.wa2c.android.cifsdocumentsprovider.domain.model.SendData
import com.wa2c.android.cifsdocumentsprovider.domain.model.SendData.Companion.getSummaryText
import com.wa2c.android.cifsdocumentsprovider.presentation.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    /** Launch intent */
    private val launchIntent by lazy {
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP }
    }

    /** Notification Intent Flag */
    private val notificationFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT

    /** Notification Builder */
    private val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID_SEND)
        .setContentTitle("")
        .setContentText("")
        .setAutoCancel(false)
        .setProgress(0, 0, false)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_REQUEST_CODE, launchIntent, notificationFlag))

    /**
     * Update notification progress
     */
    fun updateProgress(sendData: SendData, countCurrent: Int, countAll: Int) {
        createChannel()
        notificationBuilder.setContentTitle(sendData.name)
        notificationBuilder.setContentText(sendData.getSummaryText(context))
        notificationBuilder.setSubText("[$countCurrent/$countAll]")
        notificationBuilder.setProgress(100, sendData.progress, false)
        val notification = notificationBuilder.build().also {
            it.flags = it.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Finish
     */
    fun complete() {
        notificationBuilder.setContentTitle(context.getString(R.string.notification_title_send_completed))
        notificationBuilder.setContentText(null)
        notificationBuilder.setSubText(null)
        notificationBuilder.setProgress(0, 0, false)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Close
     */
    fun close() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Cancel notification
     */
    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Create channel
     */
    private fun createChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID_SEND) != null) return
        NotificationChannel(
            CHANNEL_ID_SEND,
            context.getString(R.string.notification_channel_name_send),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableLights(false)
            enableVibration(false)
            setShowBadge(true)
            vibrationPattern = longArrayOf(-1)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }.let {
            notificationManager.createNotificationChannel(it)
            it
        }
    }

    companion object {
        private const val CHANNEL_ID_SEND = "notification_channel_send"
        private const val NOTIFICATION_ID = 100
        private const val NOTIFICATION_REQUEST_CODE = 1
    }

}