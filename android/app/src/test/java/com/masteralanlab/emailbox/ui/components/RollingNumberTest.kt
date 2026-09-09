package com.masteralanlab.emailbox.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingNumberTest {

    @Test
    fun testReelSequenceUpIncreasing() {
        // Normal increasing
        assertEquals(listOf(2, 3, 4, 5), buildReelSequence(2, 5, RollDirection.Up))
        // Cyclic carry across 9 -> 0
        assertEquals(listOf(8, 9, 0, 1, 2), buildReelSequence(8, 2, RollDirection.Up))
        // Unchanged
        assertEquals(listOf(4), buildReelSequence(4, 4, RollDirection.Up))
    }

    @Test
    fun testReelSequenceDownDecreasing() {
        // Normal decreasing
        assertEquals(listOf(5, 4, 3, 2), buildReelSequence(5, 2, RollDirection.Down))
        // Cyclic borrow across 0 -> 9
        assertEquals(listOf(2, 1, 0, 9, 8), buildReelSequence(2, 8, RollDirection.Down))
        // Unchanged
        assertEquals(listOf(7), buildReelSequence(7, 7, RollDirection.Down))
    }

    @Test
    fun testTokenizeSimpleNumber() {
        val (tokens0, text0) = buildTokensForNumber(0L, grouping = false, prefix = "", suffix = "")
        assertEquals("0", text0)
        assertEquals(1, tokens0.size)
        assertTrue(tokens0[0] is RollingToken.Digit)
        assertEquals(0, (tokens0[0] as RollingToken.Digit).place)
        assertEquals(0, (tokens0[0] as RollingToken.Digit).digit)

        // 99 to 100 transition place expansion
        val (tokens99, text99) = buildTokensForNumber(99L, grouping = false, prefix = "", suffix = "")
        assertEquals("99", text99)
        assertEquals(2, tokens99.size)
        assertEquals(1, (tokens99[0] as RollingToken.Digit).place)
        assertEquals(9, (tokens99[0] as RollingToken.Digit).digit)
        assertEquals(0, (tokens99[1] as RollingToken.Digit).place)
        assertEquals(9, (tokens99[1] as RollingToken.Digit).digit)

        val (tokens100, text100) = buildTokensForNumber(100L, grouping = false, prefix = "", suffix = "")
        assertEquals("100", text100)
        assertEquals(3, tokens100.size)
        assertEquals(2, (tokens100[0] as RollingToken.Digit).place)
        assertEquals(1, (tokens100[0] as RollingToken.Digit).digit)
    }

    @Test
    fun testTokenizeWithGroupingAndThousandTransitions() {
        // 1000 -> 999
        val (tokens1000, text1000) = buildTokensForNumber(1000L, grouping = true, prefix = "", suffix = "")
        assertEquals("1,000", text1000)
        // 1 digit (place 3), 1 comma (sep_3), 3 digits (places 2, 1, 0)
        assertEquals(5, tokens1000.size)
        assertEquals("int_3", tokens1000[0].key)
        assertEquals("sep_3", tokens1000[1].key)
        assertEquals(",", (tokens1000[1] as RollingToken.StaticText).text)

        val (tokens999, text999) = buildTokensForNumber(999L, grouping = true, prefix = "", suffix = "")
        assertEquals("999", text999)
        assertEquals(3, tokens999.size)
        assertEquals("int_2", tokens999[0].key)
    }

    @Test
    fun testTokenizeNegativeNumberAndPrefixSuffix() {
        val (tokens, text) = buildTokensForNumber(-42L, grouping = false, prefix = "已选 ", suffix = " 项")
        assertEquals("已选 -42 项", text)
        assertEquals("prefix", tokens[0].key)
        assertEquals("已选 ", (tokens[0] as RollingToken.StaticText).text)
        assertEquals("sign", tokens[1].key)
        assertEquals("-", (tokens[1] as RollingToken.StaticText).text)
        assertEquals("int_1", tokens[2].key)
        assertEquals(4, (tokens[2] as RollingToken.Digit).digit)
        assertEquals("int_0", tokens[3].key)
        assertEquals(2, (tokens[3] as RollingToken.Digit).digit)
        assertEquals("suffix", tokens[4].key)
        assertEquals(" 项", (tokens[4] as RollingToken.StaticText).text)
    }

    @Test
    fun testTokenizeMoneyPositiveNegativeAndZero() {
        // Positive 1234.56 CNY
        val (tokensPos, textPos) = buildTokensForMoney(
            amountMinor = 123456L,
            currency = "CNY",
            showCurrencySymbol = true,
            showPositiveSign = false,
        )
        assertEquals("¥ 1,234.56", textPos)
        // currency, int_3, sep_3, int_2, int_1, int_0, decimal, frac_1, frac_2
        assertEquals(9, tokensPos.size)
        assertEquals("currency", tokensPos[0].key)
        assertEquals("decimal", tokensPos[6].key)
        assertEquals("frac_1", tokensPos[7].key)
        assertEquals(5, (tokensPos[7] as RollingToken.Digit).digit)
        assertEquals("frac_2", tokensPos[8].key)
        assertEquals(6, (tokensPos[8] as RollingToken.Digit).digit)

        // Negative -50.00 USD
        val (tokensNeg, textNeg) = buildTokensForMoney(
            amountMinor = -5000L,
            currency = "USD",
            showCurrencySymbol = true,
            showPositiveSign = false,
        )
        assertEquals("-$ 50.00", textNeg)
        assertEquals("sign", tokensNeg[0].key)
        assertEquals("-", (tokensNeg[0] as RollingToken.StaticText).text)
        assertEquals("currency", tokensNeg[1].key)
        assertEquals("$ ", (tokensNeg[1] as RollingToken.StaticText).text)

        // Zero 0.00 CNY
        val (tokensZero, textZero) = buildTokensForMoney(
            amountMinor = 0L,
            currency = "CNY",
            showCurrencySymbol = true,
            showPositiveSign = false,
        )
        assertEquals("¥ 0.00", textZero)
    }
}
