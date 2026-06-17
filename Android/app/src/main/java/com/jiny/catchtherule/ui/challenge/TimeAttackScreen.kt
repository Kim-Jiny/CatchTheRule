package com.jiny.catchtherule.ui.challenge

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.core.model.GameMode
import com.jiny.catchtherule.core.model.InputType
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.data.Ranking
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.SecondaryButton
import com.jiny.catchtherule.ui.play.AnswerFeedback
import com.jiny.catchtherule.ui.play.ChoicesGrid
import com.jiny.catchtherule.ui.play.Keypad
import com.jiny.catchtherule.ui.play.SequenceDisplay
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DURATION = 60

@Composable
fun TimeAttackScreen(onClose: () -> Unit) {
    val progress = LocalProgress.current
    val store = PuzzleStore.get(LocalContext.current)

    // 타임어택은 숫자 규칙(numbers) 트랙만 — 도형 퍼즐은 제외.
    var deck by remember { mutableStateOf(store.puzzles("numbers").shuffled()) }
    var deckIndex by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(DURATION) }
    var feedback by remember { mutableStateOf<AnswerFeedback?>(null) }
    var finished by remember { mutableStateOf(false) }
    // 카드 전환 중 재제출(같은 프레임 더블탭) 방지. deckIndex 가 바뀌면(=다음 카드) 해제.
    var advancing by remember { mutableStateOf(false) }

    // 타이머
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft -= 1
        }
        progress.updateBestTimeAttack(score)
        finished = true
    }

    LaunchedEffect(deckIndex) { advancing = false }

    val puzzle: Puzzle? = deck.getOrNull(deckIndex)

    fun nextCard() {
        typed = ""
        feedback = null
        if (deckIndex + 1 >= deck.size) {
            deck = deck.shuffled(); deckIndex = 0
        } else deckIndex += 1
    }

    fun submit(value: String) {
        if (value.isEmpty() || finished || advancing) return
        val p = puzzle ?: return
        if (p.isCorrect(value)) {
            advancing = true
            score += 1
            feedback = AnswerFeedback.Correct
            nextCard()
        } else {
            feedback = AnswerFeedback.Wrong
            typed = ""
        }
    }

    ScreenBackground {
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

                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        if (puzzle != null) {
                            Text(stringResource(R.string.play_prompt), color = AppColors.TextSecondary, fontSize = 14.sp)
                            SequenceDisplay(puzzle = puzzle, typed = typed, feedback = feedback)
                        }
                    }
                }

                if (puzzle != null) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        when (InputType.from(puzzle.inputType)) {
                            InputType.Keypad -> Keypad(
                                canSubmit = typed.isNotEmpty() && !advancing,
                                onDigit = { if (!advancing && typed.length < 4) typed += it.toString() },
                                onDelete = { if (typed.isNotEmpty()) typed = typed.dropLast(1) },
                                onSubmit = { submit(typed) },
                            )
                            InputType.Choices -> ChoicesGrid(puzzle.choices ?: emptyList(), enabled = !advancing) { submit(it) }
                        }
                    }
                }
            }
        }
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
