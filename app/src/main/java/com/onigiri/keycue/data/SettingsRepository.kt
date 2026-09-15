package com.onigiri.keycue.data

import android.content.Context
import android.content.SharedPreferences
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.model.PlaybackConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリ設定およびユーザー設定状態の永続化を担当するリポジトリインターフェース。
 *
 * 楽曲URI、再生速度、先読み時間、キー配置プロファイル（FitProfile）、
 * ビジュアル設定（VisualConfig）、MIDIマッピング設定等の Single Source of Truth として機能します。
 */
interface SettingsRepository {
    /** 最後に選択された楽曲のURI文字列（StateFlow） */
    val lastSongUri: StateFlow<String?>
    suspend fun saveLastSongUri(uri: String?)

    /** 再生速度比率 (1.0f = 100%) */
    val speed: StateFlow<Float>
    suspend fun saveSpeed(speed: Float)

    /** 先読み時間（ミリ秒） */
    val leadTimeMs: StateFlow<Long>
    suspend fun saveLeadTimeMs(leadTimeMs: Long)

    /** 事前ハイライト時間（ミリ秒） */
    val highlightTimeMs: StateFlow<Long>
    suspend fun saveHighlightTimeMs(highlightTimeMs: Long)

    /** 開始前カウントダウン時間（ミリ秒） */
    val countdownMs: StateFlow<Long>
    suspend fun saveCountdownMs(countdownMs: Long)

    /** 再生設定全体のまとまり */
    val playbackConfig: StateFlow<PlaybackConfig>
    suspend fun savePlaybackConfig(config: PlaybackConfig)

    /** オーバーレイボタンの正規化座標 (x: 0..1, y: 0..1) */
    val overlayPositionNormalized: StateFlow<NormalizedPoint?>
    suspend fun saveOverlayPositionNormalized(x: Float, y: Float)

    /** オーバーレイボタンの表示位置 (x, y) ピクセル（後方互換用） */
    val overlayPosition: StateFlow<Pair<Int, Int>?>
    suspend fun saveOverlayPosition(x: Int, y: Int)

    /** 15キーの画面上配置プロファイル (FitProfile) */
    val fitProfile: StateFlow<com.onigiri.keycue.model.FitProfile?>
    suspend fun saveFitProfile(profile: com.onigiri.keycue.model.FitProfile?)

    /** 演奏ガイドおよびノーツのビジュアル設定 (VisualConfig) */
    val visualConfig: StateFlow<com.onigiri.keycue.model.VisualConfig>
    suspend fun saveVisualConfig(config: com.onigiri.keycue.model.VisualConfig)
    suspend fun updateVisualConfig(transform: (com.onigiri.keycue.model.VisualConfig) -> com.onigiri.keycue.model.VisualConfig) =
        saveVisualConfig(transform(visualConfig.value))

    suspend fun saveShowKeyNumbers(show: Boolean) = updateVisualConfig { it.copy(showKeyNumbers = show) }
    suspend fun saveShowFallingNotes(show: Boolean) = updateVisualConfig { it.copy(showFallingNotes = show) }
    suspend fun saveShowApproachCircles(show: Boolean) = updateVisualConfig { it.copy(showApproachCircles = show) }
    suspend fun saveShowJustEffect(show: Boolean) = updateVisualConfig { it.copy(showJustEffect = show) }
    suspend fun saveGuideRadiusRatio(ratio: Float) = updateVisualConfig {
        it.copy(guideRadiusRatio = ratio.coerceIn(com.onigiri.keycue.model.VisualConfig.MIN_GUIDE_RADIUS_RATIO, com.onigiri.keycue.model.VisualConfig.MAX_GUIDE_RADIUS_RATIO))
    }
    suspend fun saveGuideColor(color: Int) = updateVisualConfig { it.copy(guideColor = color) }
    suspend fun saveNoteColorTop(color: Int) = updateVisualConfig { it.copy(noteColorTop = color) }
    suspend fun saveNoteColorMiddle(color: Int) = updateVisualConfig { it.copy(noteColorMiddle = color) }
    suspend fun saveNoteColorBottom(color: Int) = updateVisualConfig { it.copy(noteColorBottom = color) }

