package com.onigiri.keycue.song.sky

import java.io.IOException

/**
 * Sky Studio JSON解析関連の例外基底クラス。
 */
open class SkyStudioParseException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * 暗号化されたSky Studio譜面を検出した場合にスローされる例外。
 */
class EncryptedSkyStudioException(
    message: String = "このSky Studio譜面は暗号化されているため読み込めません。暗号化なしでエクスポートしたファイルを使用してください。"
) : SkyStudioParseException(message)

/**
 * 不正なSky Studio JSON構造（songNotesが存在しない、構文エラーなど）の場合にスローされる例外。
 */
class InvalidSkyStudioJsonException(
    message: String,
    cause: Throwable? = null
) : SkyStudioParseException(message, cause)
