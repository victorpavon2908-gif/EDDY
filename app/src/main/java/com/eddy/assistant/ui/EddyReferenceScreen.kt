package com.eddy.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EddyReferenceScreen(
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    voiceReady: Boolean,
    autoListeningEnabled: Boolean,
) {
    val statusText = when {
        !autoListeningEnabled -> "Micrófono desactivado"
        !voiceReady -> "Iniciando voz"
        visualState == EddyVisualState.LISTENING -> "Escuchando"
        visualState == EddyVisualState.THINKING -> "Pensando"
        visualState == EddyVisualState.SPEAKING -> "Hablando"
        else -> "Listo"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopChrome(
            statusText = statusText,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        EddyHero(
            state = visualState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 108.dp, bottom = 128.dp),
        )

        BottomMessageCard(
            heardText = heardText,
            responseText = responseText,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopChrome(
    statusText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp),
    ) {
        MiniGridIcon(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = 26.dp)
                .size(30.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "EDDY",
                color = EddyBlack,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 11.sp,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Canvas(modifier = Modifier.size(9.dp)) {
                    drawCircle(color = EddyMint)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusText,
                    color = EddyBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.2.sp,
                )
            }
        }

        SliderIcon(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 28.dp, top = 26.dp)
                .size(32.dp),
        )
    }
}

@Composable
private fun BottomMessageCard(
    heardText: String,
    responseText: String,
    modifier: Modifier = Modifier,
) {
    val visibleText = when {
        responseText.isNotBlank() -> responseText
        heardText.isNotBlank() -> heardText
        else -> "Listo para ayudarte."
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 14.dp)
            .height(86.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x22000000),
                spotColor = Color(0x22000000),
            ),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniEddyMark(modifier = Modifier.size(40.dp))

            Spacer(Modifier.width(14.dp))

            Canvas(modifier = Modifier.size(width = 1.dp, height = 48.dp)) {
                drawLine(
                    color = EddySoftGray,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = size.width,
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = "EDDY:",
                color = EddyMint,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = visibleText,
                color = EddyBlack,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) {
                    Canvas(modifier = Modifier.size(5.dp)) {
                        drawCircle(EddyMint)
                    }
                }
            }
        }
    }
}