    /** MIDIキーマッピング設定 (MidiMappingSettings) */
    val midiMappingSettings: StateFlow<com.onigiri.keycue.model.MidiMappingSettings>
    suspend fun saveMidiMappingSettings(settings: com.onigiri.keycue.model.MidiMappingSettings)
    suspend fun updateMidiMappingSettings(transform: (com.onigiri.keycue.model.MidiMappingSettings) -> com.onigiri.keycue.model.MidiMappingSettings) =
        saveMidiMappingSettings(transform(midiMappingSettings.value))

    suspend fun saveMidiMappingMode(mode: com.onigiri.keycue.model.MidiMappingMode) =
        updateMidiMappingSettings { it.copy(mode = mode) }
    suspend fun saveManualRoot(root: com.onigiri.keycue.model.PitchClass) =
        updateMidiMappingSettings { it.copy(manualRoot = root) }
    suspend fun saveManualScale(scale: com.onigiri.keycue.model.ScaleType) =
        updateMidiMappingSettings { it.copy(manualScale = scale) }
    suspend fun saveManualBaseOctave(octave: Int) =
        updateMidiMappingSettings { it.copy(manualBaseOctave = octave.coerceIn(2, 6)) }
}

/**
 * Android標準のSharedPreferencesを利用した永続化 SettingsRepository 実装。
 */
