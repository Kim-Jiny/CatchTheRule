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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.ProgressStore
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card
import com.jiny.catchtherule.ui.theme.modeColor
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

    // 세션 시작 시 마지막 플레이 모드 기록(히어로가 사용)
    val play: (Int, String) -> Unit = { index, track ->
        progress.markLastTrack(track)
        onPlay(index, track)
    }

    when {
        selectedMode != null -> ModeDetail(modifier, selectedMode, onBack = onBackToHub, onPlay = play)
        progress.isShapesUnlocked -> Hub(modifier, onSelectMode, play)
        else -> LockedHome(modifier, play)   // 잠금: 숫자규칙 단일 캠페인(튜토리얼)
    }
}

/** 잠금 상태 홈 — 숫자규칙 캠페인을 직접 표시. */
@Composable
private fun LockedHome(modifier: Modifier, onPlay: (Int, String) -> Unit) {
    ScreenBackground(animated = true) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HomeHeader()
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.home)
            TrackCampaign("numbers", onPlay)
        }
    }
}

/** 마지막 플레이 모드(없으면 첫 모드). 이미 완주한 모드면 가장 진행이 덜 된 모드. */
private fun heroTrack(tracks: List<String>, progress: ProgressStore): String {
    val candidate = progress.lastTrack?.takeIf { it in tracks } ?: tracks.first()
    if (!progress.isCampaignFinished(candidate)) return candidate
    return tracks.minByOrNull { progress.progressFraction(it) } ?: candidate
}

/** 모드 선택 허브(해금 후): 헤더 → 이어하기 히어로 → 2열 모드 그리드. */
@Composable
private fun Hub(modifier: Modifier, onSelectMode: (String) -> Unit, onPlay: (Int, String) -> Unit) {
    val store = PuzzleStore.get(LocalContext.current)
    val progress = LocalProgress.current
    val tracks = store.tracks
    ScreenBackground(animated = true) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HomeHeader()
            if (tracks.isNotEmpty()) {
                val hero = heroTrack(tracks, progress)
                val total = store.totalCount(hero)
                val finished = progress.isCampaignFinished(hero)
                val position = if (total > 0) store.position(progress.currentIndex(hero).coerceAtMost(total - 1), hero) else null
                HeroCard(
                    track = hero,
                    caption = stringResource(R.string.home_last_played),
                    title = stringResource(modeTitleRes(hero)),
                    subtitle = if (finished || position == null) stringResource(R.string.home_all_clear)
                    else stringResource(R.string.chapter_label, position.first) + " · " + stringResource(R.string.stage_label, position.second),
                    fraction = progress.progressFraction(hero),
                    finished = finished,
                    modifier = Modifier.staggerIn(1),
                    onOpen = { onSelectMode(hero) },
                    onContinue = { onPlay(progress.currentIndex(hero), hero) },
                )
            }
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.home)
            Text(
                stringResource(R.string.select_mode),
                color = AppColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            tracks.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEachIndexed { col, track ->
                        val total = store.totalCount(track)
                        ModeGridCard(
                            track = track,
                            fraction = progress.progressFraction(track),
                            solved = progress.currentIndex(track).coerceAtMost(total),
                            total = total,
                            finished = progress.isCampaignFinished(track),
                            modifier = Modifier.weight(1f),
                            index = row * 2 + col,
                            onClick = { onSelectMode(track) },
                        )
                    }
                    // 홀수 개면 마지막 카드가 단독 Row 의 weight(1f) 로 가로 꽉 참.
                }
            }
        }
    }
}

