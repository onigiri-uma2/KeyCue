package com.onigiri.keycue.data

import android.content.Context
import android.content.SharedPreferences
import com.onigiri.keycue.model.ControlOverlayConfig
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

    /** ノート先読み時間（ミリ秒） */
    val noteLeadTimeMs: StateFlow<Long>
    suspend fun saveNoteLeadTimeMs(noteLeadTimeMs: Long)

    /** タイミングサークル先読み時間（ミリ秒） */
    val approachCircleLeadTimeMs: StateFlow<Long>
    suspend fun saveApproachCircleLeadTimeMs(approachCircleLeadTimeMs: Long)

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

    /** 画面上のキー配置プロファイル (FitProfile) */
    val fitProfile: StateFlow<com.onigiri.keycue.model.FitProfile?>
    suspend fun saveFitProfile(profile: com.onigiri.keycue.model.FitProfile?)

    /** 演奏ガイドおよびノートのビジュアル設定 (VisualConfig) */
    val visualConfig: StateFlow<com.onigiri.keycue.model.VisualConfig>
    suspend fun saveVisualConfig(config: com.onigiri.keycue.model.VisualConfig)
    suspend fun updateVisualConfig(transform: (com.onigiri.keycue.model.VisualConfig) -> com.onigiri.keycue.model.VisualConfig) =
        saveVisualConfig(transform(visualConfig.value))

    suspend fun saveShowGuideLabels(show: Boolean) = updateVisualConfig { it.copy(showGuideLabels = show) }
    suspend fun saveShowFallingNotes(show: Boolean) = updateVisualConfig { it.copy(showFallingNotes = show) }
    suspend fun saveShowApproachCircles(show: Boolean) = updateVisualConfig { it.copy(showApproachCircles = show) }
    suspend fun saveShowRepeatCountBadge(show: Boolean) = updateVisualConfig { it.copy(showRepeatCountBadge = show) }
    suspend fun saveShowJustEffect(show: Boolean) = updateVisualConfig { it.copy(showJustEffect = show) }
    suspend fun saveGuideRadiusRatio(ratio: Float) = updateVisualConfig {
        it.copy(guideRadiusRatio = ratio.coerceIn(com.onigiri.keycue.model.VisualConfig.MIN_GUIDE_RADIUS_RATIO, com.onigiri.keycue.model.VisualConfig.MAX_GUIDE_RADIUS_RATIO))
    }
    suspend fun saveGuideColor(color: Int) = updateVisualConfig { it.copy(guideColor = color) }
    suspend fun saveNoteColorTop(color: Int) = updateVisualConfig { it.copy(noteColorTop = color) }
    suspend fun saveNoteColorMiddle(color: Int) = updateVisualConfig { it.copy(noteColorMiddle = color) }
    suspend fun saveNoteColorBottom(color: Int) = updateVisualConfig { it.copy(noteColorBottom = color) }
    suspend fun saveShowChordLinks(show: Boolean) = updateVisualConfig { it.copy(showChordLinks = show) }
    suspend fun saveShowChordHalos(show: Boolean) = updateVisualConfig { it.copy(showChordHalos = show) }
    suspend fun saveChordStrokeWidthDp(widthDp: Float) = updateVisualConfig {
        it.copy(
            chordStrokeWidthDp = widthDp.coerceIn(
                com.onigiri.keycue.model.VisualConfig.MIN_CHORD_STROKE_WIDTH_DP,
                com.onigiri.keycue.model.VisualConfig.MAX_CHORD_STROKE_WIDTH_DP
            )
        )
    }
    suspend fun saveChordStrokeAlphaPercent(percent: Int) = updateVisualConfig {
        it.copy(
            chordStrokeAlphaPercent = percent.coerceIn(
                com.onigiri.keycue.model.VisualConfig.MIN_CHORD_STROKE_ALPHA_PERCENT,
                com.onigiri.keycue.model.VisualConfig.MAX_CHORD_STROKE_ALPHA_PERCENT
            )
        )
    }
    suspend fun saveChordHaloFillAlphaPercent(percent: Int) = updateVisualConfig {
        it.copy(
            chordHaloFillAlphaPercent = percent.coerceIn(
                com.onigiri.keycue.model.VisualConfig.MIN_CHORD_HALO_FILL_ALPHA_PERCENT,
                com.onigiri.keycue.model.VisualConfig.MAX_CHORD_HALO_FILL_ALPHA_PERCENT
            )
        )
    }

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

    /** フローティング操作コントローラーの表示設定 (ControlOverlayConfig) */
    val controlOverlayConfig: StateFlow<ControlOverlayConfig>
    suspend fun saveControlOverlayConfig(config: ControlOverlayConfig)
    suspend fun updateControlOverlayConfig(transform: (ControlOverlayConfig) -> ControlOverlayConfig) =
        saveControlOverlayConfig(transform(controlOverlayConfig.value))
}

