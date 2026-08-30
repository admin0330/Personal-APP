package com.masteralanlab.emailbox

import com.masteralanlab.emailbox.data.remote.UpdateInfo
import com.masteralanlab.emailbox.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 应用内更新的两个安全门槛：清单只在远端 versionCode 更大时提示；
 * APK 落盘后必须先过 64 位 SHA-256 才能交给系统安装器。
 */
class UpdateManifestTest {

    private val publicManifestShape = UpdateInfo(
        versionCode = 3,
        versionName = "1.0.2",
        downloadUrl = "https://ym3861.cn/emailbox-updates/releases/emailbox-1.0.2-3.apk",
        sha256 = "7f4b074af0be9295ff41ec522394132742cf9ddbcd311e9df89c3496ed58f7ad",
        size = 2015201,
        changelog = "修复：修复 Release 混淆导致接口注解丢失的问题，保证所有功能在正式包中可用。",
    )

    @Test
    fun `same or lower versionCode never prompts`() {
        assertNull(UpdateManager.evaluateManifest(publicManifestShape, currentVersionCode = 3))
        assertNull(UpdateManager.evaluateManifest(publicManifestShape, currentVersionCode = 4))
    }

    @Test
    fun `higher versionCode with https url passes`() {
        val newer = publicManifestShape.copy(versionCode = 4, versionName = "1.0.3")
        assertEquals(newer, UpdateManager.evaluateManifest(newer, currentVersionCode = 3))
    }

    @Test
    fun `non-https download url is rejected`() {
        val http = publicManifestShape.copy(
            versionCode = 4,
            downloadUrl = "http://ym3861.cn/emailbox-updates/releases/emailbox-1.0.3-4.apk",
        )
        assertNull(UpdateManager.evaluateManifest(http, currentVersionCode = 3))
    }

    @Test
    fun `missing or wrong-length sha256 is rejected at manifest level`() {
        assertNull(
            UpdateManager.evaluateManifest(
                publicManifestShape.copy(versionCode = 4, sha256 = ""),
                currentVersionCode = 3,
            ),
        )
        assertNull(
            UpdateManager.evaluateManifest(
                publicManifestShape.copy(versionCode = 4, sha256 = "abc123"),
                currentVersionCode = 3,
            ),
        )
    }

    @Test
    fun `blank url without apkUrl fallback is rejected`() {
        assertNull(
            UpdateManager.evaluateManifest(
                publicManifestShape.copy(versionCode = 4, downloadUrl = "", apkUrl = ""),
                currentVersionCode = 3,
            ),
        )
    }

    @Test
    fun `sha256 format gate accepts exactly 64 hex chars`() {
        val hex = "a".repeat(64)
        assertTrue(UpdateManager.isValidSha256(hex))
        assertTrue(UpdateManager.isValidSha256(hex.uppercase()))
        assertFalse(UpdateManager.isValidSha256("g".repeat(64)))
        assertFalse(UpdateManager.isValidSha256("a".repeat(63)))
        assertFalse(UpdateManager.isValidSha256(""))
    }

    @Test
    fun `sha256 file digest matches known vector`() {
        val tmp = File.createTempFile("emailbox-test", ".apk")
        try {
            tmp.writeBytes("emailbox update payload".toByteArray(Charsets.UTF_8))
            // `printf 'emailbox update payload' | sha256sum` 的期望值
            val expected = java.security.MessageDigest
                .getInstance("SHA-256")
                .digest("emailbox update payload".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, UpdateManager.sha256(tmp))
        } finally {
            tmp.delete()
        }
    }
}
