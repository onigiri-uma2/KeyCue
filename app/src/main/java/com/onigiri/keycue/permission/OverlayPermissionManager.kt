package com.onigiri.keycue.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 他のアプリの上に重ねて表示する権限 ([android.Manifest.permission.SYSTEM_ALERT_WINDOW]) の状態確認および
 * システム設定画面への誘導を担うマネージャー。
 */
class OverlayPermissionManager(
    private val context: Context
) {

    /**
     * アプリが画面オーバーレイを描画できる状態かどうかを判定する。
     */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * システムのオーバーレイ権限設定画面へ遷移するための Intent を生成する。
     */
    fun createSettingsIntent(): Intent {
        val uri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
