package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.MessageItem
import java.math.BigDecimal
import java.math.RoundingMode

object MailCategory {
    const val OTP = "验证码"
    const val SECURITY = "登录与安全"
    const val BILL = "账单与交易"
    const val SHOPPING = "购物物流"
    const val SUBSCRIPTION = "订阅"
    const val OTHER = "其他"
    val all = listOf(OTP, SECURITY, BILL, SHOPPING, SUBSCRIPTION, OTHER)
}

data class MoneyCandidate(val currency: String, val amountMinor: Long)

data class MailInsight(
    val category: String,
    val otp: String? = null,
    val money: MoneyCandidate? = null,
)

object MailIntelligence {
    private val otpContext = Regex(
        "(?i)(验证码|校验码|动态码|一次性密码|verification|security code|one[- ]time|otp|passcode)",
    )
    private val otpCandidate = Regex("(?<![A-Za-z0-9])[A-Z0-9](?:[A-Z0-9-]{2,8})[A-Z0-9](?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
    private val security = Regex("(?i)(登录|登陆|异地|安全|密码|设备|sign[ -]?in|login|security|password|device)")
    private val bill = Regex("(?i)(账单|付款|支付|扣款|收款|交易|消费|退款|invoice|receipt|payment|charged|transaction|refund)")
    private val shopping = Regex("(?i)(订单|发货|物流|快递|配送|签收|order|shipped|delivery|tracking)")
    private val subscription = Regex("(?i)(订阅|续费|会员|到期|subscription|renewal|membership)")
    private val money = Regex("(?i)(CNY|RMB|USD|HKD|CN¥|HK\\u0024|US\\u0024|[\\u0024¥￥])\\s*([0-9]{1,9}(?:[,.][0-9]{1,3})*(?:\\.[0-9]{1,2})?)")

    fun analyze(item: MessageItem, body: String = "", overrideCategory: String? = null): MailInsight {
        val text = listOf(item.subject, item.from, item.body_preview, body).joinToString("\n")
        val otp = extractOtp(text)
        val category = overrideCategory ?: when {
            otp != null -> MailCategory.OTP
            security.containsMatchIn(text) -> MailCategory.SECURITY
            bill.containsMatchIn(text) -> MailCategory.BILL
            shopping.containsMatchIn(text) -> MailCategory.SHOPPING
            subscription.containsMatchIn(text) -> MailCategory.SUBSCRIPTION
            else -> MailCategory.OTHER
        }
        return MailInsight(category, otp, extractMoney(text))
    }

    fun extractOtp(text: String): String? {
        val context = otpContext.find(text) ?: return null
        val start = (context.range.first - 48).coerceAtLeast(0)
        val end = (context.range.last + 80).coerceAtMost(text.lastIndex)
        return otpCandidate.findAll(text.substring(start, end + 1))
            .map { it.value.trim('-') }
            .firstOrNull { candidate ->
                val compact = candidate.replace("-", "")
                compact.length in 4..8 && compact.any(Char::isDigit) &&
                    !compact.matches(Regex("(?:19|20)\\d{2}"))
            }
    }

    fun extractMoney(text: String): MoneyCandidate? {
        val match = money.find(text) ?: return null
        val currency = when (match.groupValues[1].uppercase()) {
            "USD", "US\u0024", "\u0024" -> "USD"
            "HKD", "HK\u0024" -> "HKD"
            else -> "CNY"
        }
        val normalized = match.groupValues[2].replace(",", "")
        val minor = runCatching {
            BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        return MoneyCandidate(currency, minor)
    }
}
