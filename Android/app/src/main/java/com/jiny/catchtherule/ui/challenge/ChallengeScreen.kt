package com.jiny.catchtherule.ui.challenge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.RankEntry
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.home.pressable
import com.jiny.catchtherule.ui.home.rememberCountUp
import com.jiny.catchtherule.ui.home.staggerIn
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.rememberMotionEnabled
import kotlin.math.roundToInt

private val Amber = Color(0xFFFFB020)
private val Pink = Color(0xFFFF5C8A)
private val Gold = Color(0xFFFFD166)
private val Silver = Color(0xFFC0C8D8)
private val Bronze = Color(0xFFFF9F6B)
private val ChallengeBrush = Brush.linearGradient(listOf(Amber, Pink))
private val TNUM = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun ChallengeScreen(modifier: Modifier = Modifier, onStart: () -> Unit) {
    val progress = LocalProgress.current
    var entries by remember { mutableStateOf<List<RankEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(progress.bestTimeAttack, progress.nickname) {
        loading = true
        val res = runCatching { Ranking.service.leaderboard(GameMode.TimeAttack) }
        failed = res.isFailure
        val me = progress.nickname
        entries = res.getOrDefault(emptyList()).map { it.copy(isMe = me.isNotEmpty() && it.nickname == me && it.score == progress.bestTimeAttack) }
        loading = false
    }

    ScreenBackground(animated = true) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChallengeHeader(Modifier.staggerIn(0))
            BestHero(progress.bestTimeAttack, onStart, Modifier.staggerIn(1))

            // 도전탭 배너 (타임어택 시작 ↔ 랭킹)
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.challenge)

            Text(
                stringResource(R.string.ranking), color = AppColors.TextSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.staggerIn(2),
            )
            when {
                loading -> SkeletonList()
                entries.isEmpty() -> {
                    MyRankCard(progress.nickname, progress.bestTimeAttack, entries, Modifier.staggerIn(3))
                    EmptyCard(failed, Modifier.staggerIn(4))
                }
                else -> {
                    MyRankCard(progress.nickname, progress.bestTimeAttack, entries, Modifier.staggerIn(3))
                    val tiedRanks = entries.groupingBy { it.rank }.eachCount().filter { it.value > 1 }.keys
                    // 포디움: 서버 순서 상위 3개, 가운데=[0] 왼쪽=[1] 오른쪽=[2]
                    Podium(entries.take(3), tiedRanks, Modifier.staggerIn(4))
                    entries.drop(3).forEachIndexed { i, e ->
                        LeaderboardRow(e, e.rank in tiedRanks, if (i < 8) Modifier.staggerIn(5 + i, 40) else Modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun TiedChip() {
    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(stringResource(R.string.ranking_tied), color = AppColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MyRankCard(nickname: String, best: Int, entries: List<RankEntry>, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    // 서버에 내 닉네임+최고점으로 등록된 항목이 있으면(점수 0점 포함) 그 순위를 그대로 쓴다.
    val mine = entries.firstOrNull { nickname.isNotEmpty() && it.nickname == nickname && it.score == best }
    val played = best > 0 || mine != null
    val myRank = mine?.rank ?: (entries.count { it.score > best } + 1)
    val outside = mine == null && entries.size >= 100 && best < (entries.lastOrNull()?.score ?: 0)
    val tied = mine?.let { m -> entries.count { it.rank == m.rank } > 1 } ?: entries.any { it.score == best }
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(AppColors.Accent.copy(alpha = 0.14f).compositeOnCard())
            .border(1.5.dp, AppColors.Accent.copy(alpha = 0.8f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.ranking_my_rank), color = AppColors.Accent2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (played) {
            Text(
                if (outside) stringResource(R.string.ranking_out_of_top, 100) else stringResource(R.string.ranking_rank_n, myRank),
                style = TNUM.copy(color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            if (tied && !outside) TiedChip()
            Box(Modifier.weight(1f))
            Text("$best", style = TNUM.copy(color = AppColors.Accent2, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
        } else {
            Text(
                stringResource(R.string.ranking_no_record), color = AppColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChallengeHeader(modifier: Modifier) {
    val motion = rememberMotionEnabled()
    val pulse = if (motion) {
        val t = rememberInfiniteTransition(label = "trophy")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), "p").value
    } else 0.5f
    Row(modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.challenge_title),
                style = TextStyle(brush = ChallengeBrush, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(stringResource(R.string.challenge_subtitle), color = AppColors.TextSecondary, fontSize = 14.sp)
        }
        Box(
            Modifier.size(44.dp).drawBehind {
                val pad = 10.dp.toPx() * (0.7f + 0.5f * pulse)
                drawCircle(
                    Brush.radialGradient(listOf(Amber.copy(alpha = 0.15f + 0.3f * pulse), Color.Transparent), radius = size.width / 2 + pad),
                    radius = size.width / 2 + pad,
                )
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.EmojiEvents, null, tint = Amber, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun BestHero(best: Int, onStart: () -> Unit, modifier: Modifier) {
    val motion = rememberMotionEnabled()
    val bolt = if (motion) {
        val t = rememberInfiniteTransition(label = "bolt")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "b").value
    } else 0.5f
    val score by rememberCountUp(best.toFloat(), 900)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val pad = 16.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(Amber.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) / 2 + pad,
                    ),
                    topLeft = Offset(-pad, -pad),
                    size = Size(size.width + pad * 2, size.height + pad * 2),
                    cornerRadius = CornerRadius(24.dp.toPx() + pad),
                )
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Amber.copy(alpha = 0.18f).compositeOnCard(), Pink.copy(alpha = 0.10f).compositeOnCard()),
                ),
            )
            .border(1.dp, Brush.linearGradient(listOf(Amber.copy(alpha = 0.8f), Pink.copy(alpha = 0.8f))), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.challenge_best), color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${score.roundToInt()}",
                    style = TNUM.copy(brush = ChallengeBrush, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
            Box(
                Modifier.size(64.dp)
                    .drawBehind {
                        val pad = 14.dp.toPx() * (0.7f + 0.6f * bolt)
                        drawCircle(
                            Brush.radialGradient(listOf(Amber.copy(alpha = 0.25f + 0.35f * bolt), Color.Transparent), radius = size.width / 2 + pad),
                            radius = size.width / 2 + pad,
                        )
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(ChallengeBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt, null, tint = Color.White,
                    modifier = Modifier.size(34.dp).graphicsLayer { val sc = 0.92f + 0.16f * bolt; scaleX = sc; scaleY = sc },
                )
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Info, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.challenge_mode_note), color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
        }
        ChallengeShineButton(stringResource(R.string.challenge_start), onStart)
    }
}

private fun Color.compositeOnCard(): Color = compositeOver(AppColors.Card)

/** ShineButton(HomeHero) 와 동일한 펄스 글로우 + 샤인 스윕, 도전 전용 그라데이션. */
@Composable
private fun ChallengeShineButton(text: String, onClick: () -> Unit) {
    val motion = rememberMotionEnabled()
    val pulse: Float
    val sweep: Float
    if (motion) {
        val t = rememberInfiniteTransition(label = "shine")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), "pulse")
        val sw by t.animateFloat(
            -0.4f, -0.4f,
            infiniteRepeatable(keyframes { durationMillis = 3000; -0.4f at 0; 1.4f at 900 using LinearEasing; 1.4f at 3000 }),
            "sweep",
        )
        pulse = p; sweep = sw
    } else { pulse = 0.5f; sweep = -1f }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .drawBehind {
                val pad = 8.dp.toPx() * (0.6f + 0.6f * pulse)
                drawRoundRect(
                    color = Amber.copy(alpha = 0.18f + 0.22f * pulse),
                    topLeft = Offset(-pad, -pad / 2),
                    size = Size(size.width + pad * 2, size.height + pad),
                    cornerRadius = CornerRadius(16.dp.toPx() + pad),
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(ChallengeBrush)
            .pressable(onClick = onClick)
            .drawBehind {
                if (sweep > -0.3f && sweep < 1.3f) {
                    val bandW = size.width * 0.28f
                    val x = size.width * sweep
                    clipRect {
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent), startX = x - bandW, endX = x + bandW))
                    }
                }
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun medalColor(rank: Int) = when { rank <= 1 -> Gold; rank == 2 -> Silver; else -> Bronze }

@Composable
private fun Podium(list: List<RankEntry>, tiedRanks: Set<Int>, modifier: Modifier) {
    // 표시 순서: 왼쪽=[1], 가운데=[0], 오른쪽=[2] (있는 만큼만)
    val order = listOfNotNull(list.getOrNull(1), list.getOrNull(0), list.getOrNull(2))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { e -> PodiumCard(e, e.rank in tiedRanks, Modifier.weight(1f)) }
    }
}

@Composable
private fun PodiumCard(entry: RankEntry, tied: Boolean, modifier: Modifier) {
    val first = entry.rank == 1
    val c = medalColor(entry.rank)
    val shape = RoundedCornerShape(20.dp)
    val score by rememberCountUp(entry.score.toFloat(), 900)
    val borderColor = if (entry.isMe) AppColors.Accent else c
    Column(
        modifier
            .drawBehind {
                val pad = 12.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(c.copy(alpha = if (first) 0.34f else 0.2f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) / 2 + pad,
                    ),
                    topLeft = Offset(-pad, -pad), size = Size(size.width + pad * 2, size.height + pad * 2),
                    cornerRadius = CornerRadius(20.dp.toPx() + pad),
                )
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.16f).compositeOnCard(), c.copy(alpha = 0.03f).compositeOnCard())))
            .border(if (entry.isMe) 2.dp else 1.dp, borderColor.copy(alpha = if (first || entry.isMe) 0.9f else 0.55f), shape)
            .padding(horizontal = 8.dp, vertical = if (first) 18.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (first) Text("👑", fontSize = 22.sp)
        Box(
            Modifier.size(if (first) 40.dp else 32.dp).clip(CircleShape).background(c.copy(alpha = 0.22f)).border(1.5.dp, c, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${entry.rank}", color = c, fontSize = if (first) 18.sp else 15.sp, fontWeight = FontWeight.ExtraBold) }
        if (tied) TiedChip()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.flagEmoji?.let { Text(it, fontSize = 14.sp) }
            Text(
                entry.nickname, color = if (entry.isMe) AppColors.Accent2 else AppColors.TextPrimary,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text("${score.roundToInt()}", style = TNUM.copy(color = c, fontSize = if (first) 24.sp else 19.sp, fontWeight = FontWeight.ExtraBold))
        if (entry.isMe) {
            Box(Modifier.clip(CircleShape).background(AppColors.Accent).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(stringResource(R.string.me), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: RankEntry, tied: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (entry.isMe) AppColors.Accent.copy(alpha = 0.14f) else AppColors.Card
    val stroke = if (entry.isMe) AppColors.Accent.copy(alpha = 0.8f) else AppColors.Stroke
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (entry.isMe) 1.5.dp else 1.dp, stroke, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Text("${entry.rank}", style = TNUM.copy(color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        if (tied) TiedChip()
        entry.flagEmoji?.let { Text(it, fontSize = 18.sp) }
        Text(
            entry.nickname,
            color = if (entry.isMe) AppColors.Accent2 else AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (entry.isMe) {
            Box(Modifier.clip(CircleShape).background(AppColors.Accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(stringResource(R.string.me), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.weight(1f))
        Text("${entry.score}", style = TNUM.copy(color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun EmptyCard(failed: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(AppColors.Card).border(1.dp, AppColors.Stroke, shape).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (failed) Icons.Filled.CloudOff else Icons.Filled.EmojiEvents, null, tint = AppColors.TextTertiary, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.ranking_empty), color = AppColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** 스켈레톤 시머: 회색 카드 위로 지나가는 빛. */
@Composable
private fun SkeletonList() {
    val motion = rememberMotionEnabled()
    val x = if (motion) {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1300, easing = LinearEasing)), "x").value
    } else 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) { i ->
            Box(
                Modifier.fillMaxWidth().height(if (i == 0) 96.dp else 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Card)
                    .drawBehind {
                        val w = size.width
                        val band = w * 0.35f
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                                startX = w * x - band, endX = w * x + band,
                            ),
                        )
                    },
            )
        }
    }
}
