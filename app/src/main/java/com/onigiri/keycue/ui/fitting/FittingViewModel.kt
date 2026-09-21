package com.onigiri.keycue.ui.fitting

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onigiri.keycue.data.SettingsRepository
import com.onigiri.keycue.data.SharedPreferencesSettingsRepository
import com.onigiri.keycue.fitting.GridFitter
import com.onigiri.keycue.fitting.KeyDetector
import com.onigiri.keycue.fitting.OpenCvKeyDetector
import com.onigiri.keycue.model.FitProfile
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.profile.GameProfile
import com.onigiri.keycue.profile.GameProfileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * フィッティング画面（[FittingScreen]）のビジネスロジック、画像解析実行、手動調整を統括する ViewModel。
 *
 * スクリーンショットの読み込み、[KeyDetector] による円形キー検出、[GridFitter] による格子適合、
 * 4隅ドラッグによる手動微調整、および確定したキー配置（[com.onigiri.keycue.model.FitProfile]）の
 * [SettingsRepository] への永続化を担当します。
 */
class FittingViewModel(
    private val settingsRepository: SettingsRepository,
    private val keyDetector: KeyDetector = OpenCvKeyDetector(),
    private val gridFitter: GridFitter = GridFitter(),
    private val gameProfile: GameProfile = GameProfileRegistry.current,
    externalScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(FittingUiState())
    val uiState: StateFlow<FittingUiState> = _uiState.asStateFlow()

    /** 自動検出結果として得られたキー半径比率（初回確定保存時にVisualConfigへ初期反映可能） */
    private var pendingDetectedGuideRadiusRatio: Float? = null

    init {
        // 保存済みFitProfileの購読
        scope.launch {
            settingsRepository.fitProfile.collect { saved ->
                _uiState.update { it.copy(savedProfile = saved) }
            }
        }
    }

    /**
     * スクリーンショット画像の選択・デコード処理。
     * 巨大画像の場合はメモリ節約のため適切にダウンサンプリングする。
     */
    fun onImageSelected(uri: Uri?, contentResolver: ContentResolver) {
        if (uri == null) return
        pendingDetectedGuideRadiusRatio = null

        scope.launch(Dispatchers.IO) {
            try {
                // 1. 画像サイズの確認 (inJustDecodeBounds = true)
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val origWidth = boundsOptions.outWidth
                val origHeight = boundsOptions.outHeight

                if (origWidth <= 0 || origHeight <= 0) {
                    _uiState.update {
                        it.copy(errorMessage = "画像の読み込みに失敗しました (形式不正)")
                    }
                    return@launch
                }

                // 2. ダウンサンプリング比率の算出
                var sampleSize = 1
                val maxDim = max(origWidth, origHeight)
                while (maxDim / (sampleSize * 2) >= MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }

                // 3. ビットマップデコード
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                if (bitmap == null) {
                    _uiState.update {
                        it.copy(errorMessage = "画像のデコードに失敗しました")
                    }
                    return@launch
                }

                val isLandscape = origWidth >= origHeight

                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            step = FittingStep.ImageSelected,
                            imageUri = uri,
                            imageBitmap = bitmap,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            isLandscape = isLandscape,
                            detectedPoints = emptyList(),
                            currentProfile = null,
                            confidence = 0f,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(errorMessage = "画像読み込みエラー: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    /**
     * ボタン候補点検出とグリッドフィッティングを非同期実行する。
     */
    fun detectAndFit() {
        val bitmap = _uiState.value.imageBitmap ?: return

        _uiState.update { it.copy(step = FittingStep.Detecting, errorMessage = null) }

        scope.launch(Dispatchers.Default) {
            try {
                // 1. OpenCV による候補点検出
                val candidates = keyDetector.detect(bitmap)

                // 2. GridFitter による格子フィッティング
                val fitResult = gridFitter.fit(
                    candidates = candidates,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )

                withContext(Dispatchers.Main) {
                    if (fitResult.profile != null) {
                        val profile = fitResult.profile
                        pendingDetectedGuideRadiusRatio = profile.keyRadiusRatio
                        val centers = profile.keyCenters
                        _uiState.update { current ->
                            current.copy(
                                step = FittingStep.Success(fitResult.confidence),
                                detectedPoints = candidates,
                                currentProfile = profile,
                                confidence = fitResult.confidence,
                                manualTopLeft = centers[gameProfile.topLeftKeyIndex],
                                manualTopRight = centers[gameProfile.topRightKeyIndex],
                                manualBottomLeft = centers[gameProfile.bottomLeftKeyIndex],
                                manualBottomRight = centers[gameProfile.bottomRightKeyIndex],
                                errorMessage = null
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                step = FittingStep.Failed(fitResult.errorMessage ?: "ボタン配置を検出できませんでした"),
                                detectedPoints = candidates,
                                confidence = fitResult.confidence,
                                errorMessage = fitResult.errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            step = FittingStep.Failed("解析処理中にエラーが発生しました: ${e.localizedMessage}"),
                            errorMessage = e.localizedMessage
                        )
                    }
                }
            }
        }
    }

    /**
     * 手動補正モード（4点ドラッグ）を開始する。
     */
    fun startManualAdjust() {
        val current = _uiState.value
        val profile = current.currentProfile
            ?: current.savedProfile
            ?: FitProfile.createDefaultTestProfile(current.isLandscape)

        val centers = profile.keyCenters
        _uiState.update {
            it.copy(
                step = FittingStep.ManualAdjust,
                currentProfile = profile,
                manualTopLeft = centers[gameProfile.topLeftKeyIndex],
                manualTopRight = centers[gameProfile.topRightKeyIndex],
                manualBottomLeft = centers[gameProfile.bottomLeftKeyIndex],
                manualBottomRight = centers[gameProfile.bottomRightKeyIndex]
            )
        }
    }

    /**
     * 微調整対象のコーナー（0: Key0, 1: Key4, 2: Key10, 3: Key14）を選択する。
     */
    fun selectCorner(cornerIndex: Int) {
        if (cornerIndex in 0..3) {
            _uiState.update { it.copy(selectedCorner = cornerIndex) }
        }
    }

    /**
     * 十字キーボタン押下時: 選択中のコーナーを微小移動（nudge）する。
     */
    fun nudgeSelectedCorner(deltaNormX: Float, deltaNormY: Float) {
        val selected = _uiState.value.selectedCorner
        moveManualCornerDelta(selected, deltaNormX, deltaNormY)
    }

    /**
     * 手動調整ハンドルの移動差分（delta）を現在位置に累積加算し、全キー配置を再算出する。
     *
     * @param cornerIndex 0: 左上, 1: 右上, 2: 左下, 3: 右下
     * @param deltaNormX 正規化Xの移動量
     * @param deltaNormY 正規化Yの移動量
     */
    fun moveManualCornerDelta(cornerIndex: Int, deltaNormX: Float, deltaNormY: Float) {
        val current = _uiState.value
        val oldPoint = when (cornerIndex) {
            0 -> current.manualTopLeft
            1 -> current.manualTopRight
            2 -> current.manualBottomLeft
            3 -> current.manualBottomRight
            else -> return
        }
        val newX = (oldPoint.x + deltaNormX).coerceIn(0f, 1f)
        val newY = (oldPoint.y + deltaNormY).coerceIn(0f, 1f)
        _uiState.update { it.copy(selectedCorner = cornerIndex) }
        updateManualCorner(cornerIndex, newX, newY)
    }

    /**
     * 手動調整ハンドルの座標を更新し、バイリニア補間で全キーを即座に再算出する。
     *
     * @param cornerIndex 0: 左上, 1: 右上, 2: 左下, 3: 右下
     * @param normX 新しい正規化X座標 (0.0..1.0)
     * @param normY 新しい正規化Y座標 (0.0..1.0)
     */
    fun updateManualCorner(cornerIndex: Int, normX: Float, normY: Float) {
        val clampedX = normX.coerceIn(0f, 1f)
        val clampedY = normY.coerceIn(0f, 1f)
        val newPoint = NormalizedPoint(clampedX, clampedY)

        val current = _uiState.value
        val tl = if (cornerIndex == 0) newPoint else current.manualTopLeft
        val tr = if (cornerIndex == 1) newPoint else current.manualTopRight
        val bl = if (cornerIndex == 2) newPoint else current.manualBottomLeft
        val br = if (cornerIndex == 3) newPoint else current.manualBottomRight

        // 4隅から全キーをバイリニア補間
        val interpolated = gridFitter.interpolateGridFromCorners(tl, tr, bl, br)
        val updatedProfile = FitProfile(
            keyCenters = interpolated,
            keyRadiusRatio = current.currentProfile?.keyRadiusRatio ?: 0.04f,
            landscape = current.isLandscape
        )

        _uiState.update {
            it.copy(
                manualTopLeft = tl,
                manualTopRight = tr,
                manualBottomLeft = bl,
                manualBottomRight = br,
                currentProfile = updatedProfile
            )
        }
    }

    /**
     * 手動調整を確定して Success 表示へ戻る。
     */
    fun finishManualAdjust() {
        val current = _uiState.value
        if (current.currentProfile != null) {
            _uiState.update {
                it.copy(step = FittingStep.Success(confidence = 1.0f))
            }
        }
    }

    /**
     * 保存済みのFitProfileを即座に適用する。
     */
    fun useSavedProfile() {
        pendingDetectedGuideRadiusRatio = null
        val saved = _uiState.value.savedProfile ?: return
        _uiState.update {
            it.copy(
                currentProfile = saved,
                step = FittingStep.Success(confidence = 1.0f)
            )
        }
    }

    /**
     * [ この位置を使用 ] ボタン押下時:
     * 現在の FitProfile を SettingsRepository へ保存する。
     */
    fun saveCurrentProfile() {
        val profile = _uiState.value.currentProfile ?: return
        val isFirstFitting = _uiState.value.savedProfile == null
        val detectedRadiusToApply = if (isFirstFitting) pendingDetectedGuideRadiusRatio else null
        pendingDetectedGuideRadiusRatio = null

        scope.launch {
            settingsRepository.saveFitProfile(profile)
            if (detectedRadiusToApply != null) {
                settingsRepository.saveGuideRadiusRatio(detectedRadiusToApply)
            }
            val currentSession = com.onigiri.keycue.data.InMemoryPlaybackSessionRepository.instance.currentSession.value
            if (currentSession != null) {
                com.onigiri.keycue.data.InMemoryPlaybackSessionRepository.instance.setSession(
                    songData = currentSession.song,
                    config = settingsRepository.playbackConfig.value,
                    fitProfile = profile,
                    uri = currentSession.uri,
                    format = currentSession.format,
                    resolvedMidiMapping = currentSession.resolvedMidiMapping
                )
            }
            _uiState.update { it.copy(isSavedSuccess = true) }
        }
    }

    /**
     * デバッグ表示のトグル
     */
    fun toggleDebugView() {
        _uiState.update { it.copy(showDebugView = !it.showDebugView) }
    }

    /**
     * エラーメッセージのクリア
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setDetectedResultForTest(profile: FitProfile) {
        pendingDetectedGuideRadiusRatio = profile.keyRadiusRatio
        val centers = profile.keyCenters
        _uiState.update { current ->
            current.copy(
                step = FittingStep.Success(1.0f),
                currentProfile = profile,
                confidence = 1.0f,
                manualTopLeft = centers[gameProfile.topLeftKeyIndex],
                manualTopRight = centers[gameProfile.topRightKeyIndex],
                manualBottomLeft = centers[gameProfile.bottomLeftKeyIndex],
                manualBottomRight = centers[gameProfile.bottomRightKeyIndex],
                errorMessage = null
            )
        }
    }

    companion object {
        private const val TAG = "FittingViewModel"
        private const val MAX_IMAGE_DIMENSION = 1920

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FittingViewModel(
                        settingsRepository = SharedPreferencesSettingsRepository.getInstance(context)
                    ) as T
                }
            }
    }
}
