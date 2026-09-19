package com.onigiri.keycue.overlay

import com.onigiri.keycue.fitting.Corner
import com.onigiri.keycue.model.NormalizedPoint
import com.onigiri.keycue.profile.GameProfileRegistry

/**
 * キーグリッド位置調整における4隅（Anchor Corner）の移動および連動計算を担うコントローラー。
 *
 * ユーザーによる4隅ハンドルのドラッグや十字キー操作を受け、
 * 矩形連動モード（水平・垂直の対角点連動）または4点個別調整モード（台形・傾き対応の独立移動）に応じて
 * 各コーナーの正規化座標を計算・更新します。
 * Android View に依存しない純粋なKotlinロジックとして設計されており、JVM単体テストが可能です。
 */
class FittingCornerController(
    initialTopLeft: NormalizedPoint = GameProfileRegistry.current.baseKeyCenters[GameProfileRegistry.current.topLeftKeyIndex],
    initialTopRight: NormalizedPoint = GameProfileRegistry.current.baseKeyCenters[GameProfileRegistry.current.topRightKeyIndex],
    initialBottomLeft: NormalizedPoint = GameProfileRegistry.current.baseKeyCenters[GameProfileRegistry.current.bottomLeftKeyIndex],
    initialBottomRight: NormalizedPoint = GameProfileRegistry.current.baseKeyCenters[GameProfileRegistry.current.bottomRightKeyIndex],
    var isIndividualMode: Boolean = false
) {
    var topLeft: NormalizedPoint = initialTopLeft
        internal set

    var topRight: NormalizedPoint = initialTopRight
        internal set

    var bottomLeft: NormalizedPoint = initialBottomLeft
        internal set

    var bottomRight: NormalizedPoint = initialBottomRight
        internal set

    /**
     * コーナーの座標に delta (dxNorm, dyNorm) を加算する。
     *
     * - isIndividualMode == true (4点個別調整ON):
     *   指定Cornerのみ独立して移動（台形・傾き対応）。他のCornerは動かない。
     *
     * - isIndividualMode == false (4点個別調整OFF / 矩形連動モード):
     *   トリミング枠のように、水平・垂直の対角点と連動して矩形を維持。
     *   - TOP_LEFT: XはBOTTOM_LEFTと連動、YはTOP_RIGHTと連動
     *   - TOP_RIGHT: XはBOTTOM_RIGHTと連動、YはTOP_LEFTと連動
     *   - BOTTOM_LEFT: XはTOP_LEFTと連動、YはBOTTOM_RIGHTと連動
     *   - BOTTOM_RIGHT: XはTOP_RIGHTと連動、YはBOTTOM_LEFTと連動
     */
    fun moveCornerDelta(corner: Corner, dxNorm: Float, dyNorm: Float) {
        if (isIndividualMode) {
            // 自由変形モード: 操作対象の頂点のみを独立して移動させる（台形や歪みがある配置の微調整用）
            when (corner) {
                Corner.TOP_LEFT -> topLeft = NormalizedPoint(
                    (topLeft.x + dxNorm).coerceIn(0f, 1f),
                    (topLeft.y + dyNorm).coerceIn(0f, 1f)
                )
                Corner.TOP_RIGHT -> topRight = NormalizedPoint(
                    (topRight.x + dxNorm).coerceIn(0f, 1f),
                    (topRight.y + dyNorm).coerceIn(0f, 1f)
                )
                Corner.BOTTOM_LEFT -> bottomLeft = NormalizedPoint(
                    (bottomLeft.x + dxNorm).coerceIn(0f, 1f),
                    (bottomLeft.y + dyNorm).coerceIn(0f, 1f)
                )
                Corner.BOTTOM_RIGHT -> bottomRight = NormalizedPoint(
                    (bottomRight.x + dxNorm).coerceIn(0f, 1f),
                    (bottomRight.y + dyNorm).coerceIn(0f, 1f)
                )
            }
        } else {
            // 矩形維持モード: 指定された頂点だけでなく、同じX軸・Y軸を共有する隣接2頂点も連動移動させ、
            // 常に平行な長方形グリッドを保つ（歪みのない均等配置のサイズ調整用）
            when (corner) {
                Corner.TOP_LEFT -> {
                    val newX = (topLeft.x + dxNorm).coerceIn(0f, 1f)
                    val newY = (topLeft.y + dyNorm).coerceIn(0f, 1f)
                    topLeft = NormalizedPoint(newX, newY)
                    bottomLeft = NormalizedPoint(newX, bottomLeft.y) // 左端Xを同期
                    topRight = NormalizedPoint(topRight.x, newY)     // 上端Yを同期
                }
                Corner.TOP_RIGHT -> {
                    val newX = (topRight.x + dxNorm).coerceIn(0f, 1f)
                    val newY = (topRight.y + dyNorm).coerceIn(0f, 1f)
                    topRight = NormalizedPoint(newX, newY)
                    bottomRight = NormalizedPoint(newX, bottomRight.y) // 右端Xを同期
                    topLeft = NormalizedPoint(topLeft.x, newY)         // 上端Yを同期
                }
                Corner.BOTTOM_LEFT -> {
                    val newX = (bottomLeft.x + dxNorm).coerceIn(0f, 1f)
                    val newY = (bottomLeft.y + dyNorm).coerceIn(0f, 1f)
                    bottomLeft = NormalizedPoint(newX, newY)
                    topLeft = NormalizedPoint(newX, topLeft.y)         // 左端Xを同期
                    bottomRight = NormalizedPoint(bottomRight.x, newY) // 下端Yを同期
                }
                Corner.BOTTOM_RIGHT -> {
                    val newX = (bottomRight.x + dxNorm).coerceIn(0f, 1f)
                    val newY = (bottomRight.y + dyNorm).coerceIn(0f, 1f)
                    bottomRight = NormalizedPoint(newX, newY)
                    topRight = NormalizedPoint(newX, topRight.y)       // 右端Xを同期
                    bottomLeft = NormalizedPoint(bottomLeft.x, newY)   // 下端Yを同期
                }
            }
        }
    }
}
