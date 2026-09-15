package com.onigiri.keycue.playback

import java.util.Locale

/**
 * 楽曲再生時間および現在位置の文字列フォーマットを行う共通ユーティリティ。
 *
 * コントロールオーバーレイ、プレビュー画面、ホーム画面等で
 * 一貫した表示形式 (例: "01:24 / 03:42", "01:05:30 / 01:20:00") を提供します。
 */
object TimeFormatter {

    /**
     * ミリ秒を "mm:ss" または "hh:mm:ss" 形式の文字列へ変換する。
     * 0未満の値は "00:00" として扱う。
     *
     * @param durationMs 時間（ミリ秒）
     * @param forceHours trueの場合、1時間未満でも "00:mm:ss" 形式で出力する
     */
    fun formatDuration(durationMs: Long, forceHours: Boolean = false): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L || forceHours) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 現在再生位置と総再生時間のペアを "mm:ss / mm:ss" 形式で生成する。
     * 総再生時間が1時間以上の場合は、両方とも "hh:mm:ss" 形式で揃える。
     *
     * @param currentMs 現在位置（ミリ秒）
     * @param totalMs 総時間（ミリ秒）
     */
    fun formatDurationPair(currentMs: Long, totalMs: Long): String {
        val requiresHours = totalMs >= 3600L * 1000L
        val currentStr = formatDuration(currentMs, forceHours = requiresHours)
        val totalStr = formatDuration(totalMs, forceHours = requiresHours)
        return "$currentStr / $totalStr"
    }
}
