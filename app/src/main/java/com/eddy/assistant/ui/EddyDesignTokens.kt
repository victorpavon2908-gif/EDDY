package com.eddy.assistant.ui

import androidx.compose.ui.graphics.Color

internal val EddyMint = Color(0xFF43DDB3)
internal val EddyBlack = Color(0xFF101010)
internal val EddySoftGray = Color(0xFFE8E8E8)

internal enum class EddyVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
}
