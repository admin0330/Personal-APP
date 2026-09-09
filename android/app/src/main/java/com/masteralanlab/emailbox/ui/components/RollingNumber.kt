/*
 * Rolling Number for Jetpack Compose
 *
 * Inspired by and algorithmically ported from kitlangton/rolling-number:
 * https://github.com/kitlangton/rolling-number
 * Copyright (c) 2026 Kit Langton (MIT License)
 *
 * Native Android Jetpack Compose implementation for Ym1r (Emailbox).
 * Features:
 * - Single-digit independent reel rotation (unchanged digits stay stationary)
 * - Tabular figures fontFeatureSettings = "tnum" to eliminate layout jitter
 * - Bounded cyclic travel distances
 * - Seamless animation interruption without resetting to zero
 * - Complete separation between numeric reels and symbols/currency/signs
 * - Unified TalkBack screen reader accessibility
 */

package com.masteralanlab.emailbox.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

enum class RollDirection {
    Up, Down
}

sealed class RollingToken {
    abstract val key: String

    data class Digit(
        val place: Int,
        val digit: Int,
        override val key: String = if (place >= 0) "int_$place" else "frac_${abs(place)}",
    ) : RollingToken()

    data class StaticText(
        override val key: String,
        val text: String,
    ) : RollingToken()
}

/**
 * Builds sequence of digits between [fromDigit] and [toDigit] strictly bounded in travel.
 */
fun buildReelSequence(fromDigit: Int, toDigit: Int, direction: RollDirection): List<Int> {
    val from = fromDigit.coerceIn(0, 9)
    val to = toDigit.coerceIn(0, 9)
    if (from == to) return listOf(to)

    return if (direction == RollDirection.Up) {
        if (to >= from) {
            (from..to).toList()
        } else {
            (from..9).toList() + (0..to).toList()
        }
    } else {
        if (to <= from) {
            (from downTo to).toList()
        } else {
            (from downTo 0).toList() + (9 downTo to).toList()
        }
    }
}

/**
 * Tokenizes a long integer into digit places and static texts (like commas, prefixes, suffixes).
 */
fun buildTokensForNumber(
    value: Long,
    grouping: Boolean,
    prefix: String,
    suffix: String,
): Pair<List<RollingToken>, String> {
    val isNegative = value < 0
    val absVal = abs(value)
    val absStr = absVal.toString()
    val tokens = mutableListOf<RollingToken>()

    if (prefix.isNotEmpty()) {
        tokens.add(RollingToken.StaticText("prefix", prefix))
    }
    if (isNegative) {
        tokens.add(RollingToken.StaticText("sign", "-"))
    }

    val len = absStr.length
    for (i in 0 until len) {
        val place = len - 1 - i
        val digit = absStr[i].digitToInt()
        tokens.add(RollingToken.Digit(place, digit))

        if (grouping && place > 0 && place % 3 == 0) {
            tokens.add(RollingToken.StaticText("sep_$place", ","))
        }
    }

    if (suffix.isNotEmpty()) {
        tokens.add(RollingToken.StaticText("suffix", suffix))
    }

    val fullFormatted = buildString {
        if (prefix.isNotEmpty()) append(prefix)
        if (isNegative) append("-")
        if (grouping) {
            append(String.format(Locale.US, "%,d", absVal))
        } else {
            append(absVal)
        }
        if (suffix.isNotEmpty()) append(suffix)
    }

    return Pair(tokens, fullFormatted)
}

/**
 * Tokenizes a minor amount (cents / 分) into currency symbol, sign, integer digits,
 * thousands commas, decimal point, and 2-decimal fraction digits.
 */
