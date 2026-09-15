package com.onigiri.keycue.model

import android.net.Uri

/**
 * 演奏セッション情報を表すデータクラス。
 *
 * @param song 再生対象の楽曲データ
 * @param config 再生設定（速度、先読み、ハイライト時間、カウントダウン等）
 * @param fitProfile 15キー配置プロファイル
 * @param uri 楽曲ファイルの content:// URI
 * @param format 判定された楽曲フォーマット
 */
data class PlaybackSession(
    val song: SongData,
    val config: PlaybackConfig = PlaybackConfig(),
    val fitProfile: FitProfile = FitProfile.createDefaultTestProfile(),
    val uri: Uri? = null,
    val format: SongFormat? = null,
    val resolvedMidiMapping: ResolvedMidiMapping? = null
)