class SharedPreferencesSettingsRepository(
    context: Context,
    prefsName: String = "keycue_settings"
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SONG_URI = "last_song_uri"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_LEAD_TIME = "lead_time_ms"
        private const val KEY_HIGHLIGHT_TIME = "highlight_time_ms"
        private const val KEY_COUNTDOWN = "countdown_ms"
        private const val KEY_OVERLAY_NORM_X = "overlay_norm_x"
        private const val KEY_OVERLAY_NORM_Y = "overlay_norm_y"
        private const val KEY_OVERLAY_PIXEL_X = "overlay_pixel_x"
        private const val KEY_OVERLAY_PIXEL_Y = "overlay_pixel_y"
        private const val KEY_FIT_PROFILE = "fit_profile_json"
        private const val KEY_SHOW_KEY_NUMBERS = "show_key_numbers"
        private const val KEY_SHOW_FALLING_NOTES = "show_falling_notes"
        private const val KEY_SHOW_APPROACH_CIRCLES = "show_approach_circles"
        private const val KEY_SHOW_JUST_EFFECT = "show_just_effect"
        private const val KEY_GUIDE_RADIUS_RATIO = "guide_radius_ratio"
        private const val KEY_GUIDE_COLOR = "guide_color"
        private const val KEY_NOTE_COLOR_TOP = "note_color_top"
        private const val KEY_NOTE_COLOR_MIDDLE = "note_color_middle"
        private const val KEY_NOTE_COLOR_BOTTOM = "note_color_bottom"
        private const val KEY_MIDI_MAPPING_MODE = "midi_mapping_mode"
        private const val KEY_MIDI_MANUAL_ROOT = "midi_manual_root"
        private const val KEY_MIDI_MANUAL_SCALE = "midi_manual_scale"
        private const val KEY_MIDI_MANUAL_BASE_OCTAVE = "midi_manual_base_octave"

        @Volatile
        private var instance: SharedPreferencesSettingsRepository? = null

        fun getInstance(context: Context): SharedPreferencesSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesSettingsRepository(context).also { instance = it }
            }
        }
    }

    private val _lastSongUri = MutableStateFlow(prefs.getString(KEY_LAST_SONG_URI, null))
    override val lastSongUri: StateFlow<String?> = _lastSongUri.asStateFlow()

    private val _speed = MutableStateFlow(prefs.getFloat(KEY_SPEED, 1.0f).coerceIn(0.25f, 2.0f))
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _leadTimeMs = MutableStateFlow(prefs.getLong(KEY_LEAD_TIME, 700L).coerceIn(300L, 2000L))
    override val leadTimeMs: StateFlow<Long> = _leadTimeMs.asStateFlow()

    private val _highlightTimeMs = MutableStateFlow(prefs.getLong(KEY_HIGHLIGHT_TIME, 500L).coerceIn(100L, 1000L))
    override val highlightTimeMs: StateFlow<Long> = _highlightTimeMs.asStateFlow()

    private val _countdownMs = MutableStateFlow(prefs.getLong(KEY_COUNTDOWN, 3000L).coerceIn(0L, 5000L))
    override val countdownMs: StateFlow<Long> = _countdownMs.asStateFlow()

    private val _playbackConfig = MutableStateFlow(
        PlaybackConfig(
            speed = _speed.value,
            leadTimeMs = _leadTimeMs.value,
            highlightTimeMs = _highlightTimeMs.value,
            countdownMs = _countdownMs.value
        )
    )
    override val playbackConfig: StateFlow<PlaybackConfig> = _playbackConfig.asStateFlow()

    private val _overlayPositionNormalized: MutableStateFlow<NormalizedPoint?> = MutableStateFlow(
        if (prefs.contains(KEY_OVERLAY_NORM_X) && prefs.contains(KEY_OVERLAY_NORM_Y)) {
            val nx = prefs.getFloat(KEY_OVERLAY_NORM_X, 0.8f).coerceIn(0f, 1f)
            val ny = prefs.getFloat(KEY_OVERLAY_NORM_Y, 0.15f).coerceIn(0f, 1f)
            NormalizedPoint(nx, ny)
        } else {
            null
        }
    )
    override val overlayPositionNormalized: StateFlow<NormalizedPoint?> = _overlayPositionNormalized.asStateFlow()

    private val _overlayPosition = MutableStateFlow(
        if (prefs.contains(KEY_OVERLAY_PIXEL_X) && prefs.contains(KEY_OVERLAY_PIXEL_Y)) {
            Pair(prefs.getInt(KEY_OVERLAY_PIXEL_X, 0), prefs.getInt(KEY_OVERLAY_PIXEL_Y, 0))
        } else {
            null
        }
    )
    override val overlayPosition: StateFlow<Pair<Int, Int>?> = _overlayPosition.asStateFlow()

    private val _fitProfile: MutableStateFlow<com.onigiri.keycue.model.FitProfile?> = MutableStateFlow(
        com.onigiri.keycue.model.FitProfileSerializer.fromJson(prefs.getString(KEY_FIT_PROFILE, null))
    )
    override val fitProfile: StateFlow<com.onigiri.keycue.model.FitProfile?> = _fitProfile.asStateFlow()

    private val _visualConfig: MutableStateFlow<com.onigiri.keycue.model.VisualConfig> = MutableStateFlow(
        com.onigiri.keycue.model.VisualConfig.safe(
            showKeyNumbers = prefs.getBoolean(KEY_SHOW_KEY_NUMBERS, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_KEY_NUMBERS),
            showFallingNotes = prefs.getBoolean(KEY_SHOW_FALLING_NOTES, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_FALLING_NOTES),
            showApproachCircles = prefs.getBoolean(KEY_SHOW_APPROACH_CIRCLES, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_APPROACH_CIRCLES),
            showJustEffect = prefs.getBoolean(KEY_SHOW_JUST_EFFECT, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_JUST_EFFECT),
            guideRadiusRatio = prefs.getFloat(KEY_GUIDE_RADIUS_RATIO, _fitProfile.value?.keyRadiusRatio ?: com.onigiri.keycue.model.VisualConfig.DEFAULT_GUIDE_RADIUS_RATIO),
            guideColor = prefs.getInt(KEY_GUIDE_COLOR, com.onigiri.keycue.model.VisualConfig.DEFAULT_GUIDE_COLOR),
            noteColorTop = prefs.getInt(KEY_NOTE_COLOR_TOP, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_TOP),
            noteColorMiddle = prefs.getInt(KEY_NOTE_COLOR_MIDDLE, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_MIDDLE),
            noteColorBottom = prefs.getInt(KEY_NOTE_COLOR_BOTTOM, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_BOTTOM)
        )
    )
    override val visualConfig: StateFlow<com.onigiri.keycue.model.VisualConfig> = _visualConfig.asStateFlow()

    private val _midiMappingSettings: MutableStateFlow<com.onigiri.keycue.model.MidiMappingSettings> = MutableStateFlow(
        com.onigiri.keycue.model.MidiMappingSettings(
            mode = prefs.getString(KEY_MIDI_MAPPING_MODE, null)?.let {
                try { com.onigiri.keycue.model.MidiMappingMode.valueOf(it) } catch (_: Exception) { null }
            } ?: com.onigiri.keycue.model.MidiMappingMode.AUTO,
            manualRoot = prefs.getString(KEY_MIDI_MANUAL_ROOT, null)?.let {
                try { com.onigiri.keycue.model.PitchClass.valueOf(it) } catch (_: Exception) { null }
            } ?: com.onigiri.keycue.model.PitchClass.C,
            manualScale = prefs.getString(KEY_MIDI_MANUAL_SCALE, null)?.let {
                try { com.onigiri.keycue.model.ScaleType.valueOf(it) } catch (_: Exception) { null }
            } ?: com.onigiri.keycue.model.ScaleType.MAJOR,
            manualBaseOctave = prefs.getInt(KEY_MIDI_MANUAL_BASE_OCTAVE, 4).coerceIn(2, 6)
        )
    )
    override val midiMappingSettings: StateFlow<com.onigiri.keycue.model.MidiMappingSettings> = _midiMappingSettings.asStateFlow()

    override suspend fun saveLastSongUri(uri: String?) {
        _lastSongUri.value = uri
        prefs.edit().apply {
            if (uri != null) {
                putString(KEY_LAST_SONG_URI, uri)
            } else {
                remove(KEY_LAST_SONG_URI)
            }
        }.apply()
    }

    override suspend fun saveSpeed(speed: Float) {
        savePlaybackConfig(_playbackConfig.value.copy(speed = speed))
    }

    override suspend fun saveLeadTimeMs(leadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(leadTimeMs = leadTimeMs))
    }

    override suspend fun saveHighlightTimeMs(highlightTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(highlightTimeMs = highlightTimeMs))
    }

    override suspend fun saveCountdownMs(countdownMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(countdownMs = countdownMs))
    }

    override suspend fun savePlaybackConfig(config: PlaybackConfig) {
        val normalized = config.normalized()
        applyPlaybackConfig(normalized)

        prefs.edit()
            .putFloat(KEY_SPEED, normalized.speed)
            .putLong(KEY_LEAD_TIME, normalized.leadTimeMs)
            .putLong(KEY_HIGHLIGHT_TIME, normalized.highlightTimeMs)
            .putLong(KEY_COUNTDOWN, normalized.countdownMs)
            .apply()
    }

    override suspend fun saveOverlayPositionNormalized(x: Float, y: Float) {
        val clampedX = x.coerceIn(0f, 1f)
        val clampedY = y.coerceIn(0f, 1f)
        val point = NormalizedPoint(clampedX, clampedY)
        _overlayPositionNormalized.value = point
        prefs.edit()
            .putFloat(KEY_OVERLAY_NORM_X, clampedX)
            .putFloat(KEY_OVERLAY_NORM_Y, clampedY)
            .apply()
    }

    override suspend fun saveOverlayPosition(x: Int, y: Int) {
        _overlayPosition.value = Pair(x, y)
        prefs.edit()
            .putInt(KEY_OVERLAY_PIXEL_X, x)
            .putInt(KEY_OVERLAY_PIXEL_Y, y)
            .apply()
    }

    override suspend fun saveFitProfile(profile: com.onigiri.keycue.model.FitProfile?) {
        _fitProfile.value = profile
        prefs.edit().apply {
            if (profile != null) {
                putString(KEY_FIT_PROFILE, com.onigiri.keycue.model.FitProfileSerializer.toJson(profile))
            } else {
                remove(KEY_FIT_PROFILE)
            }
        }.apply()
    }

    override suspend fun saveVisualConfig(config: com.onigiri.keycue.model.VisualConfig) {
        val safeConfig = com.onigiri.keycue.model.VisualConfig.safe(
            showKeyNumbers = config.showKeyNumbers,
            showFallingNotes = config.showFallingNotes,
            showApproachCircles = config.showApproachCircles,
            showJustEffect = config.showJustEffect,
            guideRadiusRatio = config.guideRadiusRatio,
            guideColor = config.guideColor,
            noteColorTop = config.noteColorTop,
            noteColorMiddle = config.noteColorMiddle,
            noteColorBottom = config.noteColorBottom
        )
        _visualConfig.value = safeConfig
        prefs.edit()
            .putBoolean(KEY_SHOW_KEY_NUMBERS, safeConfig.showKeyNumbers)
            .putBoolean(KEY_SHOW_FALLING_NOTES, safeConfig.showFallingNotes)
            .putBoolean(KEY_SHOW_APPROACH_CIRCLES, safeConfig.showApproachCircles)
            .putBoolean(KEY_SHOW_JUST_EFFECT, safeConfig.showJustEffect)
            .putFloat(KEY_GUIDE_RADIUS_RATIO, safeConfig.guideRadiusRatio)
            .putInt(KEY_GUIDE_COLOR, safeConfig.guideColor)
            .putInt(KEY_NOTE_COLOR_TOP, safeConfig.noteColorTop)
            .putInt(KEY_NOTE_COLOR_MIDDLE, safeConfig.noteColorMiddle)
            .putInt(KEY_NOTE_COLOR_BOTTOM, safeConfig.noteColorBottom)
            .apply()
    }



    override suspend fun saveMidiMappingSettings(settings: com.onigiri.keycue.model.MidiMappingSettings) {
        val safeOctave = settings.manualBaseOctave.coerceIn(2, 6)
        val safeSettings = settings.copy(manualBaseOctave = safeOctave)
        _midiMappingSettings.value = safeSettings
        prefs.edit()
            .putString(KEY_MIDI_MAPPING_MODE, safeSettings.mode.name)
            .putString(KEY_MIDI_MANUAL_ROOT, safeSettings.manualRoot.name)
            .putString(KEY_MIDI_MANUAL_SCALE, safeSettings.manualScale.name)
            .putInt(KEY_MIDI_MANUAL_BASE_OCTAVE, safeSettings.manualBaseOctave)
            .apply()
    }

    private fun applyPlaybackConfig(config: PlaybackConfig) {
        _playbackConfig.value = config
        _speed.value = config.speed
        _leadTimeMs.value = config.leadTimeMs
        _highlightTimeMs.value = config.highlightTimeMs
        _countdownMs.value = config.countdownMs
    }
}