fun buildTokensForMoney(
    amountMinor: Long,
    currency: String,
    showCurrencySymbol: Boolean,
    showPositiveSign: Boolean,
): Pair<List<RollingToken>, String> {
    val isNegative = amountMinor < 0
    val absMinor = abs(amountMinor)
    val integerPart = absMinor / 100
    val fractionPart = (absMinor % 100).toInt()

    val symbol = when (currency.uppercase(Locale.US)) {
        "CNY", "RMB" -> "¥ "
        "USD" -> "$ "
        "EUR" -> "€ "
        "GBP" -> "£ "
        else -> "$currency "
    }

    val tokens = mutableListOf<RollingToken>()

    if (isNegative) {
        tokens.add(RollingToken.StaticText("sign", "-"))
    } else if (showPositiveSign && amountMinor > 0) {
        tokens.add(RollingToken.StaticText("sign", "+"))
    }

    if (showCurrencySymbol) {
        tokens.add(RollingToken.StaticText("currency", symbol))
    }

    val intStr = integerPart.toString()
    val intLen = intStr.length
    for (i in 0 until intLen) {
        val place = intLen - 1 - i
        val digit = intStr[i].digitToInt()
        tokens.add(RollingToken.Digit(place, digit))
        if (place > 0 && place % 3 == 0) {
            tokens.add(RollingToken.StaticText("sep_$place", ","))
        }
    }

    // Decimal point
    tokens.add(RollingToken.StaticText("decimal", "."))

    // 2 fraction digits: place -1 (tenths), place -2 (hundredths)
    val fracStr = String.format(Locale.US, "%02d", fractionPart)
    tokens.add(RollingToken.Digit(-1, fracStr[0].digitToInt()))
    tokens.add(RollingToken.Digit(-2, fracStr[1].digitToInt()))

    val fullFormatted = buildString {
        if (isNegative) append("-")
        else if (showPositiveSign && amountMinor > 0) append("+")
        if (showCurrencySymbol) append(symbol)
        append(String.format(Locale.US, "%,d.%02d", integerPart, fractionPart))
    }

    return Pair(tokens, fullFormatted)
}

/**
 * Single-digit vertical rolling reel component with bounded travel and tabular metrics.
 */
