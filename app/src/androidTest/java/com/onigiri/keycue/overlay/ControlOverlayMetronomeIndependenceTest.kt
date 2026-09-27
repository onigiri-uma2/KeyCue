package com.onigiri.keycue.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onigiri.keycue.model.ControlOverlayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ControlOverlayView] における「ガイド表示切替」と「メトロノーム操作」セクションの
 * 表示・非表示（実際の View.visibility）が完全に独立して制御されることを検証する Android 計測テスト。
 */
@RunWith(AndroidJUnit4::class)
class ControlOverlayMetronomeIndependenceTest {

    private lateinit var overlayView: ControlOverlayView
    private lateinit var context: Context

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        instrumentation.runOnMainSync {
            overlayView = ControlOverlayView(
                context = context,
                windowManager = windowManager,
                layoutParams = WindowManager.LayoutParams(),
                callbacks = ControlOverlayCallbacks(
                    onOpenApp = {},
                    onCloseOverlay = {},
                    onPositionChanged = { _, _, _, _ -> }
                )
            )
        }
    }

    @Test
    fun testGuideVisible_metroHidden() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            overlayView.updateControlOverlayConfig(
                ControlOverlayConfig(
                    showGuideQuickToggles = true,
                    showMetronomeControl = false
                )
            )
            assertTrue("ガイドクイック切替行が表示されること", overlayView.isGuideQuickToggleSectionVisible)
            assertFalse("メトロノーム操作行は非表示になること", overlayView.isMetronomeSectionVisible)
        }
    }

    @Test
    fun testGuideHidden_metroVisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            overlayView.updateControlOverlayConfig(
                ControlOverlayConfig(
                    showGuideQuickToggles = false,
                    showMetronomeControl = true
                )
            )
            assertFalse("ガイドクイック切替行が非表示になること", overlayView.isGuideQuickToggleSectionVisible)
            assertTrue("メトロノーム操作行は表示されること", overlayView.isMetronomeSectionVisible)
        }
    }

    @Test
    fun testBothHidden() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            overlayView.updateControlOverlayConfig(
                ControlOverlayConfig(
                    showGuideQuickToggles = false,
                    showMetronomeControl = false
                )
            )
            assertFalse("ガイドクイック切替行が非表示になること", overlayView.isGuideQuickToggleSectionVisible)
            assertFalse("メトロノーム操作行も非表示になること", overlayView.isMetronomeSectionVisible)
        }
    }

    @Test
    fun testBothVisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            overlayView.updateControlOverlayConfig(
                ControlOverlayConfig(
                    showGuideQuickToggles = true,
                    showMetronomeControl = true
                )
            )
            assertTrue("ガイドクイック切替行が表示されること", overlayView.isGuideQuickToggleSectionVisible)
            assertTrue("メトロノーム操作行も表示されること", overlayView.isMetronomeSectionVisible)
        }
    }
}
