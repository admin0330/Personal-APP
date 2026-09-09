package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailIntelligenceTest {
    @Test fun extractsChineseOtp() = assertEquals("482913", MailIntelligence.extractOtp("您的验证码为 482913，五分钟内有效"))
    @Test fun extractsEnglishOtp() = assertEquals("482913", MailIntelligence.extractOtp("Your verification code is 482913"))
    @Test fun extractsOtpBeforeContext() = assertEquals("482913", MailIntelligence.extractOtp("482913 是您的验证码"))
    @Test fun extractsDashedOtp() = assertEquals("A7-9K2", MailIntelligence.extractOtp("Verification code: A7-9K2"))
    @Test fun extractsSpacedOtp() = assertEquals("123 456", MailIntelligence.extractOtp("OTP: 123 456"))
    @Test fun extractsOtpWrappedInHtml() = assertEquals("482913", MailIntelligence.extractOtp("<p>Your <b>verification code</b> is <strong>482913</strong></p>"))
    @Test fun extractsWhenContextIsSplitAcrossInlineTags() = assertEquals("482913", MailIntelligence.extractOtp("<b>verification</b><b>code</b>: 482913"))
    @Test fun extractsOtpSplitAcrossInlineTags() = assertEquals("482913", MailIntelligence.extractOtp("<span>482</span><span>913</span> 是您的验证码"))
    @Test fun decodesEntitiesAndFullWidthDigits() = assertEquals(
        "482913",
        MailIntelligence.extractOtp("您的验证码为 &#xFF14;&#xFF18;&#xFF12;&#xFF19;&#xFF11;&#xFF13;"),
    )
    @Test fun choosesCandidateClosestToContext() = assertEquals(
        "482913",
        MailIntelligence.extractOtp("通知编号 123456。Your verification code is 482913。订单号 654321"),
    )
    @Test fun ignoresOrderNumberWithoutContext() = assertNull(MailIntelligence.extractOtp("订单号 202608301234 已发货"))
    @Test fun ignoresDatesMoneyPhonesUrlsCssAndUuid() = assertNull(
        MailIntelligence.extractOtp(
            "验证码相关说明：日期 2026-09-01，金额 $1,234.50，订单号 20260830，电话 13800138000，" +
                "网址 https://example.com/482913，颜色 #482913，UUID 550e8400-e29b-41d4-a716-446655440000",
        ),
    )
    @Test fun categoryOverrideDoesNotRemoveOtp() {
        val insight = MailIntelligence.analyze(
            MessageItem(subject = "安全通知", body_preview = "Your verification code is 482913"),
            overrideCategory = MailCategory.BILL,
        )
        assertEquals(MailCategory.BILL, insight.category)
        assertEquals("482913", insight.otp)
    }
    @Test fun classifiesBill() = assertEquals(
        MailCategory.BILL,
        MailIntelligence.analyze(MessageItem(subject = "您的付款收据", body_preview = "已支付 ￥28.00")).category,
    )

    @Test fun englishCandidateRequiresLongMostlyLatinBody() {
        val body = "Your account security notice explains the recent sign in activity and the next steps. " +
            "Please review the details carefully before continuing with your account."
        assertEquals(true, EnglishBodyDetector.candidate(body) != null)
        assertEquals(null, EnglishBodyDetector.candidate("Your code is 482913. 请尽快使用。"))
    }
}
