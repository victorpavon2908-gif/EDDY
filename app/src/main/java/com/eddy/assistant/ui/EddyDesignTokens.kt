package com.eddy.assistant.ui

import androidx.compose.ui.graphics.Color

internal val EddyMint = Color(0xFF38E3B1)
internal val EddyMintDeep = Color(0xFF14B985)
internal val EddyMintSoft = Color(0xFFDDFBF1)
internal val EddyBlack = Color(0xFF0E1714)
internal val EddyGraphite = Color(0xFF25332E)
internal val EddySoftGray = Color(0xFFDDE7E3)
internal val EddyCloud = Color(0xFFF3F7F5)
internal val EddyBlue = Color(0xFF6AA6FF)

internal enum class EddyVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
}
