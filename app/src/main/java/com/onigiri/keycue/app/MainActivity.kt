package com.onigiri.keycue.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.onigiri.keycue.overlay.OverlayService
import com.onigiri.keycue.ui.theme.KeyCueTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPENED_FROM_OVERLAY = "com.onigiri.keycue.extra.OPENED_FROM_OVERLAY"
        private const val KEY_OPENED_FROM_OVERLAY = "key_opened_from_overlay"
    }

    private var openedFromOverlay by mutableStateOf(false)
    private var resetToHomeTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openedFromOverlay = if (intent.hasExtra(EXTRA_OPENED_FROM_OVERLAY)) {
            intent.getBooleanExtra(EXTRA_OPENED_FROM_OVERLAY, false)
        } else {
            savedInstanceState?.getBoolean(KEY_OPENED_FROM_OVERLAY) ?: false
        }

        if (openedFromOverlay) {
            resetToHomeTrigger++
        }

        enableEdgeToEdge()
        setContent {
            KeyCueTheme {
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    openedFromOverlay = openedFromOverlay,
                    resetToHomeTrigger = resetToHomeTrigger,
                    onCloseSettings = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val fromOverlay = intent.getBooleanExtra(EXTRA_OPENED_FROM_OVERLAY, false)
        openedFromOverlay = fromOverlay
        if (fromOverlay) {
            resetToHomeTrigger++
            OverlayService.setOverlayVisibleForSettings(false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPENED_FROM_OVERLAY, openedFromOverlay)
    }

    override fun onStart() {
        super.onStart()
        if (openedFromOverlay) {
            OverlayService.setOverlayVisibleForSettings(false)
        }
    }

    override fun onStop() {
        super.onStop()
        // Configuration Change (画面回転等) 時は不要な再表示を行わない
        if (openedFromOverlay && !isChangingConfigurations) {
            OverlayService.setOverlayVisibleForSettings(true)
        }
    }
}
