package com.jiny.catchtherule.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.InputType
import com.jiny.catchtherule.core.TimeAttackDeck
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.SecondaryButton
import com.jiny.catchtherule.ui.play.AnswerFeedback
import com.jiny.catchtherule.ui.play.ChoicesGrid
import com.jiny.catchtherule.ui.play.FigureChoicesGrid
import com.jiny.catchtherule.ui.play.FillControls
import com.jiny.catchtherule.ui.play.FillPlayArea
import com.jiny.catchtherule.ui.play.FillUi
import com.jiny.catchtherule.ui.play.PathControls
import com.jiny.catchtherule.ui.play.PathPlayArea
import com.jiny.catchtherule.ui.play.PathUi
import com.jiny.catchtherule.ui.play.Keypad
import com.jiny.catchtherule.ui.play.MatchControls
import com.jiny.catchtherule.ui.play.MatchMessage
import com.jiny.catchtherule.ui.play.MatchPlayArea
import com.jiny.catchtherule.ui.play.MatchUi
import com.jiny.catchtherule.ui.play.TargetControls
import com.jiny.catchtherule.ui.play.TargetPlayArea
import com.jiny.catchtherule.ui.play.TargetUi
import com.jiny.catchtherule.ui.play.matchSubmitValue
import com.jiny.catchtherule.ui.play.FragmentPrompt
import com.jiny.catchtherule.ui.play.SequenceDisplay
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.FullScreenBackground
import com.jiny.catchtherule.ui.home.modeIcon
import com.jiny.catchtherule.ui.home.modeTitleRes
import com.jiny.catchtherule.ui.theme.card
import com.jiny.catchtherule.ui.theme.modeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DURATION = 60
private const val SKIP_PENALTY = 3

