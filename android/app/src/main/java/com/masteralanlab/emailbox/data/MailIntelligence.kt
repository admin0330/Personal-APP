package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.MessageItem

object MailCategory {
    const val OTP = "验证码"
    const val SECURITY = "登录与安全"
    const val BILL = "账单与交易"
    const val SHOPPING = "购物物流"
    const val SUBSCRIPTION = "订阅"
    const val OTHER = "其他"
    val all = listOf(OTP, SECURITY, BILL, SHOPPING, SUBSCRIPTION, OTHER)
}

data class MailInsight(
    val category: String,
    val otp: String? = null,
)

object MailIntelligence {
    const val PARSER_VERSION = 2

    fun insightKey(item: MessageItem): String = "${item.id_mode}\u0000${item.id}"

    private val otpContext = Regex(
        "(?i)(验证码|校验码|动态码|一次性(?:验证码|密码)|安全码|verification(?:\\s*code)?|security\\s*code|one[-\\s]*time(?:\\s*code)?|otp|passcode)",
    )
    private val token = Regex(
        "(?<![A-Za-z0-9])([A-Za-z0-9]+(?:[ \\t]*[-‐‑‒–—―][ \\t]*[A-Za-z0-9]+)*)(?![A-Za-z0-9])",
    )
    private val htmlComment = Regex("(?is)<!--.*?-->")
    private val htmlHead = Regex("(?is)<head\\b[^>]*>.*?</head\\s*>")
    private val htmlScript = Regex("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)\\s*>")
    private val blockTag = Regex(
        "(?i)</?\\s*(?:address|article|aside|blockquote|br|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|li|main|nav|ol|p|pre|section|table|td|th|tr|ul)\\b[^>]*>",
    )
    private val htmlTag = Regex("(?is)</?[A-Za-z][^>]*>")
    private val entity = Regex("&(#x[0-9a-fA-F]+|#\\d+|[A-Za-z][A-Za-z0-9]+);")
    private val url = Regex("(?i)https?://[^\\s<>]+")
    private val email = Regex("(?i)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val dateLike = Regex("(?:19|20)\\d{2}[-/.]?\\d{1,2}[-/.]?\\d{1,2}")
    private val metadataBefore = Regex(
        "(?i)(?:订单(?:号)?|order(?:\\s+number)?|tracking(?:\\s+number)?|运单号|物流单号|电话|手机|tel|phone|mobile|amount|金额|price|价格|color|font(?:-size)?|margin|padding|width|height|[$¥￥])\\s*[:：#№-]?\\s*$",
    )
    private val metadataAfter = Regex(
        "(?i)^\\s*(?:元|美元|港币|usd|cny|hkd|订单|order|tracking|运单|物流)",
    )
    private val namedEntities = mapOf(
        "amp" to '&',
        "lt" to '<',
        "gt" to '>',
        "quot" to '"',
        "apos" to '\'',
        "nbsp" to ' ',
        "ndash" to '–',
        "mdash" to '—',
        "minus" to '−',
    )
    private val security = Regex("(?i)(登录|登陆|异地|安全|密码|设备|sign[ -]?in|login|security|password|device)")
    private val bill = Regex("(?i)(账单|付款|支付|扣款|收款|交易|消费|退款|invoice|receipt|payment|charged|transaction|refund)")
    private val shopping = Regex("(?i)(订单|发货|物流|快递|配送|签收|order|shipped|delivery|tracking)")
    private val subscription = Regex("(?i)(订阅|续费|会员|到期|subscription|renewal|membership)")
    private data class Token(val start: Int, val end: Int, val value: String)
    private data class OtpCandidate(val value: String, val start: Int, val end: Int)
    private data class ScoredOtp(val value: String, val score: Int)

    fun analyze(item: MessageItem, body: String = "", overrideCategory: String? = null): MailInsight {
        val parts = listOf(item.subject, item.from, item.body_preview, body)
        val text = parts.joinToString("\n") { visibleText(it) }
        val otp = extractOtpFromParts(listOf(item.subject, item.body_preview, body))
        val category = overrideCategory ?: when {
            otp != null -> MailCategory.OTP
            security.containsMatchIn(text) -> MailCategory.SECURITY
            bill.containsMatchIn(text) -> MailCategory.BILL
            shopping.containsMatchIn(text) -> MailCategory.SHOPPING
            subscription.containsMatchIn(text) -> MailCategory.SUBSCRIPTION
            else -> MailCategory.OTHER
        }
        return MailInsight(category, otp)
    }

    fun extractOtp(text: String): String? = bestOtp(visibleText(text))?.value

    /** 共享给翻译前置检测的正文清洗结果，确保列表、详情和翻译不各自解析一遍。 */
    fun cleanVisibleText(input: String): String = visibleText(input)

    private fun extractOtpFromParts(parts: List<String>): String? = parts
        .mapNotNull { bestOtp(visibleText(it)) }
        .minByOrNull { it.score }
        ?.value

    private fun bestOtp(text: String): ScoredOtp? {
        val contexts = otpContext.findAll(text).toList()
        if (contexts.isEmpty()) return null

        return candidates(text).mapNotNull { candidate ->
            val context = contexts.minByOrNull { distance(candidate, it.range.first, it.range.last + 1) }
                ?: return@mapNotNull null
            val distance = distance(candidate, context.range.first, context.range.last + 1)
            if (distance > 240) return@mapNotNull null
            val between = when {
                candidate.end <= context.range.first -> text.substring(candidate.end, context.range.first)
                context.range.last + 1 <= candidate.start -> text.substring(context.range.last + 1, candidate.start)
                else -> ""
            }
            ScoredOtp(
                candidate.value,
                distance +
                    (if (candidate.start < context.range.first) 12 else 0) -
                    (if (!between.contains('\n')) 8 else 0),
            )
        }.minWithOrNull(compareBy<ScoredOtp> { it.score }.thenBy { it.value.length })
    }

    private fun distance(candidate: OtpCandidate, start: Int, end: Int): Int = when {
        candidate.end <= start -> start - candidate.end
        candidate.start >= end -> candidate.start - end
        else -> 0
    }

    private fun candidates(text: String): List<OtpCandidate> {
        val tokens = token.findAll(text).map { Token(it.range.first, it.range.last + 1, it.value) }.toList()
        val out = linkedMapOf<String, OtpCandidate>()

        tokens.forEachIndexed { index, first ->
            for (lastIndex in index until minOf(index + 4, tokens.size)) {
                val last = tokens[lastIndex]
                if (lastIndex > index) {
                    val gap = text.substring(tokens[lastIndex - 1].end, last.start)
                    if (gap.any { it != ' ' && it != '\t' && it != '\n' && it != '\r' }) break
                }
                val raw = text.substring(first.start, last.end)
                val candidate = raw.filter { it.isLetterOrDigit() }
                if (candidate.length !in 4..8 || !candidate.any(Char::isDigit)) continue
                if (lastIndex > index && (index..lastIndex).any { !tokens[it].value.any(Char::isDigit) }) continue
                if (!isSafeCandidate(raw, candidate, text, first.start, last.end)) continue
                out.putIfAbsent("${first.start}:$last.end", OtpCandidate(raw.trim(), first.start, last.end))
            }
        }
        return out.values.toList()
    }

    private fun isSafeCandidate(raw: String, compact: String, text: String, start: Int, end: Int): Boolean {
        if (raw.count { it == '-' || it == '‐' || it == '‑' || it == '‒' || it == '–' || it == '—' || it == '―' } > 2) return false
        if (compact.all(Char::isDigit)) {
            if (compact.length == 4 && compact.matches(Regex("(?:19|20)\\d{2}"))) return false
            if (compact.length == 8 && compact.matches(Regex("(?:19|20)\\d{6}"))) return false
            if (dateLike.matches(raw)) return false
        }
        if (start > 0 && text[start - 1] == '#') return false
        if (url.findAll(text).any { start >= it.range.first && end <= it.range.last + 1 }) return false
        if (email.findAll(text).any { start >= it.range.first && end <= it.range.last + 1 }) return false

        val before = text.substring((start - 40).coerceAtLeast(0), start)
        val after = text.substring(end, (end + 40).coerceAtMost(text.length))
        if (compact.all(Char::isDigit) && (metadataBefore.containsMatchIn(before) || metadataAfter.containsMatchIn(after))) return false
        if (compact.all(Char::isDigit) && Regex("(?i)(?:\\+\\d{1,4}[ \\t-]*)$").containsMatchIn(before)) return false
        return true
    }

    private fun visibleText(input: String): String {
        val withoutMarkup = input
            .replace(htmlComment, "")
            .replace(htmlHead, "")
            .replace(htmlScript, "")
            .replace(blockTag, "\n")
            .replace(htmlTag, "")
        return decodeEntities(withoutMarkup)
            .map(::normalizeChar)
            .joinToString("")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" *\\n+ *"), "\n")
            .trim()
    }

    private fun decodeEntities(value: String): String = entity.replace(value) { match ->
        val name = match.groupValues[1]
        namedEntities[name]?.toString() ?: when {
            name.startsWith("#x", ignoreCase = true) -> name.substring(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
            name.startsWith('#') -> name.substring(1).toIntOrNull()?.let(::codePoint) ?: match.value
            else -> match.value
        }
    }

    private fun codePoint(value: Int): String = runCatching { String(Character.toChars(value)) }.getOrDefault("")

    private fun normalizeChar(char: Char): Char = when (char) {
        in '０'..'９' -> ('0'.code + char.code - '０'.code).toChar()
        in 'Ａ'..'Ｚ' -> ('A'.code + char.code - 'Ａ'.code).toChar()
        in 'ａ'..'ｚ' -> ('a'.code + char.code - 'ａ'.code).toChar()
        '　' -> ' '
        '－', '‐', '‑', '‒', '–', '—', '―' -> '-'
        '：' -> ':'
        else -> char
    }
}

