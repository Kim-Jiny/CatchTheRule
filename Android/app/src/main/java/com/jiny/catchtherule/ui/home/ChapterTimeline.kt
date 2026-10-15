package com.jiny.catchtherule.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.StarRow
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.modeColor
import com.jiny.catchtherule.ui.theme.modeGradient
import com.jiny.catchtherule.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val NODE = 32.dp
private val NODE_CENTER_Y = 34.dp
private val LINE_X = 16.dp

/** 한 트랙의 이어하기 카드(히어로 스타일) + 챕터 타임라인. 잠금 홈·모드 상세 공용. */
@Composable
fun TrackCampaign(track: String, onPlay: (Int, String) -> Unit) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val total = store.totalCount(track)
    val position = if (total > 0) store.position(progress.currentIndex(track).coerceAtMost(total - 1), track) else null
    val finished = progress.isCampaignFinished(track)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroCard(
            track = track,
            caption = stringResource(if (finished) R.string.home_all_clear else R.string.home_current),
            title = if (finished || position == null) stringResource(R.string.home_congrats)
            else stringResource(R.string.chapter_label, position.first) + " · " + stringResource(R.string.stage_label, position.second),
            subtitle = "${progress.earnedStars(track)} ${stringResource(R.string.stars_of_max, progress.maxStars(track))}",
            fraction = progress.progressFraction(track),
            finished = finished,
            modifier = Modifier.staggerIn(0),
            onContinue = { onPlay(progress.currentIndex(track), track) },
        )
        ChapterTimeline(track, onPlay)
    }
}

@Composable
private fun ChapterTimeline(track: String, onPlay: (Int, String) -> Unit) {
    val store = PuzzleStore.get(LocalContext.current)
    val chapters = store.chapters(track)
    Column {
        chapters.forEachIndexed { i, chapter ->
            ChapterRow(
                track = track, chapter = chapter, index = i,
                isFirst = i == 0, isLast = i == chapters.lastIndex,
                onPlay = onPlay,
            )
        }
    }
}

