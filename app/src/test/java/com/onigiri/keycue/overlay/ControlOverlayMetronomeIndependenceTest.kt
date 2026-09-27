package com.onigiri.keycue.overlay

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.WindowManager
import com.onigiri.keycue.model.ControlOverlayConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * [ControlOverlayView] における「ガイド表示切替」と「メトロノーム操作」セクションの
 * 表示・非表示（visibility）が完全に独立して制御されることを検証するテスト。
 */
class ControlOverlayMetronomeIndependenceTest {

    private lateinit var overlayView: ControlOverlayView

    class FakeTestContext(private val windowManager: WindowManager) : android.content.ContextWrapper(null) {
        override fun getSystemService(name: String): Any? {
            return if (name == Context.WINDOW_SERVICE) windowManager else null
        }
        override fun getApplicationContext(): Context = this
        private val testResources: Resources by lazy {
            val metrics = DisplayMetrics().apply {
                density = 2.0f
                widthPixels = 1920
                heightPixels = 1080
            }
            val assetManager = try {
                AssetManager::class.java.getDeclaredConstructor().apply {
                    isAccessible = true
                }.newInstance()
            } catch (_: Exception) {
                null
            }
            val config = Configuration()
            object : Resources(assetManager, metrics, config) {
                override fun getDisplayMetrics(): DisplayMetrics = metrics
            }
        }

        override fun getResources(): Resources = testResources
        override fun getPackageName(): String = "com.onigiri.keycue"
    }

    @Before
    fun setUp() {
        val windowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, _, _ -> null } as WindowManager

        val context = FakeTestContext(windowManager)
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

    @Test
    fun testGuideVisible_metroHidden() {
        overlayView.updateControlOverlayConfig(
            ControlOverlayConfig(
                showGuideQuickToggles = true,
                showMetronomeControl = false
            )
        )
        assertTrue("ガイドクイック切替行が表示されること", overlayView.isGuideQuickToggleSectionVisible)
        assertFalse("メトロノーム操作行は非表示になること", overlayView.isMetronomeSectionVisible)
    }

    @Test
    fun testGuideHidden_metroVisible() {
        overlayView.updateControlOverlayConfig(
            ControlOverlayConfig(
                showGuideQuickToggles = false,
                showMetronomeControl = true
            )
        )
        assertFalse("ガイドクイック切替行が非表示になること", overlayView.isGuideQuickToggleSectionVisible)
        assertTrue("メトロノーム操作行は表示されること", overlayView.isMetronomeSectionVisible)
    }

    @Test
    fun testBothHidden() {
        overlayView.updateControlOverlayConfig(
            ControlOverlayConfig(
                showGuideQuickToggles = false,
                showMetronomeControl = false
            )
        )
        assertFalse("ガイドクイック切替行が非表示になること", overlayView.isGuideQuickToggleSectionVisible)
        assertFalse("メトロノーム操作行も非表示になること", overlayView.isMetronomeSectionVisible)
    }

    @Test
    fun testBothVisible() {
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