@Composable
fun TimeAttackScreen(onClose: () -> Unit) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)
    val haptic = LocalHapticFeedback.current

    // 해금된 모든 모드를 섞어 출제. 해금 전에는 숫자 모드뿐.
    val unlocked = progress.isShapesUnlocked
    val deck = remember(unlocked) {
        TimeAttackDeck(
            allPuzzles = { store.puzzles(it) },
            tracks = if (unlocked) store.tracks else listOf(PuzzleStore.DEFAULT_TRACK),
        )
    }
    var puzzle by remember(deck) { mutableStateOf(deck.next()) }
    var typed by remember { mutableStateOf("") }
    // 점수·남은시간·종료여부는 rememberSaveable — 저메모리 프로세스 사망에도 진행 중 기록이 유지된다.
    var score by rememberSaveable { mutableIntStateOf(0) }
    var timeLeft by rememberSaveable { mutableIntStateOf(DURATION) }
    var feedback by remember { mutableStateOf<AnswerFeedback?>(null) }
    var finished by rememberSaveable { mutableStateOf(false) }
    // 카드 전환 중 재제출(같은 프레임 더블탭) 방지. 다음 카드로 넘어가면 해제.
    var advancing by remember { mutableStateOf(false) }

    // 타이머 — 앱이 RESUMED 일 때만 진행(백그라운드에선 멈춤). 실제 플레이 시간만 소진.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft = (timeLeft - 1).coerceAtLeast(0)
            }
        }
    }
    // 종료 처리(시간 소진 또는 건너뛰기 차감으로 0 도달).
    LaunchedEffect(timeLeft) {
        if (timeLeft <= 0 && !finished) {
            progress.updateBestTimeAttack(score)
            finished = true
        }
    }

    fun nextCard() {
        typed = ""
        feedback = null
        puzzle = deck.next()
        advancing = false
    }

    // 정답 후 짧은 지연(0.2s) 뒤 다음 카드.
    LaunchedEffect(advancing) {
        if (advancing) {
            delay(200)
            nextCard()
        }
    }

    // 오답 피드백은 잠시 뒤 해제(다음 오답에서 다시 흔들리도록).
    LaunchedEffect(feedback) {
        if (feedback == AnswerFeedback.Wrong) {
            delay(450)
            typed = ""
            feedback = null
        }
    }

    fun buzz() {
        if (progress.hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun submit(value: String) {
        if (value.isEmpty() || finished || advancing) return
        val p = puzzle ?: return
        if (p.isCorrect(value)) {
            advancing = true
            score += 1
            feedback = AnswerFeedback.Correct
        } else {
            feedback = AnswerFeedback.Wrong
            buzz()
        }
    }

    fun skip() {
        if (finished || advancing) return
        timeLeft = (timeLeft - SKIP_PENALTY).coerceAtLeast(0)
        if (timeLeft > 0) nextCard()
    }

    val current = puzzle
    // 숫자 만들기/성냥개비 보드 상태 — 문제가 바뀌면 반드시 초기화(key = puzzle.id).
    val targetUi = remember(current?.id) { current?.takeIf { it.isTarget }?.let { TargetUi(it) } }
    val matchUi = remember(current?.id) { current?.takeIf { it.isMatch }?.let { MatchUi(it) } }
    val fillUi = remember(current?.id) { current?.takeIf { it.isFill }?.let { FillUi(it) } }
    val pathUi = remember(current?.id) { current?.takeIf { it.isPath }?.let { PathUi(it) } }

    FullScreenBackground {
        if (finished) {
            TimeAttackResult(score = score, onClose = onClose)
        } else {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                // 상단 바
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).card(12.dp).clickable { onClose() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp)) }
                    Box(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Timer, null, tint = if (timeLeft <= 10) AppColors.Danger else AppColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            "%02d:%02d".format(timeLeft / 60, timeLeft % 60),
                            color = if (timeLeft <= 10) AppColors.Danger else AppColors.TextPrimary,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Star, null, tint = AppColors.Star, modifier = Modifier.size(18.dp))
                        Text("$score", color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (current != null) {
                    ModeChip(current.trackKey, Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
                }

                Box(Modifier.weight(1f)) {
                    if (current != null) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                        ) {
                            val promptRes = when {
                                current.isMatch -> R.string.match_prompt
                                current.isTarget -> R.string.target_prompt
                                current.isContradiction -> R.string.contradiction_prompt
                                current.isCipher -> R.string.cipher_prompt
                                current.isScale -> R.string.scale_prompt
                                current.isFill -> R.string.fill_prompt
                                current.isPath -> R.string.path_prompt
                                !current.isPrompt -> R.string.play_prompt
                                else -> null
                            }
                            if (current.isFragment) {
                                FragmentPrompt(current)
                            } else if (promptRes != null) {
                                Text(stringResource(promptRes), color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            }
                            if (current.isFill && fillUi != null) {
                                FillPlayArea(fillUi, advancing, feedback)
                            } else if (current.isPath && pathUi != null) {
                                PathPlayArea(pathUi, advancing, feedback)
                            } else if (current.isMatch && matchUi != null) {
                                MatchPlayArea(matchUi, advancing, feedback, onReject = { buzz() })
                            } else if (current.isTarget && targetUi != null) {
                                TargetPlayArea(current, targetUi, advancing, feedback) { id ->
                                    if (!advancing && !targetUi.tapTile(id)) buzz()
                                }
                            } else {
                                SequenceDisplay(
                                    puzzle = current, typed = typed, feedback = feedback,
                                    onPickStatement = { picked -> submit(picked.toString()) },
                                )
                            }
                        }
                    }
                }

                // 건너뛰기 — 입력 영역 위, 작게 우측 정렬.
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = !advancing) { skip() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.SkipNext, null, tint = AppColors.TextTertiary, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.ta_skip), color = AppColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (current != null) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (current.isFill && fillUi != null) {
                            FillControls(fillUi, advancing) {
                                if (advancing || feedback != null || !fillUi.isComplete) {
                                    // 피드백/전환 중에는 무시
                                } else if (fillUi.conflicts().isNotEmpty()) {
                                    fillUi.flashInvalid()
                                    buzz()
                                } else submit(fillUi.submitValue(current))
                            }
                        } else if (current.isPath && pathUi != null) {
                            PathControls(pathUi, advancing) {
                                if (advancing || feedback != null || pathUi.selected < 0) {
                                    // 피드백/전환 중에는 무시
                                } else submit(pathUi.submitValue())
                            }
                        } else if (current.isMatch && matchUi != null) {
                            MatchControls(matchUi, advancing) {
                                if (advancing || feedback != null || !matchUi.game.canCheck) {
                                    // 피드백/전환 중에는 무시
                                } else {
                                    val v = matchSubmitValue(current, matchUi)
                                    if (v == null) {
                                        matchUi.flash(MatchMessage.Invalid)
                                        buzz()
                                    } else submit(v)
                                }
                            }
                        } else if (current.isTarget && targetUi != null) {
                            TargetControls(
                                targetUi, current.target ?: 0, advancing,
                                onReject = { buzz() },
                                onCheck = {
                                    val v = targetUi.expr.evaluate()
                                    if (advancing || feedback != null) {
                                        // 피드백/전환 중에는 무시
                                    } else if (v == null) {
                                        targetUi.markIncomplete()
                                        buzz()
                                    } else submit(v.toString())
                                },
                            )
                        } else if (current.isContradiction) {
                            // 문장 카드를 직접 탭 — 하단 입력 없음
                        } else if (current.isFigureSequence) {
                            FigureChoicesGrid(current.figureChoices ?: emptyList(), enabled = !advancing) { submit(it) }
                        } else when (InputType.from(current.inputType)) {
                            InputType.Keypad -> Keypad(
                                canSubmit = typed.isNotEmpty() && !advancing,
                                onDigit = { if (!advancing && typed.length < 4) typed += it.toString() },
                                onDelete = { if (typed.isNotEmpty()) typed = typed.dropLast(1) },
                                onSubmit = { submit(typed) },
                            )
                            InputType.Choices -> ChoicesGrid(current.choices ?: emptyList(), enabled = !advancing) { submit(it) }
                        }
                    }
                }
            }
        }
    }
}

/** 현재 문제의 모드 칩(아이콘 + 모드 제목, 모드색). */
@Composable
private fun ModeChip(track: String, modifier: Modifier = Modifier) {
    val color = modeColor(track)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(modeIcon(track), null, tint = color, modifier = Modifier.size(14.dp))
        Text(stringResource(modeTitleRes(track)), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeAttackResult(score: Int, onClose: () -> Unit) {
    val progress = LocalProgress.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(progress.nickname) }
    var submitting by remember { mutableStateOf(false) }
    var myRank by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Flag, null, tint = AppColors.Accent, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ta_end), color = AppColors.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.ta_score, score), color = AppColors.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))

        val rank = myRank
        if (rank != null) {
            Text(stringResource(R.string.ta_rank_registered, rank), color = AppColors.Accent2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Box(
                Modifier.fillMaxWidth().height(50.dp).card(14.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (nickname.isEmpty()) {
                            Text(stringResource(R.string.nickname_placeholder), color = AppColors.TextTertiary, fontSize = 17.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(if (submitting) R.string.registering else R.string.register_ranking),
                icon = Icons.Filled.EmojiEvents,
                enabled = nickname.trim().isNotEmpty() && !submitting,
            ) {
                submitting = true
                val nick = nickname.trim()
                progress.updateNickname(nick)
                scope.launch {
                    myRank = runCatching { Ranking.service.submit(score, nick, GameMode.TimeAttack) }.getOrNull()
                    submitting = false
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SecondaryButton(stringResource(R.string.close)) { onClose() }
    }
}
