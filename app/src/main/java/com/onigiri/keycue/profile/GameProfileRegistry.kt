package com.onigiri.keycue.profile

/**
 * 現在アクティブなゲームプロファイルを提供するレジストリ。
 *
 * 現時点ではSky専用として動作するため、単一の [SkyProfile] を提供します。
 */
object GameProfileRegistry {
    val current: GameProfile = SkyProfile
}
