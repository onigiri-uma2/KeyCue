package com.onigiri.keycue.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.onigiri.keycue.app.MainActivity
import com.onigiri.keycue.data.InMemoryPlaybackSessionRepository
import com.onigiri.keycue.data.PlaybackSessionRepository
import com.onigiri.keycue.data.SettingsRepository
import com.onigiri.keycue.data.SharedPreferencesSettingsRepository
import com.onigiri.keycue.playback.NoteScheduler
import com.onigiri.keycue.playback.PlaybackEngine
import com.onigiri.keycue.playback.PlaybackState
import android.net.Uri
import com.onigiri.keycue.model.MetronomeConfig
import com.onigiri.keycue.model.PlaybackSession
import com.onigiri.keycue.model.RecentSongEntry
import com.onigiri.keycue.model.SongData
import com.onigiri.keycue.audio.MetronomeScheduler
import com.onigiri.keycue.audio.MetronomeSoundPlayer
import com.onigiri.keycue.audio.MetronomeTimingResolver
import com.onigiri.keycue.audio.SoundPoolMetronomeSoundPlayer
import com.onigiri.keycue.song.SongSelectionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ゲーム画面上で演奏ガイドおよびコントロールUIを常駐表示するための Foreground Service。
 *
 * 主な責務:
 * - **ライフサイクル管理**: フォアグラウンド通知を表示し、バックグラウンドでのプロセス破棄を防ぎながらオーバーレイを維持します。
 * - **再生制御連携**: [PlaybackEngine] の再生状態・時間進行を購読し、[GuideOverlayView] へフレーム情報をリアルタイムに供給します。
 * - **ウィンドウ制御**: [OverlayWindowController] を通じてガイド、コントロールパネル、フィッティング画面の表示・破棄を一元管理します。
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.onigiri.keycue.overlay.ACTION_START"
        const val ACTION_STOP = "com.onigiri.keycue.overlay.ACTION_STOP"

        private var isRunning = false
        private var instance: OverlayService? = null

        fun isServiceRunning(): Boolean = isRunning

        /**
         * 設定画面（MainActivity）表示中のオーバーレイ一時非表示・再表示を設定する。
         */
        fun setOverlayVisibleForSettings(visible: Boolean) {
            instance?.let { service ->
                if (visible) {
                    service.windowController?.restoreAfterSettings()
                } else {
                    service.windowController?.hideForSettings()
                }
            }
        }

        /**
         * OverlayServiceを起動する。
         */
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * OverlayServiceを停止するIntentを生成する。
         */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            }
        }

        /**
         * OverlayServiceを停止する。
         */
        fun stop(context: Context) {
            val intent = createStopIntent(context)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var settingsRepository: SettingsRepository
    private val sessionRepository: PlaybackSessionRepository = InMemoryPlaybackSessionRepository.instance

    private val playbackEngine: PlaybackEngine = PlaybackEngine()
    private val noteScheduler: NoteScheduler = NoteScheduler()

    private var windowController: OverlayWindowController? = null
    private var loadedSong: SongData? = null
    private var isFrameLoopRunning = false
    private var isRecentSongLoading = false

    private var metronomeSoundPlayer: MetronomeSoundPlayer? = null
    private var metronomeScheduler: MetronomeScheduler? = null
    private var timelineRequestGeneration: Long = 0L
    private var timelineResolutionJob: Job? = null

    // ディスプレイの垂直同期信号 (VSYNC) に合わせて60fps/120fpsでフレーム描画を駆動するChoreographerコールバック
    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFrameLoopRunning) return

            renderCurrentFrame()
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        OverlayNotificationFactory.createNotificationChannel(this)

        settingsRepository = SharedPreferencesSettingsRepository.getInstance(this)

        val soundPlayer = SoundPoolMetronomeSoundPlayer(this)
        metronomeSoundPlayer = soundPlayer
        metronomeScheduler = MetronomeScheduler(
            timeProvider = { playbackEngine.getCurrentPositionMs(allowNegative = false) },
            speedProvider = { playbackEngine.targetPlaybackSpeed },
            isPlayingProvider = { playbackEngine.state.value is PlaybackState.Playing },
            durationProvider = { loadedSong?.durationMs ?: 0L },
            loopBoundsProvider = {
                if (playbackEngine.isLoopValid()) {
                    playbackEngine.loopStartMs to playbackEngine.loopEndMs
                } else {
                    null to null
                }
            },
            soundPlayer = soundPlayer,
            scope = serviceScope
        )

        playbackEngine.onLoopRewound = { targetPos ->
            metronomeScheduler?.onLoopRewound(targetPos)
        }

        sessionRepository.currentSession.value?.song?.let { song ->
            metronomeScheduler?.updateTimingMetadata(song.timingMetadata)
        }
        metronomeScheduler?.updateConfig(settingsRepository.metronomeConfig.value)

        windowController = OverlayWindowController(
            context = this,
            onOpenApp = { openMainActivity() },
            onCloseOverlay = { stopSelf() },
            onPositionChanged = { normX, normY, pixelX, pixelY ->
                serviceScope.launch {
                    settingsRepository.saveOverlayPositionNormalized(normX, normY)
                    settingsRepository.saveOverlayPosition(pixelX, pixelY)
                }
            },
            onSelectFile = { handleSelectFile() },
            onPlayPause = { handlePlayPause() },
            onStop = { handleStop() },
            onRestart = { handleRestart() },
            onSeekBack = { handleSeekBack() },
            onSeekForward = { handleSeekForward() },
            onSpeedChange = { newSpeed ->
                serviceScope.launch {
                    settingsRepository.saveSpeed(newSpeed)
                }
            },
            onNoteLeadTimeChange = { newLead ->
                serviceScope.launch {
                    settingsRepository.saveNoteLeadTimeMs(newLead)
                }
            },
            onApproachCircleLeadTimeChange = { newCircle ->
                serviceScope.launch {
                    settingsRepository.saveApproachCircleLeadTimeMs(newCircle)
                }
            },
            onSaveFitProfile = { profile ->
                serviceScope.launch {
                    settingsRepository.saveFitProfile(profile)
                }
            },
            onSeekTo = { pos ->
                playbackEngine.seekTo(pos)
                metronomeScheduler?.onSeek(pos)
                renderCurrentFrame(forceControlUpdate = true)
            },
            onSetLoopStart = {
                playbackEngine.setLoopStart()
                metronomeScheduler?.onLoopBoundsChanged()
                renderCurrentFrame(forceControlUpdate = true)
            },
            onSetLoopEnd = {
                playbackEngine.setLoopEnd()
                metronomeScheduler?.onLoopBoundsChanged()
                renderCurrentFrame(forceControlUpdate = true)
            },
            onClearLoop = {
                playbackEngine.clearLoop()
                metronomeScheduler?.onLoopBoundsChanged()
                renderCurrentFrame(forceControlUpdate = true)
            },
            onCountdownChange = { newMs ->
                serviceScope.launch {
                    settingsRepository.saveCountdownMs(newMs)
                }
            },
            onShowFallingNotesChange = { show ->
                serviceScope.launch {
                    settingsRepository.saveShowFallingNotes(show)
                }
            },
            onShowApproachCirclesChange = { show ->
                serviceScope.launch {
                    settingsRepository.saveShowApproachCircles(show)
                }
            },
            onSelectRecentSong = { entry ->
                handleSelectRecentSong(entry)
            },
            onMetronomeEnabledChange = { enabled ->
                serviceScope.launch {
                    val current = settingsRepository.metronomeConfig.value
                    settingsRepository.saveMetronomeConfig(current.copy(enabled = enabled))
                }
            }
        )

        observePlaybackState()
        observeSettings()
        observePlaybackSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlayAndSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        // GuideOverlayView を先、ControlOverlayView を手前に配置
        windowController?.showGuideOverlay()

        val savedNorm = settingsRepository.overlayPositionNormalized.value
        val savedPixel = settingsRepository.overlayPosition.value
        windowController?.showControlOverlay(
            initialNormX = savedNorm?.x,
            initialNormY = savedNorm?.y,
            initialPixelX = savedPixel?.first,
            initialPixelY = savedPixel?.second
        )

        applyCurrentState()
        renderCurrentFrame()

        return START_NOT_STICKY
    }

    private fun applyCurrentState() {
        val session = sessionRepository.currentSession.value
        applySong(session?.song)
        applyPlaybackConfig(settingsRepository.playbackConfig.value)
        val metroConfig = settingsRepository.metronomeConfig.value
        metronomeScheduler?.updateConfig(metroConfig)
        windowController?.updateMetronomeState(metroConfig.enabled)
        windowController?.updateControlOverlayConfig(settingsRepository.controlOverlayConfig.value)
        windowController?.updateRecentSongs(
            songs = settingsRepository.recentSongs.value,
            currentSongUri = session?.uri?.toString()
        )
        val profile = settingsRepository.fitProfile.value ?: session?.fitProfile
        if (profile != null) windowController?.updateFitProfile(profile)
        updateGuideLabelsForSession(session)
    }

    private fun applySong(song: SongData?) {
        if (song == null || song === loadedSong) return
        playbackEngine.stop()
        playbackEngine.setSong(song)
        noteScheduler.prepare(song.events)
        loadedSong = song

        // メトロノームを即座に安全状態へリセット（古い曲の拍を停止・破棄し、新曲のタイムライン構築完了まで即時ミュート）
        metronomeScheduler?.prepareForSongChange(song.timingMetadata)

        val config = settingsRepository.metronomeConfig.value
        launchTimelineResolution(song, config)
    }

    private fun launchTimelineResolution(song: SongData, config: MetronomeConfig) {
        val generation = ++timelineRequestGeneration
        timelineResolutionJob?.cancel()
        val metadata = song.timingMetadata
        val durationMs = song.durationMs

        // タイムライン構築をバックグラウンドスレッドで実行
        timelineResolutionJob = serviceScope.launch(Dispatchers.Default) {
            val resolvedTimeline = MetronomeTimingResolver.resolveTimeline(
                config = config,
                timingMetadata = metadata,
                durationMs = durationMs
            )
            withContext(Dispatchers.Main) {
                // 要求世代番号が一致している場合のみ適用（曲切替や設定変更途中の古い結果を破棄）
                if (generation == timelineRequestGeneration) {
                    metronomeScheduler?.applyResolvedTimeline(resolvedTimeline, metadata)
                    renderCurrentFrame(forceControlUpdate = true)
                }
            }
        }
    }

    private fun applyPlaybackConfig(config: com.onigiri.keycue.model.PlaybackConfig) {
        playbackEngine.countdownMs = config.countdownMs
        playbackEngine.setSpeed(config.speed)
        metronomeScheduler?.onSpeedChanged()
    }

    private fun handlePlayPause() {
        when (val state = playbackEngine.state.value) {
            is PlaybackState.Playing -> playbackEngine.pause()
            is PlaybackState.CountingDown -> playbackEngine.stop()
            is PlaybackState.Finished -> playbackEngine.restart(skipCountdown = false)
            else -> playbackEngine.play()
        }
    }

    private fun handleStop() {
        playbackEngine.stop()
        metronomeScheduler?.stop()
        renderCurrentFrame(forceControlUpdate = true)
    }

    private fun handleRestart() {
        playbackEngine.restart(skipCountdown = false)
        metronomeScheduler?.onSeek(0L)
    }

    private fun handleSeekBack() {
        playbackEngine.seekBack(10_000L)
        metronomeScheduler?.onSeek(playbackEngine.getCurrentPositionMs())
        renderCurrentFrame(forceControlUpdate = true)
    }

    private fun handleSeekForward() {
        playbackEngine.seekForward(10_000L)
        metronomeScheduler?.onSeek(playbackEngine.getCurrentPositionMs())
        renderCurrentFrame(forceControlUpdate = true)
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            var lastStateKind: kotlin.reflect.KClass<out PlaybackState>? = null
            playbackEngine.state.collect { state ->
                val currentKind = state::class
                if (currentKind != lastStateKind) {
                    lastStateKind = currentKind
                    metronomeScheduler?.onPlaybackStateChanged(state)
                }
                when (state) {
                    is PlaybackState.Playing, is PlaybackState.CountingDown -> {
                        startFrameLoop()
                    }
                    is PlaybackState.Paused, is PlaybackState.Stopped, is PlaybackState.Finished -> {
                        stopFrameLoop()
                        // 停止・一時停止時の最終状態を1回描画
                        renderCurrentFrame()
                    }
                }
            }
        }
    }

    private var sessionCollectJob: Job? = null

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.playbackConfig.collect { config ->
                applyPlaybackConfig(config)
                renderCurrentFrame()
            }
        }
        serviceScope.launch {
            settingsRepository.visualConfig.collect { config ->
                windowController?.updateVisualConfig(config)
                renderCurrentFrame()
            }
        }
        serviceScope.launch {
            settingsRepository.fitProfile.collect { profile ->
                val profileToUse = profile ?: sessionRepository.currentSession.value?.fitProfile
                if (profileToUse != null) {
                    windowController?.updateFitProfile(profileToUse)
                    renderCurrentFrame()
                }
            }
        }
        serviceScope.launch {
            settingsRepository.controlOverlayConfig.collect { config ->
                windowController?.updateControlOverlayConfig(config)
                renderCurrentFrame(forceControlUpdate = true)
            }
        }
        serviceScope.launch {
            settingsRepository.recentSongs.collect { songs ->
                windowController?.updateRecentSongs(
                    songs = songs,
                    currentSongUri = sessionRepository.currentSession.value?.uri?.toString()
                )
            }
        }
        serviceScope.launch {
            settingsRepository.metronomeConfig.collect { config ->
                val scheduler = metronomeScheduler
                if (scheduler != null) {
                    scheduler.updateConfig(config)
                    if (!scheduler.isReady) {
                        // タイムライン構築中に設定が変更された場合は、進行中の非同期解決を破棄し最新設定で再構築
                        val song = loadedSong
                        if (song != null) {
                            launchTimelineResolution(song, config)
                        }
                    } else {
                        // 通常稼働時の設定変更では、未完了の非同期解決があればキャンセルし世代を進める
                        timelineRequestGeneration++
                        timelineResolutionJob?.cancel()
                    }
                }
                renderCurrentFrame(forceControlUpdate = true)
            }
        }
    }

    private fun observePlaybackSession() {
        sessionCollectJob?.cancel()
        sessionCollectJob = serviceScope.launch {
            sessionRepository.currentSession.collect { session ->
                applySong(session?.song)
                val profile = settingsRepository.fitProfile.value ?: session?.fitProfile
                if (profile != null) windowController?.updateFitProfile(profile)
                updateGuideLabelsForSession(session)
                windowController?.updateRecentSongs(
                    songs = settingsRepository.recentSongs.value,
                    currentSongUri = session?.uri?.toString()
                )
                renderCurrentFrame()
            }
        }
    }

    private fun updateGuideLabelsForSession(session: PlaybackSession?) {
        windowController?.updateGuideLabels(resolveGuideLabels(session))
    }

    private fun handleSelectFile() {
        val previousState = playbackEngine.state.value
        val wasPlaying = previousState is PlaybackState.Playing || previousState is PlaybackState.CountingDown
        val previousPosition = playbackEngine.getCurrentPositionMs(allowNegative = false)

        if (wasPlaying) {
            playbackEngine.pause()
        }

        OverlayFilePickerActivity.start(
            context = this,
            listener = object : OverlayFilePickerActivity.OverlayFilePickerListener {
                override fun onPickerStarted() {
                    windowController?.hideForFilePicker()
                }

                override fun onFileSelected(uri: Uri, onComplete: (title: String?) -> Unit) {
                    serviceScope.launch {
                        android.util.Log.d("OverlayService", "onFileSelected: uri=$uri")
                        val coordinator = SongSelectionCoordinator(
                            contentResolver = contentResolver,
                            sessionRepository = sessionRepository,
                            settingsRepository = settingsRepository
                        )
                        val result = coordinator.select(uri, initializeManualFromAuto = true)
                        android.util.Log.d("OverlayService", "coordinator.select result: isSuccess=${result.isSuccess}")
                        windowController?.restoreAfterFilePicker()
                        if (result.isSuccess) {
                            val song = result.getOrNull()
                            onComplete(song?.title ?: "楽曲")
                        } else {
                            android.util.Log.e(
                                "OverlayService",
                                "Failed to load song from URI: $uri",
                                result.exceptionOrNull()
                            )
                            // 失敗時は元の再生状態を復元
                            if (wasPlaying) {
                                playbackEngine.seekTo(previousPosition)
                                playbackEngine.resume()
                            }
                            onComplete(null)
                        }
                    }
                }

                override fun onPickerCancelled() {
                    android.util.Log.d("OverlayService", "onPickerCancelled called")
                    windowController?.restoreAfterFilePicker()
                    // キャンセル時はPlayingだった場合のみResume、Paused/Stoppedなら維持
                    if (wasPlaying) {
                        playbackEngine.resume()
                    }
                }
            }
        )
    }

    private fun handleSelectRecentSong(entry: RecentSongEntry) {
        if (isRecentSongLoading) return
        val currentState = playbackEngine.state.value
        // CountingDown中はRecent切替を受け付けない
        if (currentState is PlaybackState.CountingDown) return

        val currentUri = sessionRepository.currentSession.value?.uri?.toString()
        if (entry.uri == currentUri) return

        val uri = try {
            Uri.parse(entry.uri)
        } catch (_: Exception) {
            serviceScope.launch { settingsRepository.removeRecentSong(entry.uri) }
            return
        }

        isRecentSongLoading = true
        val wasPlaying = currentState is PlaybackState.Playing
        val previousPosition = playbackEngine.getCurrentPositionMs(allowNegative = false)
        if (wasPlaying) {
            playbackEngine.pause()
        }

        serviceScope.launch {
            try {
                val coordinator = SongSelectionCoordinator(
                    contentResolver = contentResolver,
                    sessionRepository = sessionRepository,
                    settingsRepository = settingsRepository
                )
                val result = coordinator.select(uri, initializeManualFromAuto = false)
                if (result.isSuccess) {
                    // 成功時は observePlaybackSession -> applySong 経由で
                    // 自然に stop() / setSong() され、Stopped 先頭待機となる（自動再生なし）
                } else {
                    android.util.Log.e(
                        "OverlayService",
                        "Failed to load recent song: ${entry.uri}",
                        result.exceptionOrNull()
                    )
                    settingsRepository.removeRecentSong(entry.uri)
                    if (wasPlaying) {
                        playbackEngine.seekTo(previousPosition)
                        playbackEngine.resume()
                    }
                }
            } finally {
                isRecentSongLoading = false
            }
        }
    }

    private fun startFrameLoop() {
        if (isFrameLoopRunning) return
        isFrameLoopRunning = true
        android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!isFrameLoopRunning) return
        isFrameLoopRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private var lastControlUpdateTimestamp = 0L
    private var lastControlPlayingState: Boolean? = null
    private var lastControlSongTitle: String? = null
    private var lastControlSpeed: Float = -1f
    private var lastControlNoteLeadTimeMs: Long = -1L
    private var lastControlApproachCircleLeadTimeMs: Long = -1L
    private var lastControlCountdownMs: Long = -1L
    private var lastControlLoopStartMs: Long? = null
    private var lastControlLoopEndMs: Long? = null

    private fun renderCurrentFrame(forceControlUpdate: Boolean = false) {
        val session = sessionRepository.currentSession.value
        val song = session?.song ?: playbackEngine.songData
        val config = settingsRepository.playbackConfig.value

        val state = playbackEngine.state.value
        val currentPos = playbackEngine.getCurrentPositionMs(allowNegative = true)

        // カウントダウン表示テキストの判定 (3, 2, 1, START)
        val countdownText = when (state) {
            is PlaybackState.CountingDown -> {
                state.countNumber.toString()
            }
            is PlaybackState.Playing -> {
                if (currentPos in 0L..600L) "START" else null
            }
            else -> null
        }

        // 停止時および再生完了時は空フレームにしてノート・ハイライト等を即座にクリア
        val frame = if (state is PlaybackState.Stopped || state is PlaybackState.Finished) {
            com.onigiri.keycue.playback.GuideFrame(
                currentTimeMs = if (state is PlaybackState.Stopped) 0L else currentPos,
                upcomingNotes = emptyList(),
                highlightedKeys = emptySet(),
                justKeys = emptySet(),
                countdownText = null,
                noteLeadTimeMs = config.noteLeadTimeMs,
                approachCircleLeadTimeMs = config.approachCircleLeadTimeMs
            )
        } else {
            noteScheduler.scheduleFrame(
                events = song?.events ?: emptyList(),
                currentTimeMs = currentPos,
                noteLeadTimeMs = config.noteLeadTimeMs,
                approachCircleLeadTimeMs = config.approachCircleLeadTimeMs,
                countdownText = countdownText
            )
        }

        windowController?.renderGuideFrame(frame)

        // Control Overlay の更新頻度分離:
        // 再生状態の変更（isPlaying）や曲の変更・停止・速度・先読み設定変更・ABループ変更・Countdown変更等の主要イベントは即時反映。
        // 連続的な positionMs 更新のみ 100ms (10Hz) 間隔で間引く。
        val isPlaying = state is PlaybackState.Playing
        val now = android.os.SystemClock.uptimeMillis()
        val loopStart = playbackEngine.loopStartMs
        val loopEnd = playbackEngine.loopEndMs
        val isMajorStateChanged = (lastControlPlayingState != isPlaying) ||
                (lastControlSongTitle != song?.title) ||
                (lastControlSpeed != config.speed) ||
                (lastControlNoteLeadTimeMs != config.noteLeadTimeMs) ||
                (lastControlApproachCircleLeadTimeMs != config.approachCircleLeadTimeMs) ||
                (lastControlCountdownMs != config.countdownMs) ||
                (lastControlLoopStartMs != loopStart) ||
                (lastControlLoopEndMs != loopEnd)
        val isTimeIntervalElapsed = (now - lastControlUpdateTimestamp >= 100L)

        if (forceControlUpdate || isMajorStateChanged || isTimeIntervalElapsed) {
            lastControlPlayingState = isPlaying
            lastControlSongTitle = song?.title
            lastControlSpeed = config.speed
            lastControlNoteLeadTimeMs = config.noteLeadTimeMs
            lastControlApproachCircleLeadTimeMs = config.approachCircleLeadTimeMs
            lastControlCountdownMs = config.countdownMs
            lastControlLoopStartMs = loopStart
            lastControlLoopEndMs = loopEnd
            lastControlUpdateTimestamp = now

            windowController?.updateControlStatus(
                isPlaying = isPlaying,
                positionMs = if (currentPos < 0L) 0L else currentPos,
                durationMs = song?.durationMs ?: 0L,
                speed = config.speed,
                songTitle = song?.title,
                noteLeadTimeMs = config.noteLeadTimeMs,
                approachCircleLeadTimeMs = config.approachCircleLeadTimeMs,
                loopStartMs = loopStart,
                loopEndMs = loopEnd,
                countdownMs = config.countdownMs
            )

            val metroConfig = settingsRepository.metronomeConfig.value
            val metroInfo = if (metroConfig.enabled && metronomeScheduler != null) {
                val queryPos = if (currentPos < 0L) 0L else currentPos
                val bpm = Math.round(metronomeScheduler!!.currentBpm(queryPos)).toInt()
                val ts = metronomeScheduler!!.currentTimeSignature(queryPos)
                val modeStr = if (metroConfig.timingMode == com.onigiri.keycue.model.MetronomeTimingMode.AUTO) "AUTO" else "MANUAL"
                "$modeStr $bpm BPM / ${ts.displayString}"
            } else {
                ""
            }
            windowController?.updateMetronomeState(metroConfig.enabled, metroInfo)
        }
    }

    private fun startAsForeground() {
        val notification = OverlayNotificationFactory.buildNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                OverlayNotificationFactory.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                OverlayNotificationFactory.NOTIFICATION_ID,
                notification,
                0
            )
        } else {
            startForeground(OverlayNotificationFactory.NOTIFICATION_ID, notification)
        }
    }

    private fun openMainActivity() {
        // 設定画面を開く直前にオーバーレイを一時非表示にし、画面遷移中の被りを防ぐ
        windowController?.hideForSettings()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPENED_FROM_OVERLAY, true)
        }
        startActivity(intent)
    }

    private fun stopOverlayAndSelf() {
        stopFrameLoop()
        playbackEngine.stop()
        metronomeScheduler?.stop()
        windowController?.destroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        stopFrameLoop()
        sessionCollectJob?.cancel()
        sessionCollectJob = null
        timelineResolutionJob?.cancel()
        timelineResolutionJob = null
        playbackEngine.onLoopRewound = null
        playbackEngine.release()
        metronomeScheduler?.release()
        metronomeScheduler = null
        metronomeSoundPlayer = null
        loadedSong = null
        windowController?.destroy()
        windowController = null
        serviceScope.cancel()
    }
}
