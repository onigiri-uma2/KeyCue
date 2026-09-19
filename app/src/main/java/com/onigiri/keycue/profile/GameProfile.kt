package com.onigiri.keycue.profile

import com.onigiri.keycue.model.NormalizedPoint

/**
 * ゲーム固有のキーボード仕様および基準配置情報を定義するインターフェース。
 *
 * Coreロジック（再生タイミング、描画、フィッティングアルゴリズム等）から
 * 各ゲーム特有のハードコード値（キー数、幾何構造、基準座標、デフォルトMIDI音等）を
 * 分離するための境界定義です。
 */
interface GameProfile {
    /** 総キー数 */
    val keyCount: Int

    /** グリッド行数 */
    val rowCount: Int

    /** グリッド列数 */
    val columnCount: Int

    /** 基準となるキー中心正規化座標リスト（要素数は必ず [keyCount] と一致） */
    val baseKeyCenters: List<NormalizedPoint>

    /** 画面短辺に対する基準キー半径比率 */
    val baseKeyRadiusRatio: Float

    /** 4隅アンカーキーのインデックス (左上, 右上, 左下, 右下) */
    val topLeftKeyIndex: Int
    val topRightKeyIndex: Int
    val bottomLeftKeyIndex: Int
    val bottomRightKeyIndex: Int

    /** キーインデックスから行（段）番号 (0-indexed) を取得 */
    fun getRow(keyIndex: Int): Int

    /** キーインデックスから列番号 (0-indexed) を取得 */
    fun getColumn(keyIndex: Int): Int

    /** デフォルトのMIDIノート番号リスト */
    val defaultMidiNotes: List<Int>
}
