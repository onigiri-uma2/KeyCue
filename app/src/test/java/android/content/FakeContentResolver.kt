package android.content

import android.net.Uri

/**
 * JVM単体テスト用FakeContentResolver実装。
 * android.contentパッケージに配置してpackage-privateなコンストラクタにアクセスする。
 */
class FakeContentResolver : ContentResolver(null) {
    var lastTakePermissionUri: Uri? = null
    var shouldThrowSecurityException: Boolean = false

    override fun takePersistableUriPermission(uri: Uri, modeFlags: Int) {
        if (shouldThrowSecurityException) {
            throw SecurityException("Mock security exception")
        }
        lastTakePermissionUri = uri
    }
}
