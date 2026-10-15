package com.jiny.catchtherule.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import com.jiny.catchtherule.ui.theme.modeGradient
import kotlin.math.roundToInt

/** 홈 2열 그리드의 모드 카드(높이 ≈ 150). [locked] 면 회색 + 자물쇠(탭 불가). */
@Composable
fun ModeGridCard(
    track: String,
    fraction: Float,
    solved: Int,
    total: Int,
    finished: Boolean,
    modifier: Modifier = Modifier,
    index: Int = 0,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val pct = (fraction * 100).roundToInt()
    val color = if (locked) Color(0xFF6B7280) else modeColor(track)
    Column(
        modifier
            .height(152.dp)
            .staggerIn(2 + index)
            .pressable(enabled = !locked, onClick = onClick)
            .neonCard(track, strong = finished, locked = locked)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            GlowIconTile(track, 46.dp, locked = locked)
            Spacer(Modifier.weight(1f))
            if (locked) {
                Icon(Icons.Filled.Lock, stringResource(R.string.mode_locked), tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
            } else if (finished) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(modeGradient(track)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, stringResource(R.string.mode_complete), tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(modeTitleRes(track)),
            color = if (locked) AppColors.TextSecondary else AppColors.TextPrimary,
            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        NeonBar(track, if (locked) 0f else fraction, delayMs = 150)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$pct%",
                style = TextStyle(fontFeatureSettings = "tnum", color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$solved/$total",
                style = TextStyle(fontFeatureSettings = "tnum", color = AppColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
