package com.onigiri.keycue.song

import com.onigiri.keycue.model.SongData
import java.io.InputStream

/**
 * 楽曲パーサーの共通インターフェース。
 *
 * 入力ストリームから楽曲データを読み取り、共通の [SongData] へ変換する。
 */
interface SongParser {
    /**
     * 入力ストリームから楽曲データを解析する。
     *
     * @param inputStream 楽曲ファイルの入力ストリーム
     * @param title 楽曲のタイトル（ファイル名など）
     * @return 解析された [SongData]
     */
    fun parse(inputStream: InputStream, title: String): SongData
}
