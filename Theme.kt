package com.shehta.mushaf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.shehta.mushaf.data.PaperTheme

class MushafColors(
    val paper: Color,
    val ink: Color,
    val marker: Color,
    val gharib: Color,
    val frame: Color,
    val frameFill: Color,
    val ayahHighlight: Color,
    val wordHighlight: Color,
    val muted: Color,
    val ribbon: Color,
    val bar: Color,
    val onBar: Color,
    val playing: Color = Color(0x2E1E6FD9),
    val playingWord: Color = Color(0x661E6FD9),
    val mistake: Color = Color(0xFFC62828),
    val tajweed: List<Color> = listOf(
        Color(0xFF169B4F), Color(0xFFB98A2E), Color(0xFFD89A1E), Color(0xFFF07A1A),
        Color(0xFFE0201C), Color(0xFF9C0F0F), Color(0xFF1E6FD9), Color(0xFF9E9E9E),
    ),
)

/** Note highlight colours (soft, so the text stays readable). */
val NoteColors = listOf(Color(0x55F2C94C), Color(0x5556CCF2), Color(0x556FCF97), Color(0x55F2994A), Color(0x55BB6BD9))

fun mushafColors(t: PaperTheme) = when (t) {
    PaperTheme.CREAM -> MushafColors(
        paper = Color(0xFFFBF6E9), ink = Color(0xFF1B1B1B), marker = Color(0xFF9A7420),
        gharib = Color(0xFF16705A), frame = Color(0xFFB08A3E), frameFill = Color(0xFFF3E7C6),
        ayahHighlight = Color(0x332E7D67), wordHighlight = Color(0x5546A589), muted = Color(0xFF8C8270),
        ribbon = Color(0xFFB0303F), bar = Color(0xF20F4C3A), onBar = Color(0xFFFDF8EC),
    )
    PaperTheme.WHITE -> MushafColors(
        paper = Color(0xFFFFFFFF), ink = Color(0xFF111111), marker = Color(0xFF9A7420),
        gharib = Color(0xFF0F6E8C), frame = Color(0xFF8E9AA3), frameFill = Color(0xFFF1F4F6),
        ayahHighlight = Color(0x2A0F6E8C), wordHighlight = Color(0x4D0F6E8C), muted = Color(0xFF8A9096),
        ribbon = Color(0xFFB0303F), bar = Color(0xF21F2A33), onBar = Color(0xFFFFFFFF),
    )
    PaperTheme.NIGHT -> MushafColors(
        paper = Color(0xFF121416), ink = Color(0xFFE6E0D2), marker = Color(0xFFC9A55A),
        gharib = Color(0xFF6CCBAE), frame = Color(0xFF8B7440), frameFill = Color(0xFF1D2124),
        ayahHighlight = Color(0x335FB49C), wordHighlight = Color(0x5546A589), muted = Color(0xFF7D7A72),
        ribbon = Color(0xFFD0505E), bar = Color(0xF2181C1F), onBar = Color(0xFFE6E0D2),
        playing = Color(0x334F8FE8), playingWord = Color(0x704F8FE8), mistake = Color(0xFFFF6B6B),
        tajweed = listOf(
            Color(0xFF4CC97E), Color(0xFFD9B060), Color(0xFFE8B04A), Color(0xFFFF9A45),
            Color(0xFFFF5A52), Color(0xFFE04848), Color(0xFF64A8FF), Color(0xFF8C8C8C),
        ),
    )
}

@Composable
fun MushafTheme(t: PaperTheme, content: @Composable () -> Unit) {
    val scheme = if (t == PaperTheme.NIGHT) darkColorScheme(
        primary = Color(0xFF6CCBAE), secondary = Color(0xFFC9A55A),
        background = Color(0xFF121416), surface = Color(0xFF1A1D20),
    ) else lightColorScheme(
        primary = Color(0xFF0F4C3A), secondary = Color(0xFF9A7420),
        background = if (t == PaperTheme.CREAM) Color(0xFFFBF6E9) else Color.White,
        surface = if (t == PaperTheme.CREAM) Color(0xFFFFFBF2) else Color.White,
        surfaceContainerLow = if (t == PaperTheme.CREAM) Color(0xFFF6EEDB) else Color(0xFFF5F6F7),
    )
    // The whole app is Arabic: force right-to-left even if the phone language is English.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
