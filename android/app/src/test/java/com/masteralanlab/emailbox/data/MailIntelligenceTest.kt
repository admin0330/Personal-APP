package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MailIntelligenceTest {
    @Test fun extractsChineseOtp() = assertEquals("482913", MailIntelligence.extractOtp("您的验证码为 482913，五分钟内有效"))
    @Test fun extractsDashedOtp() = assertEquals("A7-9K2", MailIntelligence.extractOtp("Verification code: A7-9K2"))
    @Test fun ignoresOrderNumberWithoutContext() = assertNull(MailIntelligence.extractOtp("订单号 202608301234 已发货"))
    @Test fun extractsMoneyAsMinorUnits() = assertEquals(MoneyCandidate("HKD", 123450), MailIntelligence.extractMoney("Payment HK$ 1,234.50"))
    @Test fun classifiesBill() = assertEquals(
        MailCategory.BILL,
        MailIntelligence.analyze(MessageItem(subject = "您的付款收据", body_preview = "已支付 ￥28.00")).category,
    )
}
