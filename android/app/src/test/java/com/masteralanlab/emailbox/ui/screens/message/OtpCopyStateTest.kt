package com.masteralanlab.emailbox.ui.screens.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpCopyStateTest {

    @Test
    fun `copied state is isolated to the active code`() {
        assertTrue(isOtpCopied("482913", "482913"))
        assertFalse(isOtpCopied("482913", "120884"))
        assertFalse(isOtpCopied("", ""))
    }
}
