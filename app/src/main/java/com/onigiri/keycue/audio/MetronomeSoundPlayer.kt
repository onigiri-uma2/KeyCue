package com.onigiri.keycue.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.onigiri.keycue.R

/**
 * メトロノームのクリック音を再生する音声プレイヤーのインターフェース。
 *
 * 単体テスト時はモックやフェイクに差し替え可能としています。
 */
interface MetronomeSoundPlayer {
    /**
     * メトロノームクリック音を発音する。
     *
     * @param isAccent アクセント音（小節頭）を鳴らすかどうか
     * @param volumePercent 音量パーセント（0〜100）
     */
    fun playBeat(isAccent: Boolean, volumePercent: Int)

    /**
     * 再生中のクリック音を停止する。
     */
    fun stop()

    /**
     * 保持している音声リソースを解放する。
     */
    fun release()
}

/**
 * Androidの [SoundPool] を用いて超低遅延で短いクリック音を再生する実装クラス。
 *
 * Sky 星を紡ぐ子どもたち のゲーム演奏音と共存させるため、Audio Focus の明示的奪取は行わず、
 * ゲーム効果音用途（USAGE_GAME, CONTENT_TYPE_SONIFICATION）の AudioAttributes を使用します。
 */
class SoundPoolMetronomeSoundPlayer(
    private val context: Context
) : MetronomeSoundPlayer {

    companion object {
        private const val TAG = "SoundPoolMetroPlayer"
        private const val MAX_STREAMS = 2
    }

    private val soundPool: SoundPool
    private var beatSoundId: Int = 0
    private var accentSoundId: Int = 0

    private var isBeatLoaded = false
    private var isAccentLoaded = false

    private var lastStreamId: Int = 0
    private var isReleased = false
    private val lock = Any()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(lock) {
                if (!isReleased) {
                    if (status == 0) {
                        if (sampleId == beatSoundId) isBeatLoaded = true
                        if (sampleId == accentSoundId) isAccentLoaded = true
                    } else {
                        Log.w(TAG, "Failed to load metronome sound sampleId=$sampleId, status=$status")
                    }
                }
            }
        }

        try {
            beatSoundId = soundPool.load(context, R.raw.metronome_beat, 1)
            accentSoundId = soundPool.load(context, R.raw.metronome_accent, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating sound loading", e)
        }
    }

    override fun playBeat(isAccent: Boolean, volumePercent: Int) {
        val clampedVolPercent = volumePercent.coerceIn(0, 100)
        if (clampedVolPercent <= 0) return

        synchronized(lock) {
            if (isReleased) return

            val targetSoundId = if (isAccent) accentSoundId else beatSoundId
            val isLoaded = if (isAccent) isAccentLoaded else isBeatLoaded

            if (!isLoaded || targetSoundId == 0) {
                // 音源未ロード時は安全にスキップ
                return
            }

            val volume = clampedVolPercent / 100f
            try {
                // 直前のストリームが存在する場合は停止して不要な重なりを防ぐ
                if (lastStreamId != 0) {
                    soundPool.stop(lastStreamId)
                }
                lastStreamId = soundPool.play(
                    targetSoundId,
                    volume, // leftVolume
                    volume, // rightVolume
                    1,      // priority
                    0,      // loop (0 = no loop)
                    1.0f    // rate
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception during soundPool.play", e)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (isReleased) return
            if (lastStreamId != 0) {
                try {
                    soundPool.stop(lastStreamId)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during soundPool.stop", e)
                }
                lastStreamId = 0
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            if (isReleased) return
            isReleased = true
            if (lastStreamId != 0) {
                try {
                    soundPool.stop(lastStreamId)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during soundPool.stop", e)
                }
                lastStreamId = 0
            }
            try {
                soundPool.release()
            } catch (e: Exception) {
                Log.w(TAG, "Exception during soundPool.release", e)
            }
        }
    }
}
