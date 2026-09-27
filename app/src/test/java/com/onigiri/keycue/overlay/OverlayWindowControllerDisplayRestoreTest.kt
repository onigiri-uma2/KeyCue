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
        private val testResources: android.content.res.Resources by lazy {
            val metrics = android.util.DisplayMetrics().apply {
                density = 2.0f
                widthPixels = 1920
                heightPixels = 1080
            }
            val assetManager = try {
                android.content.res.AssetManager::class.java.getDeclaredConstructor().apply {
                    isAccessible = true
                }.newInstance()
            } catch (_: Exception) {
                null
            }
            val config = android.content.res.Configuration()
            object : android.content.res.Resources(assetManager, metrics, config) {
                override fun getDisplayMetrics(): android.util.DisplayMetrics = metrics
            }
        }

        override fun getResources(): android.content.res.Resources = testResources
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

    /**
     * 追加テスト: 設定画面でガイドを非表示にしたまま微調整を変更
     */
    @Test
    fun testUpdateFitProfile_duringSettings_keepsGuideGone_untilRestoreAfterSettings() {
        val validProfile = FitProfile.createDefaultTestProfile()
        controller.updateFitProfile(validProfile)
        assertEquals(View.VISIBLE, fakeGuideView.visibility)

        // 設定画面を開いて非表示にする
        controller.hideForSettings()
        assertEquals(View.GONE, fakeGuideView.visibility)

        // 設定画面内で微調整を変更（updateFitProfile が呼ばれる）
        controller.updateFitProfile(validProfile)
        // 設定画面表示中なので、activeFitProfile が有効でも GONE のまま維持されること
        assertEquals(View.GONE, fakeGuideView.visibility)

        // 設定画面を閉じたときに初めて VISIBLE に復元されること
        controller.restoreAfterSettings()
        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    /**
     * 追加テスト: ファイル選択中のプロファイル更新
     */
    @Test
    fun testUpdateFitProfile_duringFilePicker_keepsGuideGone_untilRestoreAfterFilePicker() {
        val validProfile = FitProfile.createDefaultTestProfile()
        controller.updateFitProfile(validProfile)
        assertEquals(View.VISIBLE, fakeGuideView.visibility)

        // ファイルピッカーを開いて非表示にする
        controller.hideForFilePicker()
        assertEquals(View.GONE, fakeGuideView.visibility)

        // ファイル選択中にプロファイル更新が通知された場合
        controller.updateFitProfile(validProfile)
        // ファイルピッカー表示中なので GONE のまま維持されること
        assertEquals(View.GONE, fakeGuideView.visibility)

        // ファイルピッカー終了時に初めて VISIBLE に復元されること
        controller.restoreAfterFilePicker()
        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }

    /**
     * 追加テスト: 手動フィッティング中のプロファイル更新
     */
    @Test
    fun testUpdateFitProfile_duringManualFitting_keepsGuideGone_untilFinishManualFitting() {
        val validProfile = FitProfile.createDefaultTestProfile()
        controller.updateFitProfile(validProfile)
        assertEquals(View.VISIBLE, fakeGuideView.visibility)

        // 手動フィッティング開始（fittingOverlayView が表示されている状態）
        controller.fittingOverlayView = View(fakeGuideView.context)
        // 手動フィッティング中は shouldShowGuide が false になり、updateFitProfile を呼んでも GONE が維持されること
        controller.updateFitProfile(validProfile)
        assertEquals(View.GONE, fakeGuideView.visibility)

        // 手動フィッティング終了時に初めて VISIBLE に復元されること
        controller.finishManualFitting()
        assertEquals(View.VISIBLE, fakeGuideView.visibility)
    }
}