/**
 * 単体テストおよびプレビュー表示用のインメモリ [SettingsRepository] 実装。
 */
class InMemorySettingsRepository(
    initialSongUri: String? = null,
    initialOverlayPosition: Pair<Int, Int>? = null,
    initialOverlayNormalized: NormalizedPoint? = null,
    initialFitProfile: com.onigiri.keycue.model.FitProfile? = null,
    initialSpeed: Float = 1.0f,
    initialLeadTimeMs: Long = 700L,
    initialHighlightTimeMs: Long = 500L,
    initialCountdownMs: Long = 3000L,
    initialVisualConfig: com.onigiri.keycue.model.VisualConfig = com.onigiri.keycue.model.VisualConfig(),
    initialMidiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings = com.onigiri.keycue.model.MidiMappingSettings()
) : SettingsRepository {

    companion object {
        val instance: InMemorySettingsRepository by lazy { InMemorySettingsRepository() }
    }

    private val _lastSongUri = MutableStateFlow(initialSongUri)
    override val lastSongUri: StateFlow<String?> = _lastSongUri.asStateFlow()

    private val _fitProfile = MutableStateFlow(initialFitProfile)
    override val fitProfile: StateFlow<com.onigiri.keycue.model.FitProfile?> = _fitProfile.asStateFlow()

    private val _speed = MutableStateFlow(initialSpeed.coerceIn(0.25f, 2.0f))
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _leadTimeMs = MutableStateFlow(initialLeadTimeMs.coerceIn(300L, 2000L))
    override val leadTimeMs: StateFlow<Long> = _leadTimeMs.asStateFlow()

    private val _highlightTimeMs = MutableStateFlow(initialHighlightTimeMs.coerceIn(100L, 1000L))
    override val highlightTimeMs: StateFlow<Long> = _highlightTimeMs.asStateFlow()

    private val _countdownMs = MutableStateFlow(initialCountdownMs.coerceIn(0L, 5000L))
    override val countdownMs: StateFlow<Long> = _countdownMs.asStateFlow()

    private val _playbackConfig = MutableStateFlow(
        PlaybackConfig(
            speed = _speed.value,
            leadTimeMs = _leadTimeMs.value,
            highlightTimeMs = _highlightTimeMs.value,
            countdownMs = _countdownMs.value
        )
    )
    override val playbackConfig: StateFlow<PlaybackConfig> = _playbackConfig.asStateFlow()

    private val _overlayPositionNormalized = MutableStateFlow(initialOverlayNormalized)
    override val overlayPositionNormalized: StateFlow<NormalizedPoint?> = _overlayPositionNormalized.asStateFlow()

    private val _overlayPosition = MutableStateFlow(initialOverlayPosition)
    override val overlayPosition: StateFlow<Pair<Int, Int>?> = _overlayPosition.asStateFlow()

    private val _visualConfig = MutableStateFlow(initialVisualConfig)
    override val visualConfig: StateFlow<com.onigiri.keycue.model.VisualConfig> = _visualConfig.asStateFlow()

    private val _midiMappingSettings = MutableStateFlow(initialMidiMappingSettings)
    override val midiMappingSettings: StateFlow<com.onigiri.keycue.model.MidiMappingSettings> = _midiMappingSettings.asStateFlow()

    override suspend fun saveLastSongUri(uri: String?) {
        _lastSongUri.value = uri
    }

    override suspend fun saveSpeed(speed: Float) {
        savePlaybackConfig(_playbackConfig.value.copy(speed = speed))
    }

    override suspend fun saveLeadTimeMs(leadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(leadTimeMs = leadTimeMs))
    }

    override suspend fun saveHighlightTimeMs(highlightTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(highlightTimeMs = highlightTimeMs))
    }

    override suspend fun saveCountdownMs(countdownMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(countdownMs = countdownMs))
    }

    override suspend fun savePlaybackConfig(config: PlaybackConfig) {
        applyPlaybackConfig(config.normalized())
    }

    override suspend fun saveOverlayPositionNormalized(x: Float, y: Float) {
        _overlayPositionNormalized.value = NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    override suspend fun saveOverlayPosition(x: Int, y: Int) {
        _overlayPosition.value = Pair(x, y)
    }

    override suspend fun saveFitProfile(profile: com.onigiri.keycue.model.FitProfile?) {
        _fitProfile.value = profile
    }

    override suspend fun saveVisualConfig(config: com.onigiri.keycue.model.VisualConfig) {
        _visualConfig.value = com.onigiri.keycue.model.VisualConfig.safe(
            showKeyNumbers = config.showKeyNumbers,
            showFallingNotes = config.showFallingNotes,
            showApproachCircles = config.showApproachCircles,
            showJustEffect = config.showJustEffect,
            guideRadiusRatio = config.guideRadiusRatio,
            guideColor = config.guideColor,
            noteColorTop = config.noteColorTop,
            noteColorMiddle = config.noteColorMiddle,
            noteColorBottom = config.noteColorBottom
        )
    }



    override suspend fun saveMidiMappingSettings(settings: com.onigiri.keycue.model.MidiMappingSettings) {
        val safeOctave = settings.manualBaseOctave.coerceIn(2, 6)
        _midiMappingSettings.value = settings.copy(manualBaseOctave = safeOctave)
    }

    private fun applyPlaybackConfig(config: PlaybackConfig) {
        _playbackConfig.value = config
        _speed.value = config.speed
        _leadTimeMs.value = config.leadTimeMs
        _highlightTimeMs.value = config.highlightTimeMs
        _countdownMs.value = config.countdownMs
    }
}
