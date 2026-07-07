package com.jiny.catchtherule.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.rotate
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
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedMode: String?,
    onSelectMode: (String) -> Unit,
    onBackToHub: () -> Unit,
    onPlay: (Int, String) -> Unit,
) {
    val progress = LocalProgress.current

    // 해금 알림(1회)
    var showUnlock by remember { mutableStateOf(false) }
    LaunchedEffect(progress.isShapesUnlocked) {
        if (progress.isShapesUnlocked && !progress.shapesUnlockSeen) {
            showUnlock = true
            progress.markShapesUnlockSeen()
        }
    }
    if (showUnlock) UnlockDialog { showUnlock = false }

    when {
        selectedMode != null -> ModeDetail(modifier, selectedMode, onBack = onBackToHub, onPlay = onPlay)
        progress.isShapesUnlocked -> Hub(modifier, onSelectMode)
        else -> LockedHome(modifier, onPlay)   // 잠금: 숫자규칙 단일 캠페인(튜토리얼)
    }
}

/** 잠금 상태 홈 — 숫자규칙 캠페인을 직접 표시. */
@Composable
private fun LockedHome(modifier: Modifier, onPlay: (Int, String) -> Unit) {
    ScreenBackground {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeTitle()
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.home)
            TrackCampaign("numbers", onPlay)
        }
    }
}

/** 모드 선택 허브(해금 후). */
@Composable
private fun Hub(modifier: Modifier, onSelectMode: (String) -> Unit) {
    val store = PuzzleStore.get(LocalContext.current)
    ScreenBackground {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeTitle()
            SectionHeader(stringResource(R.string.select_mode))
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.home)
            store.tracks.forEach { track -> ModeCard(track) { onSelectMode(track) } }
        }
    }
}

@Composable
private fun HomeTitle() {
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.app_name), color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_subtitle), color = AppColors.TextSecondary, fontSize = 15.sp)
    }
}

/** 허브의 모드 카드 — 제목 + 완료 %(또는 완료 배지). */
@Composable
private fun ModeCard(track: String, onClick: () -> Unit) {
    val progress = LocalProgress.current
    val fraction = progress.progressFraction(track)
    val finished = progress.isCampaignFinished(track)
    Row(
        Modifier.fillMaxWidth().card().clickable { onClick() }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.AccentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modeIcon(track),
                null, tint = Color.White, modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(modeTitleRes(track)),
                color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
            if (finished) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Verified, null, tint = AppColors.Success, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.mode_complete), color = AppColors.Success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(stringResource(R.string.mode_progress, (fraction * 100).roundToInt()), color = AppColors.TextTertiary, fontSize = 13.sp)
            }
        }
        ProgressRing(fraction, size = 52.dp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(22.dp))
    }
}

/** 모드 상세 페이지 — 헤더(뒤로+모드명) + 캠페인. */
@Composable
private fun ModeDetail(modifier: Modifier, track: String, onBack: () -> Unit, onPlay: (Int, String) -> Unit) {
    ScreenBackground {
        Column(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).card(12.dp).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                Box(Modifier.weight(1f))
                Text(
                    stringResource(modeTitleRes(track)),
                    color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
                Box(Modifier.weight(1f))
                Box(Modifier.size(38.dp))
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TrackCampaign(track, onPlay)
            }
        }
    }
}

/** 해금 알림 팝업. */
@Composable
private fun UnlockDialog(onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().card(20.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.LockOpen, null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.unlock_title), color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.unlock_msg), color = AppColors.TextSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            PrimaryButton(text = stringResource(R.string.unlock_ok), icon = Icons.Filled.PlayArrow, onClick = onClose)
        }
    }
}

