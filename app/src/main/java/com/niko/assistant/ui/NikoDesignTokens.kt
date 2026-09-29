package com.niko.assistant.ui

import androidx.compose.ui.graphics.Color

internal val NikoMint = Color(0xFF38E3B1)
internal val NikoMintDeep = Color(0xFF14B985)
internal val NikoMintSoft = Color(0xFFDDFBF1)
internal val NikoBlack = Color(0xFF0E1714)
internal val NikoGraphite = Color(0xFF25332E)
internal val NikoSoftGray = Color(0xFFDDE7E3)
internal val NikoCloud = Color(0xFFF3F7F5)
internal val NikoBlue = Color(0xFF6AA6FF)

internal enum class NikoVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
}