/**
 * 翻译只针对足够长、单一拉丁文字正文；短文本、混合语言、按钮和签名不会自动提示。
 * 这些阈值是产品规则的一部分，避免把 OTP、链接或 HTML 元数据误判为英文邮件。
 */
object EnglishBodyDetector {
    const val MIN_VISIBLE_CHARS = 80
    const val MIN_ENGLISH_WORDS = 15
    const val MIN_LANGUAGE_CONFIDENCE = 0.90f
    const val MIN_LATIN_RATIO = 0.90f
    const val MAX_CJK_CHARS = 3

    private val url = Regex("(?i)https?://[^\\s<>]+")
    private val email = Regex("(?i)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val otp = Regex("(?i)\\b(?:otp|code|passcode|verification)\\s*[:#-]?\\s*[A-Za-z0-9-]{4,12}\\b")
    private val englishWord = Regex("\\b[A-Za-z]{2,}\\b")
    private val boilerplate = Regex(
        "(?i)^(?:unsubscribe|view (?:in )?browser|click here|manage preferences|sent from my (?:iphone|android)|this email was sent|privacy policy|terms of service)\\b",
    )

    data class Candidate(
        val text: String,
        val englishWords: Int,
        val latinRatio: Float,
        val cjkChars: Int,
    )

    fun candidate(body: String): Candidate? {
        val text = MailIntelligence.cleanVisibleText(body)
            .lineSequence()
            .takeWhile { line ->
                val normalized = line.trim()
                !normalized.startsWith("--") &&
                    !normalized.startsWith(">") &&
                    !normalized.contains("original message", ignoreCase = true) &&
                    !normalized.matches(Regex("(?i)^on .+ wrote:$"))
            }
            .filterNot { boilerplate.containsMatchIn(it.trim()) }
            .joinToString(" ")
            .replace(url, " ")
            .replace(email, " ")
            .replace(otp, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length < MIN_VISIBLE_CHARS) return null
        val letters = text.count(Char::isLetter)
        if (letters == 0) return null
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val cjk = text.count { it.code in 0x3400..0x4DBF || it.code in 0x4E00..0x9FFF }
        val words = englishWord.findAll(text).count()
        val ratio = latin.toFloat() / letters
        if (words < MIN_ENGLISH_WORDS || ratio < MIN_LATIN_RATIO || cjk > MAX_CJK_CHARS) return null
        return Candidate(text, words, ratio, cjk)
    }
}
