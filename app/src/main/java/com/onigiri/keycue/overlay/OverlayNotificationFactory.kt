package com.onigiri.keycue.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.onigiri.keycue.R
import com.onigiri.keycue.app.MainActivity

/**
 * [OverlayService] がフォアグラウンドサービスとして常駐するために必要な通知を生成するファクトリ。
 *
 * 通知タップで本体アプリ（[MainActivity]）を開く PendingIntent や、
 * オーバーレイ停止アクション等の通知アクションを構成します。
 */
object OverlayNotificationFactory {

    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "keycue_overlay_channel"
    private const val CHANNEL_NAME = "KeyCue Overlay"

    /**
     * 通知チャンネルをシステムに登録する（API 26以降）。
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "演奏支援オーバーレイ常駐用通知"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Foreground Service用の通知インスタンスを構築する。
     */
    fun buildNotification(context: Context): Notification {
        // MainActivityを前面に戻すPendingIntent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // オーバーレイおよびサービスを停止するPendingIntent
        val stopIntent = OverlayService.createStopIntent(context)
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KeyCue")
            .setContentText("演奏支援オーバーレイを実行中")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "終了",
                stopPendingIntent
            )
            .build()
    }
}