/** 앱 타이틀(흰→시안 그라데이션, 위에서 슬라이드 인) + 부제 + 별 합계 칩(카운트업). */
@Composable
private fun HomeHeader() {
    val progress = LocalProgress.current
    val stars by rememberCountUp(progress.totalStars.toFloat(), 700)
    Row(
        Modifier.fillMaxWidth().staggerIn(0, 60),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFF18C8FF))),
                    fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(stringResource(R.string.home_subtitle), color = AppColors.TextSecondary, fontSize = 14.sp)
        }
        Row(
            Modifier.padding(start = 10.dp, top = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Star.copy(alpha = 0.12f))
                .drawBehind { drawRoundRect(AppColors.Star.copy(alpha = 0.35f), Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Filled.Star, null, tint = AppColors.Star, modifier = Modifier.size(15.dp))
            Text(
                stringResource(R.string.home_total_stars, stars.roundToInt()),
                style = TextStyle(fontFeatureSettings = "tnum", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }
}

/** 모드 상세 페이지(챕터 목록) — 모드색 글로우 헤더(뒤로+모드명, 아이콘 워터마크) + 캠페인. */
@Composable
private fun ModeDetail(modifier: Modifier, track: String, onBack: () -> Unit, onPlay: (Int, String) -> Unit) {
    val color = modeColor(track)
    ScreenBackground(animated = true) {
        Column(modifier) {
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.8f,
                        ),
                    )
                },
            ) {
                Icon(
                    modeIcon(track), null, tint = color.copy(alpha = 0.10f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(96.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).card(12.dp).clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                    Box(Modifier.weight(1f))
                    Text(
                        stringResource(modeTitleRes(track)),
                        color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Box(Modifier.weight(1f))
                    Box(Modifier.size(38.dp))
                }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 28.dp),
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

fun modeTitleRes(track: String): Int = when (track) {
    "shapes" -> R.string.mode_shapes
    "logic" -> R.string.mode_logic
    "contradiction" -> R.string.mode_contradiction
    "target" -> R.string.mode_target
    "match" -> R.string.mode_match
    "fragment" -> R.string.mode_fragment
    else -> R.string.mode_numbers
}

@Composable
fun chapterTitle(track: String, chapter: Int): String {
    if (track == "fragment") {
        return when (chapter) {
            1 -> stringResource(R.string.fragment_chapter_1)
            2 -> stringResource(R.string.fragment_chapter_2)
            3 -> stringResource(R.string.fragment_chapter_3)
            4 -> stringResource(R.string.fragment_chapter_4)
            5 -> stringResource(R.string.fragment_chapter_5)
            6 -> stringResource(R.string.fragment_chapter_6)
            7 -> stringResource(R.string.fragment_chapter_7)
            8 -> stringResource(R.string.fragment_chapter_8)
            9 -> stringResource(R.string.fragment_chapter_9)
            10 -> stringResource(R.string.fragment_chapter_10)
            11 -> stringResource(R.string.fragment_chapter_11)
            12 -> stringResource(R.string.fragment_chapter_12)
            13 -> stringResource(R.string.fragment_chapter_13)
            14 -> stringResource(R.string.fragment_chapter_14)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
    if (track == "match") {
        return when (chapter) {
            1 -> stringResource(R.string.match_chapter_1)
            2 -> stringResource(R.string.match_chapter_2)
            3 -> stringResource(R.string.match_chapter_3)
            4 -> stringResource(R.string.match_chapter_4)
            5 -> stringResource(R.string.match_chapter_5)
            6 -> stringResource(R.string.match_chapter_6)
            7 -> stringResource(R.string.match_chapter_7)
            8 -> stringResource(R.string.match_chapter_8)
            9 -> stringResource(R.string.match_chapter_9)
            10 -> stringResource(R.string.match_chapter_10)
            11 -> stringResource(R.string.match_chapter_11)
            12 -> stringResource(R.string.match_chapter_12)
            13 -> stringResource(R.string.match_chapter_13)
            14 -> stringResource(R.string.match_chapter_14)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
    if (track == "target") {
        return when (chapter) {
            1 -> stringResource(R.string.target_chapter_1)
            2 -> stringResource(R.string.target_chapter_2)
            3 -> stringResource(R.string.target_chapter_3)
            4 -> stringResource(R.string.target_chapter_4)
            5 -> stringResource(R.string.target_chapter_5)
            6 -> stringResource(R.string.target_chapter_6)
            7 -> stringResource(R.string.target_chapter_7)
            8 -> stringResource(R.string.target_chapter_8)
            9 -> stringResource(R.string.target_chapter_9)
            10 -> stringResource(R.string.target_chapter_10)
            11 -> stringResource(R.string.target_chapter_11)
            12 -> stringResource(R.string.target_chapter_12)
            13 -> stringResource(R.string.target_chapter_13)
            14 -> stringResource(R.string.target_chapter_14)
            else -> stringResource(R.string.chapter_label, chapter)
        }
    }
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