@Composable
private fun SingleDigitReel(
    targetDigit: Int,
    direction: RollDirection,
    durationMs: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val digitStyle = remember(style) {
        style.copy(fontFeatureSettings = "tnum")
    }

    // Measure exact reference digit "0"
    val sampleMeasure = remember(digitStyle, textMeasurer) {
        textMeasurer.measure("0", digitStyle)
    }
    val digitWidthPx = sampleMeasure.size.width
    val digitHeightPx = sampleMeasure.size.height
    val density = LocalDensity.current
    val digitWidthDp = with(density) { digitWidthPx.toDp() }
    val digitHeightDp = with(density) { digitHeightPx.toDp() }

    var currentDigit by remember { mutableIntStateOf(targetDigit) }
    var sequence by remember { mutableStateOf(listOf(targetDigit)) }
    val progress = remember { Animatable(1f) }
    var isFirstRender by remember { mutableStateOf(true) }

    LaunchedEffect(targetDigit, direction) {
        if (isFirstRender) {
            isFirstRender = false
            currentDigit = targetDigit
            sequence = listOf(targetDigit)
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (targetDigit == currentDigit && progress.value == 1f) {
            return@LaunchedEffect
        }

        // Interruption: sample current displayed digit
        val startDigit = if (progress.value < 1f && sequence.isNotEmpty()) {
            val idx = (progress.value * (sequence.size - 1)).toInt().coerceIn(0, sequence.size - 1)
            sequence[idx]
        } else {
            currentDigit
        }

        currentDigit = targetDigit
        val newSeq = buildReelSequence(startDigit, targetDigit, direction)
        sequence = newSeq

        if (newSeq.size <= 1 || durationMs <= 0) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .size(width = digitWidthDp, height = digitHeightDp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val totalTravel = (sequence.size - 1) * digitHeightPx
        val offsetY = if (direction == RollDirection.Up) {
            -progress.value * totalTravel
        } else {
            -(1f - progress.value) * totalTravel
        }

        Column(
            modifier = Modifier.graphicsLayer {
                translationY = offsetY
            }
        ) {
            val displaySeq = if (direction == RollDirection.Up) sequence else sequence.asReversed()
            displaySeq.forEach { d ->
                Text(
                    text = d.toString(),
                    style = digitStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Reusable Jetpack Compose RollingNumber component.
 * Animates integer numbers with per-digit independent reels.
 */
@Composable
fun RollingNumber(
    value: Long,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    grouping: Boolean = false,
    durationMs: Int = 350,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    var previousValue by remember { mutableLongStateOf(value) }
    var direction by remember { mutableStateOf(RollDirection.Up) }

    LaunchedEffect(value) {
        if (value != previousValue) {
            direction = if (value >= previousValue) RollDirection.Up else RollDirection.Down
            previousValue = value
        }
    }

    val (tokens, fullText) = remember(value, grouping, prefix, suffix) {
        buildTokensForNumber(value, grouping, prefix, suffix)
    }

    Row(
        modifier = modifier
            .animateContentSize(
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
            )
            .clearAndSetSemantics {
                text = AnnotatedString(fullText)
                contentDescription = fullText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tokens.forEach { token ->
            key(token.key) {
                when (token) {
                    is RollingToken.Digit -> {
                        SingleDigitReel(
                            targetDigit = token.digit,
                            direction = direction,
                            durationMs = durationMs,
                            style = style,
                            color = color,
                        )
                    }
                    is RollingToken.StaticText -> {
                        Text(
                            text = token.text,
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    grouping: Boolean = false,
    durationMs: Int = 350,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) = RollingNumber(
    value = value.toLong(),
    modifier = modifier,
    prefix = prefix,
    suffix = suffix,
    grouping = grouping,
    durationMs = durationMs,
    style = style,
    color = color,
)

/**
 * Reusable Jetpack Compose RollingMoney component for financial summaries.
 * Correctly maintains currency, thousand separators, decimals, and sign transitions.
 */
@Composable
fun RollingMoney(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    currency: String = "CNY",
    showCurrencySymbol: Boolean = true,
    showPositiveSign: Boolean = false,
    durationMs: Int = 400,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    var previousAmount by remember { mutableLongStateOf(amountMinor) }
    var direction by remember { mutableStateOf(RollDirection.Up) }

    LaunchedEffect(amountMinor) {
        if (amountMinor != previousAmount) {
            direction = if (amountMinor >= previousAmount) RollDirection.Up else RollDirection.Down
            previousAmount = amountMinor
        }
    }

    val (tokens, fullText) = remember(amountMinor, currency, showCurrencySymbol, showPositiveSign) {
        buildTokensForMoney(amountMinor, currency, showCurrencySymbol, showPositiveSign)
    }

    Row(
        modifier = modifier
            .animateContentSize(
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
            )
            .clearAndSetSemantics {
                text = AnnotatedString(fullText)
                contentDescription = fullText
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tokens.forEach { token ->
            key(token.key) {
                when (token) {
                    is RollingToken.Digit -> {
                        SingleDigitReel(
                            targetDigit = token.digit,
                            direction = direction,
                            durationMs = durationMs,
                            style = style,
                            color = color,
                        )
                    }
                    is RollingToken.StaticText -> {
                        Text(
                            text = token.text,
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact RollingBadge for dock tabs and status indicators.
 */
@Composable
fun RollingBadge(
    count: Int,
    modifier: Modifier = Modifier,
    maxCount: Int = 99,
    containerColor: Color = AppleColors.MusicRed,
    contentColor: Color = Color.White,
) {
    if (count <= 0) return

    val displayCount = count.coerceAtMost(maxCount)
    val suffix = if (count > maxCount) "+" else ""

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        RollingNumber(
            value = displayCount.toLong(),
            suffix = suffix,
            durationMs = 280,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            ),
            color = contentColor,
        )
    }
}
