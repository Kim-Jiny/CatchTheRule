package com.jiny.catchtherule.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.SectionHeader
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card

@Composable
fun ChallengeScreen(modifier: Modifier = Modifier, onStart: () -> Unit) {
    val progress = LocalProgress.current
    var entries by remember { mutableStateOf<List<RankEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(progress.bestTimeAttack, progress.nickname) {
        loading = true
        val result = runCatching { Ranking.service.leaderboard(GameMode.TimeAttack) }.getOrDefault(emptyList())
        val me = progress.nickname
        entries = result.map { it.copy(isMe = me.isNotEmpty() && it.nickname == me) }
        loading = false
    }

    ScreenBackground {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text("타임어택", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("60초 동안 최대한 많이 풀고 랭킹에 도전하세요", color = AppColors.TextSecondary, fontSize = 14.sp)
            }

            Column(
                Modifier.fillMaxWidth().card().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("내 최고 점수", color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${progress.bestTimeAttack}", color = AppColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Filled.Bolt, null, tint = AppColors.Accent, modifier = Modifier.size(34.dp))
                }
                PrimaryButton("타임어택 시작", icon = Icons.Filled.PlayArrow, onClick = onStart)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("랭킹", Modifier.weight(1f))
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = AppColors.TextSecondary)
            }
            if (entries.isEmpty() && !loading) {
                Text("아직 기록이 없어요. 첫 주자가 되어보세요!", color = AppColors.TextTertiary, fontSize = 14.sp)
            }
            entries.forEach { LeaderboardRow(it) }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: RankEntry) {
    val medal = when (entry.rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null
    }
    val bg = if (entry.isMe) AppColors.Accent.copy(alpha = 0.12f) else AppColors.Card
    val strokeColor = if (entry.isMe) AppColors.Accent.copy(alpha = 0.4f) else AppColors.Stroke

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, strokeColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (medal != null) Text(medal, fontSize = 20.sp)
            else Text("${entry.rank}", color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        entry.flagEmoji?.let { Text(it, fontSize = 18.sp) }
        Text(
            entry.nickname,
            color = if (entry.isMe) AppColors.Accent2 else AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Medium,
        )
        if (entry.isMe) {
            Box(
                Modifier.clip(CircleShape).background(AppColors.Accent).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("나", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.weight(1f))
        Text("${entry.score}", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
