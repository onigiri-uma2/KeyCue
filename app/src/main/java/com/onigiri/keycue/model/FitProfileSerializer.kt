package com.onigiri.keycue.model

import com.onigiri.keycue.profile.GameProfileRegistry

/**
 * [FitProfile] のシリアライズおよびデシリアライズを行うユーティリティ。
 *
 * 外部JSONライブラリやAndroidフレームワーク（org.json等）に依存せず純粋なKotlinロジックで実装されており、
 * JVM上の単体テストおよびSharedPreferencesでの永続化において安全に動作する。
 */
object FitProfileSerializer {

    /**
     * [FitProfile] をJSON文字列へシリアライズする。
     */
    fun toJson(profile: FitProfile): String {
        val centersJson = profile.keyCenters.joinToString(separator = ",") { point ->
            "{\"x\":${point.x},\"y\":${point.y}}"
        }
        return "{\"landscape\":${profile.landscape},\"keyRadiusRatio\":${profile.keyRadiusRatio},\"keyCenters\":[$centersJson]}"
    }

    /**
     * JSON文字列から [FitProfile] を復元する。
     * フォーマット不正やキー要素数が不正な場合は null を返す。
     */
    fun fromJson(json: String?): FitProfile? {
        if (json.isNullOrBlank()) return null

        return try {
            // landscape 判定
            val landscape = if (json.contains("\"landscape\":false")) false else true

            // keyRadiusRatio 抽出
            val radiusRegex = Regex("\"keyRadiusRatio\"\\s*:\\s*([0-9.]+)")
            val radiusMatch = radiusRegex.find(json)
            val radius = radiusMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0.04f

            // keyCenters 抽出: 各 {"x": ..., "y": ...}
            val pointRegex = Regex("\\{\\s*\"x\"\\s*:\\s*([0-9.]+)\\s*,\\s*\"y\"\\s*:\\s*([0-9.]+)\\s*\\}")
            val matches = pointRegex.findAll(json).toList()

            val expectedKeyCount = GameProfileRegistry.current.keyCount
            if (matches.size != expectedKeyCount) {
                return null
            }

            val centers = matches.map { match ->
                val x = match.groupValues[1].toFloat().coerceIn(0f, 1f)
                val y = match.groupValues[2].toFloat().coerceIn(0f, 1f)
                NormalizedPoint(x, y)
            }

            FitProfile(
                keyCenters = centers,
                keyRadiusRatio = radius.coerceAtLeast(0.001f),
                landscape = landscape
            )
        } catch (_: Exception) {
            null
        }
    }
}
