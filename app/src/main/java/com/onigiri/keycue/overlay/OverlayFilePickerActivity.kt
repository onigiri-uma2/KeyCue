package com.onigiri.keycue.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

import android.widget.Toast

/**
 * オーバーレイ表示中から楽曲ファイル選択ピッカー (SAF: Storage Access Framework) を開くための透明な Helper Activity。
 *
 * [MainActivity] を前面に遷移させると背後のゲーム画面のレイアウトやコンテキストが失われるため、
 * 透過テーマの専用Activityを経由して標準のファイル選択（[androidx.activity.result.contract.ActivityResultContracts.OpenDocument]）を起動します。
 *
 * ※ Activity Resultを受け取る必要があるため、マニフェストで `android:noHistory="true"` は指定しません。
 */
class OverlayFilePickerActivity : ComponentActivity() {

    interface OverlayFilePickerListener {
        /**
         * Activityが起動し、ファイルピッカーを表示する直前に呼ばれる。
         * オーバーレイの一時非表示などに利用される。
         */
        fun onPickerStarted()

        /**
         * ファイルが正常に選択された場合に呼ばれる。
         * 読み込み完了時に [onComplete] を呼び出すことで、フォアグラウンドActivityから確実にToastを表示する。
         */
        fun onFileSelected(uri: Uri, onComplete: (title: String?) -> Unit)

        /**
         * ファイル選択がキャンセルされた場合に呼ばれる。
         */
        fun onPickerCancelled()
    }

    companion object {
        private const val KEY_HAS_LAUNCHED = "key_has_launched"

        private var activeListener: OverlayFilePickerListener? = null

        /**
         * OverlayFilePickerActivity を起動する。
         */
        fun start(context: Context, listener: OverlayFilePickerListener) {
            activeListener = listener
            val intent = Intent(context, OverlayFilePickerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            android.util.Log.d("OverlayFilePicker", "start: starting OverlayFilePickerActivity")
            context.startActivity(intent)
        }

        fun clearListener() {
            activeListener = null
        }
    }

    private var hasLaunchedPicker = false
    private var resultHandled = false

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        resultHandled = true
        val listener = activeListener
        activeListener = null
        android.util.Log.d("OverlayFilePicker", "ActivityResult received: uri=$uri, hasListener=${listener != null}")
        if (uri != null && listener != null) {
            listener.onFileSelected(uri) { title ->
                if (title != null) {
                    Toast.makeText(this@OverlayFilePickerActivity, "$title を読み込みました", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OverlayFilePickerActivity, "楽曲を読み込めませんでした", Toast.LENGTH_SHORT).show()
                }
                finishWithNoAnimation()
            }
        } else {
            if (uri != null) {
                listener?.onFileSelected(uri) {}
            } else {
                listener?.onPickerCancelled()
            }
            finishWithNoAnimation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasLaunchedPicker = savedInstanceState?.getBoolean(KEY_HAS_LAUNCHED, false) ?: false
        android.util.Log.d("OverlayFilePicker", "onCreate: hasLaunchedPicker=$hasLaunchedPicker, activeListener=$activeListener")

        if (!hasLaunchedPicker) {
            hasLaunchedPicker = true
            activeListener?.onPickerStarted()

            val mimeTypes = arrayOf(
                "audio/midi",
                "audio/x-midi",
                "audio/*",
                "application/json",
                "text/plain",
                "text/json",
                "text/*",
                "application/octet-stream",
                "*/*"
            )
            openDocumentLauncher.launch(mimeTypes)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_LAUNCHED, hasLaunchedPicker)
    }

    private fun finishWithNoAnimation() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("OverlayFilePicker", "onDestroy: isFinishing=$isFinishing, resultHandled=$resultHandled")
        if (isFinishing && !resultHandled) {
            val listener = activeListener
            activeListener = null
            listener?.onPickerCancelled()
        }
    }
}
