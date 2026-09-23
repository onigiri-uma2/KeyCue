package com.onigiri.keycue.overlay

import com.onigiri.keycue.model.FitProfile

/**
 * コントロールオーバーレイ（フローティング操作UI）からの各種ユーザー操作イベントを受け取るコールバック群。
 *
 * 画面上のドラッグ移動、再生制御（再生/一時停止/停止/リスタート/シーク）、
 * 演奏ガイド表示切替、再生速度・先読み変更、フィッティング開始等のイベントを集約して伝達します。
 */
data class ControlOverlayCallbacks(
    /** ホーム画面（本体アプリ）を開く */
    val onOpenApp: () -> Unit = {},
    /** オーバーレイサービスおよび全オーバーレイを終了する */
    val onCloseOverlay: () -> Unit = {},
    /** フローティングアイコン・パネルの位置がユーザーのドラッグ操作によって変更された */
    val onPositionChanged: (normX: Float, normY: Float, pixelX: Int, pixelY: Int) -> Unit = { _, _, _, _ -> },
    /** 楽曲ファイル選択ピッカーを起動する */
    val onSelectFile: () -> Unit = {},
    /** 演奏ガイドオーバーレイの表示/非表示をトグルする（戻り値は切替後の表示状態） */
    val onToggleGuide: () -> Boolean = { false },
    /** 現在演奏ガイドが表示中かどうかを取得する */
    val isGuideShowing: () -> Boolean = { false },
    /** 再生 / 一時停止を切り替える */
    val onPlayPause: () -> Unit = {},
    /** 再生を停止して先頭に戻す */
    val onStop: () -> Unit = {},
    /** 先頭から再生を開始・やり直す */
    val onRestart: () -> Unit = {},
    /** 一定時間巻き戻す */
    val onSeekBack: () -> Unit = {},
    /** 一定時間早送りする */
    val onSeekForward: () -> Unit = {},
    /** 指定した楽曲位置（ミリ秒）へシークする */
    val onSeekTo: (Long) -> Unit = {},
    /** ABリピートのA地点（開始位置）を現在位置に設定する */
    val onSetLoopStart: () -> Unit = {},
    /** ABリピートのB地点（終了位置）を現在位置に設定する */
    val onSetLoopEnd: () -> Unit = {},
    /** ABリピート設定を解除（クリア）する */
    val onClearLoop: () -> Unit = {},
    /** 再生速度（0.5x〜2.0x等）を変更する */
    val onSpeedChange: (Float) -> Unit = {},
    /** ノート先読み時間（ms）を変更する */
    val onNoteLeadTimeChange: (Long) -> Unit = {},
    /** タイミングサークル先読み時間（ms）を変更する */
    val onApproachCircleLeadTimeChange: (Long) -> Unit = {},
    /** ゲーム画面上でのキー位置手動調整（フィッティング）を開始する */
    val onStartFitting: () -> Unit = {},
    /** 調整されたキー位置プロファイルを保存する */
    val onSaveFitProfile: (FitProfile) -> Unit = {},
    /** 開始カウントダウン時間（ms）を変更する */
    val onCountdownChange: (Long) -> Unit = {},
    /** 落下ノート（Falling Notes）表示ON/OFFを切り替える */
    val onShowFallingNotesChange: (Boolean) -> Unit = {},
    /** タイミングサークル（Approach Circles）表示ON/OFFを切り替える */
    val onShowApproachCirclesChange: (Boolean) -> Unit = {}
)