/**
 * Android標準のSharedPreferencesを利用した永続化 SettingsRepository 実装。
 */
class SharedPreferencesSettingsRepository internal constructor(
    private val prefs: SharedPreferences
) : SettingsRepository {

    constructor(
        context: Context,
        prefsName: String = "keycue_settings"
    ) : this(context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE))

    companion object {
        private const val KEY_LAST_SONG_URI = "last_song_uri"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_NOTE_LEAD_TIME = "lead_time_ms"
        private const val KEY_APPROACH_CIRCLE_LEAD_TIME = "highlight_time_ms"
        private const val KEY_COUNTDOWN = "countdown_ms"
        private const val KEY_OVERLAY_NORM_X = "overlay_norm_x"
        private const val KEY_OVERLAY_NORM_Y = "overlay_norm_y"
        private const val KEY_OVERLAY_PIXEL_X = "overlay_pixel_x"
        private const val KEY_OVERLAY_PIXEL_Y = "overlay_pixel_y"
        private const val KEY_FIT_PROFILE = "fit_profile_json"
        private const val KEY_SHOW_GUIDE_LABELS = "show_key_numbers"
        private const val KEY_SHOW_FALLING_NOTES = "show_falling_notes"
        private const val KEY_SHOW_APPROACH_CIRCLES = "show_approach_circles"
        private const val KEY_SHOW_REPEAT_COUNT_BADGE = "show_repeat_count_badge"
        private const val KEY_SHOW_JUST_EFFECT = "show_just_effect"
        private const val KEY_GUIDE_RADIUS_RATIO = "guide_radius_ratio"
        private const val KEY_GUIDE_COLOR = "guide_color"
        private const val KEY_NOTE_COLOR_TOP = "note_color_top"
        private const val KEY_NOTE_COLOR_MIDDLE = "note_color_middle"
        private const val KEY_NOTE_COLOR_BOTTOM = "note_color_bottom"
        private const val KEY_SHOW_CHORD_LINKS = "show_chord_links"
        private const val KEY_SHOW_CHORD_HALOS = "show_chord_halos"
        private const val KEY_CHORD_STROKE_WIDTH_DP = "chord_stroke_width_dp"
        private const val KEY_CHORD_STROKE_ALPHA_PERCENT = "chord_stroke_alpha_percent"
        private const val KEY_CHORD_HALO_FILL_ALPHA_PERCENT = "chord_halo_fill_alpha_percent"
        private const val KEY_MIDI_MAPPING_MODE = "midi_mapping_mode"
        private const val KEY_MIDI_MANUAL_ROOT = "midi_manual_root"
        private const val KEY_MIDI_MANUAL_SCALE = "midi_manual_scale"
        private const val KEY_MIDI_MANUAL_BASE_OCTAVE = "midi_manual_base_octave"
        private const val KEY_CONTROL_SHOW_SONG_INFO = "control_show_song_info"
        private const val KEY_CONTROL_SHOW_SEEK_BAR = "control_show_seek_bar"
        private const val KEY_CONTROL_SHOW_PLAYBACK_CONTROLS = "control_show_playback_controls"
        private const val KEY_CONTROL_SHOW_LOOP_CONTROLS = "control_show_loop_controls"
        private const val KEY_CONTROL_SHOW_SPEED_CONTROL = "control_show_speed_control"
        private const val KEY_CONTROL_SHOW_NOTE_LEAD_TIME = "control_show_note_lead_time"
        private const val KEY_CONTROL_SHOW_CIRCLE_LEAD_TIME = "control_show_circle_lead_time"
        private const val KEY_CONTROL_SHOW_SONG_SELECTION = "control_show_song_selection"
        private const val KEY_CONTROL_SHOW_FITTING = "control_show_fitting"
        private const val KEY_CONTROL_SHOW_GUIDE_TOGGLE = "control_show_guide_toggle"
        private const val KEY_CONTROL_SHOW_COUNTDOWN_CONTROL = "control_show_countdown_control"
        private const val KEY_CONTROL_SHOW_GUIDE_QUICK_TOGGLES = "control_show_guide_quick_toggles"
        private const val KEY_CONTROL_SHOW_RECENT_SONGS = "control_show_recent_songs"

        @Volatile
        private var instance: SharedPreferencesSettingsRepository? = null

        fun getInstance(context: Context): SharedPreferencesSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesSettingsRepository(context).also { instance = it }
            }
        }
    }

    private val _controlOverlayConfig = MutableStateFlow(
        ControlOverlayConfig(
            showSongInfo = prefs.getBoolean(KEY_CONTROL_SHOW_SONG_INFO, true),
            showSeekBar = prefs.getBoolean(KEY_CONTROL_SHOW_SEEK_BAR, true),
            showPlaybackControls = prefs.getBoolean(KEY_CONTROL_SHOW_PLAYBACK_CONTROLS, true),
            showLoopControls = prefs.getBoolean(KEY_CONTROL_SHOW_LOOP_CONTROLS, true),
            showSpeedControl = prefs.getBoolean(KEY_CONTROL_SHOW_SPEED_CONTROL, true),
            showNoteLeadTimeControl = prefs.getBoolean(KEY_CONTROL_SHOW_NOTE_LEAD_TIME, true),
            showCircleLeadTimeControl = prefs.getBoolean(KEY_CONTROL_SHOW_CIRCLE_LEAD_TIME, true),
            showSongSelection = prefs.getBoolean(KEY_CONTROL_SHOW_SONG_SELECTION, true),
            showFitting = prefs.getBoolean(KEY_CONTROL_SHOW_FITTING, true),
            showGuideToggle = prefs.getBoolean(KEY_CONTROL_SHOW_GUIDE_TOGGLE, true),
            showCountdownControl = prefs.getBoolean(KEY_CONTROL_SHOW_COUNTDOWN_CONTROL, true),
            showGuideQuickToggles = prefs.getBoolean(KEY_CONTROL_SHOW_GUIDE_QUICK_TOGGLES, true),
            showRecentSongs = prefs.getBoolean(KEY_CONTROL_SHOW_RECENT_SONGS, false)
        )
    )
    override val controlOverlayConfig: StateFlow<ControlOverlayConfig> = _controlOverlayConfig.asStateFlow()

    private val _lastSongUri = MutableStateFlow(prefs.getString(KEY_LAST_SONG_URI, null))
    override val lastSongUri: StateFlow<String?> = _lastSongUri.asStateFlow()

    private val _initialConfig = PlaybackConfig.normalize(
        speed = prefs.getFloat(KEY_SPEED, 1.0f),
        noteLeadTimeMs = prefs.getLong(KEY_NOTE_LEAD_TIME, PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS),
        approachCircleLeadTimeMs = prefs.getLong(KEY_APPROACH_CIRCLE_LEAD_TIME, PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS),
        countdownMs = prefs.getLong(KEY_COUNTDOWN, 3000L)
    )

    private val _speed = MutableStateFlow(_initialConfig.speed)
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _noteLeadTimeMs = MutableStateFlow(_initialConfig.noteLeadTimeMs)
    override val noteLeadTimeMs: StateFlow<Long> = _noteLeadTimeMs.asStateFlow()

    private val _approachCircleLeadTimeMs = MutableStateFlow(_initialConfig.approachCircleLeadTimeMs)
    override val approachCircleLeadTimeMs: StateFlow<Long> = _approachCircleLeadTimeMs.asStateFlow()

    private val _countdownMs = MutableStateFlow(_initialConfig.countdownMs)
    override val countdownMs: StateFlow<Long> = _countdownMs.asStateFlow()

    private val _playbackConfig = MutableStateFlow(_initialConfig)
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
            showGuideLabels = prefs.getBoolean(KEY_SHOW_GUIDE_LABELS, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_GUIDE_LABELS),
            showFallingNotes = prefs.getBoolean(KEY_SHOW_FALLING_NOTES, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_FALLING_NOTES),
            showApproachCircles = prefs.getBoolean(KEY_SHOW_APPROACH_CIRCLES, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_APPROACH_CIRCLES),
            showRepeatCountBadge = prefs.getBoolean(KEY_SHOW_REPEAT_COUNT_BADGE, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_REPEAT_COUNT_BADGE),
            showJustEffect = prefs.getBoolean(KEY_SHOW_JUST_EFFECT, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_JUST_EFFECT),
            guideRadiusRatio = prefs.getFloat(KEY_GUIDE_RADIUS_RATIO, _fitProfile.value?.keyRadiusRatio ?: com.onigiri.keycue.model.VisualConfig.DEFAULT_GUIDE_RADIUS_RATIO),
            guideColor = prefs.getInt(KEY_GUIDE_COLOR, com.onigiri.keycue.model.VisualConfig.DEFAULT_GUIDE_COLOR),
            noteColorTop = prefs.getInt(KEY_NOTE_COLOR_TOP, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_TOP),
            noteColorMiddle = prefs.getInt(KEY_NOTE_COLOR_MIDDLE, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_MIDDLE),
            noteColorBottom = prefs.getInt(KEY_NOTE_COLOR_BOTTOM, com.onigiri.keycue.model.VisualConfig.DEFAULT_NOTE_COLOR_BOTTOM),
            showChordLinks = prefs.getBoolean(KEY_SHOW_CHORD_LINKS, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_CHORD_LINKS),
            showChordHalos = prefs.getBoolean(KEY_SHOW_CHORD_HALOS, com.onigiri.keycue.model.VisualConfig.DEFAULT_SHOW_CHORD_HALOS),
            chordStrokeWidthDp = prefs.getFloat(KEY_CHORD_STROKE_WIDTH_DP, com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_STROKE_WIDTH_DP),
            chordStrokeAlphaPercent = prefs.getInt(KEY_CHORD_STROKE_ALPHA_PERCENT, com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_STROKE_ALPHA_PERCENT),
            chordHaloFillAlphaPercent = prefs.getInt(KEY_CHORD_HALO_FILL_ALPHA_PERCENT, com.onigiri.keycue.model.VisualConfig.DEFAULT_CHORD_HALO_FILL_ALPHA_PERCENT)
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

    override suspend fun saveNoteLeadTimeMs(noteLeadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(noteLeadTimeMs = noteLeadTimeMs))
    }

    override suspend fun saveApproachCircleLeadTimeMs(approachCircleLeadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(approachCircleLeadTimeMs = approachCircleLeadTimeMs))
    }

    override suspend fun saveCountdownMs(countdownMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(countdownMs = countdownMs))
    }

    override suspend fun savePlaybackConfig(config: PlaybackConfig) {
        val normalized = config.normalized()
        applyPlaybackConfig(normalized)

        prefs.edit()
            .putFloat(KEY_SPEED, normalized.speed)
            .putLong(KEY_NOTE_LEAD_TIME, normalized.noteLeadTimeMs)
            .putLong(KEY_APPROACH_CIRCLE_LEAD_TIME, normalized.approachCircleLeadTimeMs)
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
            showGuideLabels = config.showGuideLabels,
            showFallingNotes = config.showFallingNotes,
            showApproachCircles = config.showApproachCircles,
            showRepeatCountBadge = config.showRepeatCountBadge,
            showJustEffect = config.showJustEffect,
            guideRadiusRatio = config.guideRadiusRatio,
            guideColor = config.guideColor,
            noteColorTop = config.noteColorTop,
            noteColorMiddle = config.noteColorMiddle,
            noteColorBottom = config.noteColorBottom,
            showChordLinks = config.showChordLinks,
            showChordHalos = config.showChordHalos,
            chordStrokeWidthDp = config.chordStrokeWidthDp,
            chordStrokeAlphaPercent = config.chordStrokeAlphaPercent,
            chordHaloFillAlphaPercent = config.chordHaloFillAlphaPercent
        )
        _visualConfig.value = safeConfig
        prefs.edit()
            .putBoolean(KEY_SHOW_GUIDE_LABELS, safeConfig.showGuideLabels)
            .putBoolean(KEY_SHOW_FALLING_NOTES, safeConfig.showFallingNotes)
            .putBoolean(KEY_SHOW_APPROACH_CIRCLES, safeConfig.showApproachCircles)
            .putBoolean(KEY_SHOW_REPEAT_COUNT_BADGE, safeConfig.showRepeatCountBadge)
            .putBoolean(KEY_SHOW_JUST_EFFECT, safeConfig.showJustEffect)
            .putFloat(KEY_GUIDE_RADIUS_RATIO, safeConfig.guideRadiusRatio)
            .putInt(KEY_GUIDE_COLOR, safeConfig.guideColor)
            .putInt(KEY_NOTE_COLOR_TOP, safeConfig.noteColorTop)
            .putInt(KEY_NOTE_COLOR_MIDDLE, safeConfig.noteColorMiddle)
            .putInt(KEY_NOTE_COLOR_BOTTOM, safeConfig.noteColorBottom)
            .putBoolean(KEY_SHOW_CHORD_LINKS, safeConfig.showChordLinks)
            .putBoolean(KEY_SHOW_CHORD_HALOS, safeConfig.showChordHalos)
            .putFloat(KEY_CHORD_STROKE_WIDTH_DP, safeConfig.chordStrokeWidthDp)
            .putInt(KEY_CHORD_STROKE_ALPHA_PERCENT, safeConfig.chordStrokeAlphaPercent)
            .putInt(KEY_CHORD_HALO_FILL_ALPHA_PERCENT, safeConfig.chordHaloFillAlphaPercent)
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

    override suspend fun saveControlOverlayConfig(config: ControlOverlayConfig) {
        _controlOverlayConfig.value = config
        prefs.edit()
            .putBoolean(KEY_CONTROL_SHOW_SONG_INFO, config.showSongInfo)
            .putBoolean(KEY_CONTROL_SHOW_SEEK_BAR, config.showSeekBar)
            .putBoolean(KEY_CONTROL_SHOW_PLAYBACK_CONTROLS, config.showPlaybackControls)
            .putBoolean(KEY_CONTROL_SHOW_LOOP_CONTROLS, config.showLoopControls)
            .putBoolean(KEY_CONTROL_SHOW_SPEED_CONTROL, config.showSpeedControl)
            .putBoolean(KEY_CONTROL_SHOW_NOTE_LEAD_TIME, config.showNoteLeadTimeControl)
            .putBoolean(KEY_CONTROL_SHOW_CIRCLE_LEAD_TIME, config.showCircleLeadTimeControl)
            .putBoolean(KEY_CONTROL_SHOW_SONG_SELECTION, config.showSongSelection)
            .putBoolean(KEY_CONTROL_SHOW_FITTING, config.showFitting)
            .putBoolean(KEY_CONTROL_SHOW_GUIDE_TOGGLE, config.showGuideToggle)
            .putBoolean(KEY_CONTROL_SHOW_COUNTDOWN_CONTROL, config.showCountdownControl)
            .putBoolean(KEY_CONTROL_SHOW_GUIDE_QUICK_TOGGLES, config.showGuideQuickToggles)
            .putBoolean(KEY_CONTROL_SHOW_RECENT_SONGS, config.showRecentSongs)
            .apply()
    }

    private fun applyPlaybackConfig(config: PlaybackConfig) {
        _playbackConfig.value = config
        _speed.value = config.speed
        _noteLeadTimeMs.value = config.noteLeadTimeMs
        _approachCircleLeadTimeMs.value = config.approachCircleLeadTimeMs
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
    initialNoteLeadTimeMs: Long = PlaybackConfig.DEFAULT_NOTE_LEAD_TIME_MS,
    initialApproachCircleLeadTimeMs: Long = PlaybackConfig.DEFAULT_APPROACH_CIRCLE_LEAD_TIME_MS,
    initialCountdownMs: Long = 3000L,
    initialVisualConfig: com.onigiri.keycue.model.VisualConfig = com.onigiri.keycue.model.VisualConfig(),
    initialMidiMappingSettings: com.onigiri.keycue.model.MidiMappingSettings = com.onigiri.keycue.model.MidiMappingSettings(),
    initialControlOverlayConfig: ControlOverlayConfig = ControlOverlayConfig()
) : SettingsRepository {

    companion object {
        val instance: InMemorySettingsRepository by lazy { InMemorySettingsRepository() }
    }

    private val _lastSongUri = MutableStateFlow(initialSongUri)
    override val lastSongUri: StateFlow<String?> = _lastSongUri.asStateFlow()

    private val _fitProfile = MutableStateFlow(initialFitProfile)
    override val fitProfile: StateFlow<com.onigiri.keycue.model.FitProfile?> = _fitProfile.asStateFlow()

    private val _initialConfig = PlaybackConfig.normalize(
        speed = initialSpeed,
        noteLeadTimeMs = initialNoteLeadTimeMs,
        approachCircleLeadTimeMs = initialApproachCircleLeadTimeMs,
        countdownMs = initialCountdownMs
    )

    private val _speed = MutableStateFlow(_initialConfig.speed)
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _noteLeadTimeMs = MutableStateFlow(_initialConfig.noteLeadTimeMs)
    override val noteLeadTimeMs: StateFlow<Long> = _noteLeadTimeMs.asStateFlow()

    private val _approachCircleLeadTimeMs = MutableStateFlow(_initialConfig.approachCircleLeadTimeMs)
    override val approachCircleLeadTimeMs: StateFlow<Long> = _approachCircleLeadTimeMs.asStateFlow()

    private val _countdownMs = MutableStateFlow(_initialConfig.countdownMs)
    override val countdownMs: StateFlow<Long> = _countdownMs.asStateFlow()

    private val _playbackConfig = MutableStateFlow(_initialConfig)
    override val playbackConfig: StateFlow<PlaybackConfig> = _playbackConfig.asStateFlow()

    private val _overlayPositionNormalized = MutableStateFlow(initialOverlayNormalized)
    override val overlayPositionNormalized: StateFlow<NormalizedPoint?> = _overlayPositionNormalized.asStateFlow()

    private val _overlayPosition = MutableStateFlow(initialOverlayPosition)
    override val overlayPosition: StateFlow<Pair<Int, Int>?> = _overlayPosition.asStateFlow()

    private val _visualConfig = MutableStateFlow(initialVisualConfig)
    override val visualConfig: StateFlow<com.onigiri.keycue.model.VisualConfig> = _visualConfig.asStateFlow()

    private val _midiMappingSettings = MutableStateFlow(initialMidiMappingSettings)
    override val midiMappingSettings: StateFlow<com.onigiri.keycue.model.MidiMappingSettings> = _midiMappingSettings.asStateFlow()

    private val _controlOverlayConfig = MutableStateFlow(initialControlOverlayConfig)
    override val controlOverlayConfig: StateFlow<ControlOverlayConfig> = _controlOverlayConfig.asStateFlow()

    override suspend fun saveLastSongUri(uri: String?) {
        _lastSongUri.value = uri
    }

    override suspend fun saveSpeed(speed: Float) {
        savePlaybackConfig(_playbackConfig.value.copy(speed = speed))
    }

    override suspend fun saveNoteLeadTimeMs(noteLeadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(noteLeadTimeMs = noteLeadTimeMs))
    }

    override suspend fun saveApproachCircleLeadTimeMs(approachCircleLeadTimeMs: Long) {
        savePlaybackConfig(_playbackConfig.value.copy(approachCircleLeadTimeMs = approachCircleLeadTimeMs))
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
            showGuideLabels = config.showGuideLabels,
            showFallingNotes = config.showFallingNotes,
            showApproachCircles = config.showApproachCircles,
            showRepeatCountBadge = config.showRepeatCountBadge,
            showJustEffect = config.showJustEffect,
            guideRadiusRatio = config.guideRadiusRatio,
            guideColor = config.guideColor,
            noteColorTop = config.noteColorTop,
            noteColorMiddle = config.noteColorMiddle,
            noteColorBottom = config.noteColorBottom,
            showChordLinks = config.showChordLinks,
            showChordHalos = config.showChordHalos,
            chordStrokeWidthDp = config.chordStrokeWidthDp,
            chordStrokeAlphaPercent = config.chordStrokeAlphaPercent,
            chordHaloFillAlphaPercent = config.chordHaloFillAlphaPercent
        )
    }

    override suspend fun saveMidiMappingSettings(settings: com.onigiri.keycue.model.MidiMappingSettings) {
        val safeOctave = settings.manualBaseOctave.coerceIn(2, 6)
        _midiMappingSettings.value = settings.copy(manualBaseOctave = safeOctave)
    }

    override suspend fun saveControlOverlayConfig(config: ControlOverlayConfig) {
        _controlOverlayConfig.value = config
    }

    private fun applyPlaybackConfig(config: PlaybackConfig) {
        _playbackConfig.value = config
        _speed.value = config.speed
        _noteLeadTimeMs.value = config.noteLeadTimeMs
        _approachCircleLeadTimeMs.value = config.approachCircleLeadTimeMs
        _countdownMs.value = config.countdownMs
    }
}