/** 한 트랙의 이어하기 카드 + 챕터 목록. 잠금 홈·모드 상세 공용. */
@Composable
private fun TrackCampaign(track: String, onPlay: (Int, String) -> Unit) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    var expanded by remember(track) { mutableStateOf(setOf<Int>()) }
    val total = store.totalCount(track)
    val position = if (total > 0) store.position(progress.currentIndex(track).coerceAtMost(total - 1), track) else null
    val finished = progress.isCampaignFinished(track)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 이어하기 카드
        Column(
            Modifier.fillMaxWidth().card().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (finished) R.string.home_all_clear else R.string.home_current),
                        color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (finished || position == null) stringResource(R.string.home_congrats)
                        else stringResource(R.string.chapter_label, position.first) + " · " + stringResource(R.string.stage_label, position.second),
                        color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
                ProgressRing(progress.progressFraction(track))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Star, null, tint = AppColors.Star, modifier = Modifier.size(16.dp))
                Text("${progress.earnedStars(track)}", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.stars_of_max, progress.maxStars(track)), color = AppColors.TextTertiary, fontSize = 14.sp)
            }

            if (finished) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(AppColors.BgElevated).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.home_wait_update),
                        color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.home_continue),
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(progress.currentIndex(track), track) },
                )
            }
        }

        // 챕터 목록
        store.chapters(track).forEach { chapter ->
            ChapterBlock(
                track = track,
                chapter = chapter,
                expanded = chapter in expanded,
                onToggle = { expanded = if (chapter in expanded) expanded - chapter else expanded + chapter },
                onReplay = { idx -> onPlay(idx, track) },
            )
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, size: androidx.compose.ui.unit.Dp = 62.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
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

/** 챕터 한 칸. 완료(지나간) 챕터는 탭하면 스테이지 목록을 펼쳐 이전 문제를 다시 풀 수 있다. */
@Composable
private fun ChapterBlock(
    track: String,
    chapter: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReplay: (Int) -> Unit,
) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val items = store.puzzlesIn(chapter, track)
    val earned = items.sumOf { progress.starCount(it.id) }
    val maxStars = items.size * 3
    // 해당 챕터의 첫 인덱스(=잠금 해제 기준). order 는 챕터 내 번호라 트랙 인덱스로 비교.
    val firstIndex = store.puzzles(track).indexOfFirst { it.chapter == chapter }.coerceAtLeast(0)
    val lastIndex = firstIndex + items.size - 1
    val current = progress.currentIndex(track)
    val unlocked = current >= firstIndex
    val completed = current > lastIndex   // 챕터 전체를 지나감
    val starLevel = if (maxStars == 0) 0 else (earned.toDouble() / maxStars * 3).roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().card()
                .let { if (completed) it.clickable { onToggle() } else it }
                .padding(16.dp),
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
                Text(chapterTitle(track, chapter), color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chapter_stages, items.size), color = AppColors.TextTertiary, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StarRow(starLevel)
                Text("$earned/$maxStars", color = AppColors.TextTertiary, fontSize = 12.sp)
            }
            if (completed) {
                Icon(
                    Icons.Filled.ExpandMore, null, tint = AppColors.TextTertiary,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }

        if (completed && expanded) {
            items.forEachIndexed { offset, puzzle ->
                StageReplayRow(
                    stage = offset + 1,
                    stars = progress.starCount(puzzle.id),
                    onClick = { onReplay(firstIndex + offset) },
                )
            }
        }
    }
}

/** 완료된 챕터의 스테이지 한 줄 — 누르면 그 스테이지부터 다시 플레이. */
@Composable
private fun StageReplayRow(stage: Int, stars: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp).card().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.stage_label, stage), color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        StarRow(stars, size = 12)
        Box(Modifier.weight(1f))
        Icon(Icons.Filled.Refresh, null, tint = AppColors.Accent2, modifier = Modifier.size(16.dp))
    }
}

private fun modeTitleRes(track: String): Int = when (track) {
    "shapes" -> R.string.mode_shapes
    "logic" -> R.string.mode_logic
    "contradiction" -> R.string.mode_contradiction
    else -> R.string.mode_numbers
}

private fun modeIcon(track: String) = when (track) {
    "shapes" -> Icons.Filled.ChangeHistory
    "logic" -> Icons.Filled.Lightbulb
    "contradiction" -> Icons.Filled.Warning
    else -> Icons.Filled.Numbers
}

@Composable
private fun chapterTitle(track: String, chapter: Int): String {
    if (track == "contradiction") {
        return when (chapter) {
            1 -> stringResource(R.string.contradiction_chapter_1)
            2 -> stringResource(R.string.contradiction_chapter_2)
            3 -> stringResource(R.string.contradiction_chapter_3)
            4 -> stringResource(R.string.contradiction_chapter_4)
            5 -> stringResource(R.string.contradiction_chapter_5)
            6 -> stringResource(R.string.contradiction_chapter_6)
            7 -> stringResource(R.string.contradiction_chapter_7)
            8 -> stringResource(R.string.contradiction_chapter_8)
            9 -> stringResource(R.string.contradiction_chapter_9)
            10 -> stringResource(R.string.contradiction_chapter_10)
            11 -> stringResource(R.string.contradiction_chapter_11)
            12 -> stringResource(R.string.contradiction_chapter_12)
            13 -> stringResource(R.string.contradiction_chapter_13)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
    if (track == "logic") {
        return when (chapter) {
            1 -> stringResource(R.string.logic_chapter_1)
            2 -> stringResource(R.string.logic_chapter_2)
            3 -> stringResource(R.string.logic_chapter_3)
            4 -> stringResource(R.string.logic_chapter_4)
            5 -> stringResource(R.string.logic_chapter_5)
            6 -> stringResource(R.string.logic_chapter_6)
            7 -> stringResource(R.string.logic_chapter_7)
            8 -> stringResource(R.string.logic_chapter_8)
            9 -> stringResource(R.string.logic_chapter_9)
            10 -> stringResource(R.string.logic_chapter_10)
            11 -> stringResource(R.string.logic_chapter_11)
            12 -> stringResource(R.string.logic_chapter_12)
            13 -> stringResource(R.string.logic_chapter_13)
            14 -> stringResource(R.string.logic_chapter_14)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
    if (track == "shapes") {
        return when (chapter) {
            1 -> stringResource(R.string.shape_chapter_1)
            2 -> stringResource(R.string.shape_chapter_2)
            3 -> stringResource(R.string.shape_chapter_3)
            4 -> stringResource(R.string.shape_chapter_4)
            5 -> stringResource(R.string.shape_chapter_5)
            6 -> stringResource(R.string.shape_chapter_6)
            7 -> stringResource(R.string.shape_chapter_7)
            8 -> stringResource(R.string.shape_chapter_8)
            9 -> stringResource(R.string.shape_chapter_9)
            10 -> stringResource(R.string.shape_chapter_10)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
    return when (chapter) {
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
}
