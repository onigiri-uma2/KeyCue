package android.net

import android.os.Parcel

/**
 * JVM単体テスト用FakeUri実装。android.netパッケージに配置してpackage-privateなUriコンストラクタにアクセスする。
 */
class FakeUri(private val uriString: String) : Uri() {
    override fun toString(): String = uriString
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = uriString.substringAfter(":")
    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart
    override fun getAuthority(): String = "media"
    override fun getEncodedAuthority(): String = authority
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = "media"
    override fun getPort(): Int = -1
    override fun getPath(): String = "/external/audio/media/42"
    override fun getEncodedPath(): String = path
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("external", "audio", "media", "42")
    override fun getLastPathSegment(): String = "42"
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString() ?: "")
    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == uriString
    override fun hashCode(): Int = uriString.hashCode()

    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}
