package com.onigiri.keycue.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.onigiri.keycue.model.FitProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * [OverlayWindowController] におけるガイドオーバーレイ表示復元経路のテスト。
 *
 * 要件:
 * - activeFitProfile が null（無効プロファイル・画面外等）の場合、
 *   restoreAfterSettings, restoreAfterFilePicker, finishManualFitting, showGuideOverlay
 *   のいずれの復元経路でもガイドを再表示せず GONE を維持すること。
 * - 有効なプロファイルが存在する場合は VISIBLE に復元されること。
 */
class OverlayWindowControllerDisplayRestoreTest {

    private lateinit var controller: OverlayWindowController
    private lateinit var fakeGuideView: TestGuideOverlayView

    class TestGuideOverlayView(context: Context) : GuideOverlayView(context) {
        private var customVisibility: Int = View.VISIBLE

        override fun getVisibility(): Int = customVisibility

        override fun setVisibility(visibility: Int) {
            customVisibility = visibility
        }

        override fun updateFitProfile(profile: FitProfile) {}
        override fun updateGuideLabels(labels: List<String>?) {}
    }

    class FakeTestContext(private val windowManager: WindowManager) : android.content.ContextWrapper(null) {
        override fun getSystemService(name: String): Any? {
            return if (name == Context.WINDOW_SERVICE) windowManager else null
        }
        override fun getApplicationContext(): Context = this
        override fun getResources(): android.content.res.Resources {
            throw UnsupportedOperationException("Unit test stub")
        }
        override fun getPackageName(): String = "com.onigiri.keycue"
    }

    @Before
    fun setUp() {
        val windowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, _, _ -> null } as WindowManager

        val context = FakeTestContext(windowManager)

        controller = OverlayWindowController(
            context = context,
            callbacks = ControlOverlayCallbacks(
                onOpenApp = {},
                onCloseOverlay = {},
                onPositionChanged = { _, _, _, _ -> }
            )
        )

        fakeGuideView = TestGuideOverlayView(context)
        controller.guideOverlayView = fakeGuideView
    }

    @Test
    fun testUpdateFitProfile_null_hidesGuide() {
        fakeGuideView.visibility = View.VISIBLE
        controller.updateFitProfile(null)

        assertEquals(View.GONE, fakeGuideView.visibility)
    }

    @Test
    fun testUpdateFitProfile_valid_showsGuide() {
        fakeGuideView.visibility = View.GONE
        controller.updateFitProfile(FitProfile.createDefaultTestProfile())

        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    @Test
    fun testRestoreAfterFilePicker_whenProfileNull_keepsGone() {
        controller.updateFitProfile(null)
        fakeGuideView.visibility = View.GONE

        controller.restoreAfterFilePicker()

        assertEquals(View.GONE, fakeGuideView.visibility)
    }

    @Test
    fun testRestoreAfterFilePicker_whenProfileValid_restoresVisible() {
        controller.updateFitProfile(FitProfile.createDefaultTestProfile())
        fakeGuideView.visibility = View.GONE

        controller.restoreAfterFilePicker()

        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    @Test
    fun testRestoreAfterSettings_whenProfileNull_keepsGone() {
        controller.updateFitProfile(null)
        fakeGuideView.visibility = View.GONE

        // 設定画面を開いて閉じたシミュレーション
        controller.hideForSettings()
        controller.restoreAfterSettings()

        assertEquals(View.GONE, fakeGuideView.visibility)
    }

    @Test
    fun testRestoreAfterSettings_whenProfileValid_restoresVisible() {
        controller.updateFitProfile(FitProfile.createDefaultTestProfile())
        fakeGuideView.visibility = View.GONE

        controller.hideForSettings()
        controller.restoreAfterSettings()

        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    @Test
    fun testFinishManualFitting_whenProfileNull_keepsGone() {
        controller.updateFitProfile(null)
        fakeGuideView.visibility = View.GONE

        controller.finishManualFitting()

        assertEquals(View.GONE, fakeGuideView.visibility)
    }

    @Test
    fun testFinishManualFitting_whenProfileValid_restoresVisible() {
        controller.updateFitProfile(FitProfile.createDefaultTestProfile())
        fakeGuideView.visibility = View.GONE

        controller.finishManualFitting()

        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    @Test
    fun testShowGuideOverlay_whenAlreadyExistsAndProfileNull_keepsGone() {
        controller.updateFitProfile(null)
        fakeGuideView.visibility = View.GONE

        controller.showGuideOverlay()

        assertEquals(View.GONE, fakeGuideView.visibility)
    }

    @Test
    fun testShowGuideOverlay_whenAlreadyExistsAndProfileValid_showsVisible() {
        controller.updateFitProfile(FitProfile.createDefaultTestProfile())
        fakeGuideView.visibility = View.GONE

        controller.showGuideOverlay()

        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }
}
