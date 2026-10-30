package com.diell.iptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.diell.iptv.R
import com.diell.iptv.ui.theme.Ink
import com.diell.iptv.ui.theme.PanelHigh
import com.diell.iptv.ui.theme.Sun

/** The Diell sun from the brand logo. */
@Composable
fun SunMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.diell_sun),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun DiellWordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SunMark(if (compact) 34.dp else 48.dp)
        Spacer(Modifier.width(8.dp))
        Text("Diell", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 20.sp else 26.sp)
        Spacer(Modifier.width(6.dp))
        Text("IPTV", color = Sun, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 20.sp else 26.sp)
    }
}

/** Yellow outline when the item has D-pad focus (Android TV). */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.hasFocus }
        .border(BorderStroke(2.dp, if (focused) Sun else Color.Transparent), shape)
}

/** Channel logo, falling back to the channel's initials when there is no logo or it fails to load. */
@Composable
fun ChannelLogo(name: String, logo: String, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(PanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), color = Sun, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
        if (logo.isNotBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun initials(name: String): String =
    name.filter { it.isLetterOrDigit() || it == ' ' }
        .trim().split(Regex("\\s+"))
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "TV" }

val GapS = Arrangement.spacedBy(8.dp)
val GapM = Arrangement.spacedBy(12.dp)
