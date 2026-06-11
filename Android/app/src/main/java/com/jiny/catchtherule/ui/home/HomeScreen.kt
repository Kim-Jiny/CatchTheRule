package com.jiny.catchtherule.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.SectionHeader
import com.jiny.catchtherule.ui.StarRow
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card
import kotlin.math.roundToInt

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onContinue: () -> Unit) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val position = store.position(progress.currentIndex.coerceAtMost(store.totalCount - 1))

    ScreenBackground {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.app_name), color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_subtitle), color = AppColors.TextSecondary, fontSize = 15.sp)
            }

            // 이어하기 카드
            Column(
                Modifier.fillMaxWidth().card().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (progress.isCampaignFinished) R.string.home_all_clear else R.string.home_current),
                            color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (progress.isCampaignFinished || position == null) stringResource(R.string.home_congrats)
                            else stringResource(R.string.chapter_label, position.first) + " · " + stringResource(R.string.stage_label, position.second),
                            color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    ProgressRing(progress.progressFraction)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Star, null, tint = AppColors.Star, modifier = Modifier.size(16.dp))
                    Text("${progress.totalStars}", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.stars_of_max, progress.maxStars), color = AppColors.TextTertiary, fontSize = 14.sp)
                }

                PrimaryButton(
                    text = stringResource(if (progress.isCampaignFinished) R.string.home_retry else R.string.home_continue),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onContinue,
                )
            }

            // 홈탭 배너 (이어하기 ↔ 챕터)
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.home)

            // 챕터 목록
            SectionHeader(stringResource(R.string.chapters))
            store.chapters.forEach { chapter ->
                ChapterRow(chapter)
            }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(62.dp)) {
            val stroke = 6.dp.toPx()
            drawArc(
                color = AppColors.Stroke,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                brush = AppColors.AccentGradient,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text("${(fraction * 100).roundToInt()}%", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChapterRow(chapter: Int) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val items = store.puzzlesIn(chapter)
    val earned = items.sumOf { progress.starCount(it.id) }
    val maxStars = items.size * 3
    // 해당 챕터의 첫 전역 인덱스에 도달했는지(=잠금 해제). order 는 챕터 내 번호라 전역 인덱스로 비교.
    val firstGlobal = store.puzzles.indexOfFirst { it.chapter == chapter }.coerceAtLeast(0)
    val unlocked = progress.currentIndex >= firstGlobal
    val starLevel = if (maxStars == 0) 0 else (earned.toDouble() / maxStars * 3).roundToInt()

    Row(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val badgeBrush = if (unlocked) AppColors.AccentGradient
        else androidx.compose.ui.graphics.SolidColor(AppColors.Accent.copy(alpha = 0.25f))
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(badgeBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text("$chapter", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(chapterTitle(chapter), color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.chapter_stages, items.size), color = AppColors.TextTertiary, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StarRow(starLevel)
            Text("$earned/$maxStars", color = AppColors.TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun chapterTitle(chapter: Int): String = when (chapter) {
    1 -> stringResource(R.string.chapter_1)
    2 -> stringResource(R.string.chapter_2)
    3 -> stringResource(R.string.chapter_3)
    4 -> stringResource(R.string.chapter_4)
    5 -> stringResource(R.string.chapter_5)
    6 -> stringResource(R.string.chapter_6)
    7 -> stringResource(R.string.chapter_7)
    8 -> stringResource(R.string.chapter_8)
    9 -> stringResource(R.string.chapter_9)
        10 -> stringResource(R.string.chapter_10)
        11 -> stringResource(R.string.chapter_11)
    else -> stringResource(R.string.chapter_label, chapter)
}