/** 챕터 한 줄 = 왼쪽 노드/연결선 + 챕터 카드 + (펼침 시) 스테이지 칩. */
@Composable
private fun ChapterRow(
    track: String, chapter: Int, index: Int, isFirst: Boolean, isLast: Boolean,
    onPlay: (Int, String) -> Unit,
) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val items = store.puzzlesIn(chapter, track)
    val earned = items.sumOf { progress.starCount(it.id) }
    val maxStars = items.size * 3
    val firstIndex = store.puzzles(track).indexOfFirst { it.chapter == chapter }.coerceAtLeast(0)
    val lastIndex = firstIndex + items.size - 1
    val current = progress.currentIndex(track)
    val unlocked = current >= firstIndex
    val completed = current > lastIndex
    val isCurrent = unlocked && !completed
    val starLevel = if (maxStars == 0) 0 else (earned.toDouble() / maxStars * 3).roundToInt()
    val solved = (current - firstIndex).coerceIn(0, items.size)
    val fraction = if (items.isEmpty()) 0f else solved.toFloat() / items.size

    var expanded by remember(track, chapter) { mutableStateOf(false) }
    val color = modeColor(track)
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxWidth()
            .staggerIn(1 + index.coerceAtMost(6), 40)
            .drawBehind {
                val x = LINE_X.toPx()
                val cy = NODE_CENTER_Y.toPx()
                val w = 3.dp.toPx()
                val idle = Color.White.copy(alpha = 0.10f)
                val done = color.copy(alpha = 0.85f)
                // 위 구간: 이 챕터에 도달했으면 채움
                if (!isFirst) drawLine(if (unlocked) done else idle, Offset(x, 0f), Offset(x, cy), w, StrokeCap.Butt)
                // 아래 구간: 이 챕터를 끝냈으면 채움
                if (!isLast) drawLine(if (completed) done else idle, Offset(x, cy), Offset(x, size.height), w, StrokeCap.Butt)
            }
            .padding(bottom = 12.dp),
    ) {
        TimelineNode(
            track = track, number = chapter, completed = completed, current = isCurrent,
            modifier = Modifier.align(Alignment.TopStart).offset(y = NODE_CENTER_Y - NODE / 2),
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 46.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(shake.value.roundToInt(), 0) }
                    .pressable {
                        if (unlocked) expanded = !expanded
                        else scope.launch {
                            shake.animateTo(0f, keyframes {
                                durationMillis = 360
                                0f at 0; -10f at 60; 10f at 130; -7f at 200; 5f at 270; 0f at 360
                            })
                        }
                    }
                    .neonCard(track, radius = 18.dp, strong = isCurrent, locked = !unlocked)
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.chapter_label, chapter),
                        color = if (unlocked) color else AppColors.TextTertiary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        chapterTitle(track, chapter),
                        color = if (unlocked) AppColors.TextPrimary else AppColors.TextSecondary,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(3.dp))
                    NeonBar(track, if (unlocked) fraction else 0f, height = 4.dp, delayMs = 100 + index * 40)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StarRow(if (unlocked) starLevel else 0)
                    Text("$earned/$maxStars", color = AppColors.TextTertiary, fontSize = 12.sp)
                }
                if (unlocked) {
                    Icon(
                        Icons.Filled.ExpandMore, null, tint = AppColors.TextTertiary,
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && unlocked,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.indices.chunked(3).forEachIndexed { rowIdx, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { offset ->
                                val puzzle = items[offset]
                                StageChip(
                                    track = track, stage = offset + 1, stars = progress.starCount(puzzle.id),
                                    state = when {
                                        firstIndex + offset < current -> StageState.Done
                                        firstIndex + offset == current -> StageState.Current
                                        else -> StageState.Locked
                                    },
                                    order = rowIdx * 3 + offset % 3,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPlay(firstIndex + offset, track) },
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private enum class StageState { Done, Current, Locked }

@Composable
private fun StageChip(
    track: String, stage: Int, stars: Int, state: StageState, order: Int,
    modifier: Modifier, onClick: () -> Unit,
) {
    val color = modeColor(track)
    val motion = rememberMotionEnabled()
    val pulse = if (state == StageState.Current && motion) {
        val t = rememberInfiniteTransition(label = "stage-pulse")
        val v by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), "p")
        v
    } else 1f
    val shape = RoundedCornerShape(14.dp)
    val bg = when (state) {
        StageState.Done -> color.copy(alpha = 0.22f)
        StageState.Current -> color.copy(alpha = 0.10f)
        StageState.Locked -> Color.White.copy(alpha = 0.04f)
    }
    val borderColor = when (state) {
        StageState.Done -> color.copy(alpha = 0.65f)
        StageState.Current -> color.copy(alpha = pulse)
        StageState.Locked -> Color.White.copy(alpha = 0.08f)
    }
    Column(
        modifier
            .staggerIn(order, 40)
            .pressable(enabled = state != StageState.Locked, onClick = onClick)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .graphicsLayer { alpha = if (state == StageState.Locked) 0.45f else 1f }
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.stage_label, stage),
            color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        if (state == StageState.Locked) {
            Icon(Icons.Filled.Lock, null, tint = AppColors.TextTertiary, modifier = Modifier.size(12.dp))
        } else {
            StarRow(stars, size = 12)
        }
    }
}

/** 타임라인 원형 노드: 완료=채움+체크+글로우, 현재=펄스 링(1.6초), 잠김=흐린 자물쇠. */
@Composable
private fun TimelineNode(track: String, number: Int, completed: Boolean, current: Boolean, modifier: Modifier) {
    val color = modeColor(track)
    val motion = rememberMotionEnabled()
    Box(modifier.size(NODE), contentAlignment = Alignment.Center) {
        if (current) {
            val ringScale: Float
            val ringAlpha: Float
            if (motion) {
                val t = rememberInfiniteTransition(label = "node-pulse")
                val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), "p")
                ringScale = 1f + 0.7f * p
                ringAlpha = 0.7f * (1f - p)
            } else {
                ringScale = 1.25f; ringAlpha = 0.35f
            }
            Box(
                Modifier.size(NODE)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .border(2.dp, color, CircleShape),
            )
        }
        when {
            completed -> Box(
                Modifier.size(NODE - 4.dp)
                    .drawBehind {
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(color.copy(alpha = 0.5f), Color.Transparent), radius = size.width,
                            ),
                            radius = size.width,
                        )
                    }
                    .clip(CircleShape).background(modeGradient(track)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
            current -> Box(
                Modifier.size(NODE - 4.dp).clip(CircleShape).background(AppColors.BgElevated).border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$number", color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            else -> Box(
                Modifier.size(NODE - 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, null, tint = AppColors.TextTertiary, modifier = Modifier.size(14.dp)) }
        }
    }
}
