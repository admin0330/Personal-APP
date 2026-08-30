package com.masteralanlab.emailbox

import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.apiBaseUrl
import com.masteralanlab.emailbox.data.remote.normalizeServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 服务器地址规范化是所有请求的第一道门：HTTPS 强制、路径保留、垃圾输入拦截。
 */
class NormalizeServerUrlTest {

    @Test
    fun `bare host becomes https with default path`() {
        assertEquals(
            Prefs.DEFAULT_SERVER,
            normalizeServerUrl("ym3861.cn"),
        )
    }

    @Test
    fun `production address with subpath is preserved and trimmed`() {
        assertEquals(
            "https://ym3861.cn/emailbox",
            normalizeServerUrl("https://ym3861.cn/emailbox/"),
        )
    }

    @Test
    fun `plain http is upgraded to https`() {
        assertEquals(
            "https://ym3861.cn/emailbox",
            normalizeServerUrl("http://ym3861.cn/emailbox"),
        )
    }

    @Test
    fun `bare production host gains emailbox path`() {
        assertEquals(
            "https://ym3861.cn/emailbox",
            normalizeServerUrl("https://ym3861.cn"),
        )
        assertEquals(
            "https://ym3861.cn/emailbox",
            normalizeServerUrl("ym3861.cn/"),
        )
    }

    @Test
    fun `blank input falls back to default server`() {
        assertEquals(Prefs.DEFAULT_SERVER, normalizeServerUrl("   "))
    }

    @Test
    fun `other hosts keep their own path`() {
        assertEquals(
            "https://example.com/mail",
            normalizeServerUrl("example.com/mail"),
        )
    }

    @Test
    fun `embedded credentials are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("https://user:pass@ym3861.cn/emailbox")
        }
    }

    @Test
    fun `query and fragment are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("https://ym3861.cn/emailbox?next=1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("https://ym3861.cn/emailbox#login")
        }
    }

    @Test
    fun `garbage input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("not a url")
        }
    }

    @Test
    fun `api base url ends with slash for retrofit`() {
        assertEquals(
            "https://ym3861.cn/emailbox/api/v1/",
            apiBaseUrl("https://ym3861.cn/emailbox"),
        )
    }
}
