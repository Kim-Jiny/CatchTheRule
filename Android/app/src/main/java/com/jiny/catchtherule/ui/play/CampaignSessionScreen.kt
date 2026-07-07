package com.jiny.catchtherule.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jiny.catchtherule.data.BillingManager
import com.jiny.catchtherule.data.LocalAds
import com.jiny.catchtherule.data.LocalBilling
import com.jiny.catchtherule.R
import com.jiny.catchtherule.core.PuzzleStore
import com.jiny.catchtherule.core.model.InputType
import com.jiny.catchtherule.core.model.Puzzle
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card
import kotlinx.coroutines.delay

@Composable
fun CampaignSessionScreen(startIndex: Int, track: String = PuzzleStore.DEFAULT_TRACK, onClose: () -> Unit) {
    val progress = LocalProgress.current
    val billing = LocalBilling.current
    val ads = LocalAds.current
    val activity = LocalContext.current as? Activity
    val store = PuzzleStore.get(LocalContext.current)
    val puzzles = store.puzzles(track)

    var index by remember { mutableIntStateOf(startIndex) }
    var typed by remember { mutableStateOf("") }
    var hintsShown by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<AnswerFeedback?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    var showHintShop by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }

    val puzzle: Puzzle? = puzzles.getOrNull(index)

    // 오답 시 50% 확률 전면광고(광고제거 구매 시 제외).
    val showWrongAd = {
        if (!billing.removeAdsPurchased && activity != null) ads.maybeShowInterstitialOnWrong(activity)
    }

    // 정답 후 자동 진행
    LaunchedEffect(solved) {
        if (solved) {
            delay(900)
            // 스테이지 클리어 전면광고(챕터 2+ / 챕터별 확률 / 3분 쿨다운). 광고제거 구매 시 제외.
            val ch = puzzle?.chapter ?: 0
            if (!billing.removeAdsPurchased && activity != null) {
                ads.maybeShowInterstitial(activity, ch)
            }
            index += 1
            typed = ""; hintsShown = 0; feedback = null; reveal = false; solved = false
        }
    }

    ScreenBackground {
        if (puzzle == null) {
            CampaignComplete(track, onClose)
            return@ScreenBackground
        }

        val position = store.position(index, track)

        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            // 헤더
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).card(12.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp)) }
                Box(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.chapter_label, position?.first ?: puzzle.chapter), color = AppColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.stage_label, position?.second ?: puzzle.order), color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).card(12.dp).clickable { showCalc = !showCalc },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Calculate, null, tint = if (showCalc) AppColors.Accent2 else AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                Box(Modifier.size(8.dp))
                HintButton(
                    remaining = progress.hintsRemaining,
                    enabled = hintsShown < puzzle.localizedHints.size && !solved,
                ) {
                    if (progress.hintsRemaining > 0) {
                        if (progress.spendHint()) hintsShown += 1
                    } else {
                        showHintShop = true
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                // 가운데 영역만 스크롤: 힌트가 늘어도 키패드·배너는 안 밀린다.
                // 내용이 짧으면 CenterVertically 로 기존처럼 가운데 정렬.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                ) {
                    if (puzzle.isContradiction) {
                        Text(stringResource(R.string.contradiction_prompt), color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    } else if (!puzzle.isPrompt) {
                        Text(stringResource(R.string.play_prompt), color = AppColors.TextSecondary, fontSize = 15.sp)
                    }
                    SequenceDisplay(puzzle = puzzle, typed = typed, reveal = reveal, feedback = feedback,
                        onPickStatement = { picked ->
                            if (!solved) {
                                typed = picked.toString()
                                submitCampaign(puzzle, index, picked.toString(), progress, hintsShown,
                                    onCorrect = { feedback = AnswerFeedback.Correct; reveal = true; solved = true },
                                    onWrong = { feedback = AnswerFeedback.Wrong; showWrongAd() })
                            }
                        })
                    if (hintsShown > 0) {
                        Column(
                            Modifier.fillMaxWidth().card().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            puzzle.localizedHints.take(hintsShown).forEach { hint ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Lightbulb, null, tint = AppColors.Star, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                    Text(hint, color = AppColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 입력 영역 (모순찾기는 문장 카드를 직접 탭하므로 하단 입력 없음)
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (puzzle.isContradiction) {
                    // no bottom input
                } else if (puzzle.isFigureSequence) {
                    // 시각형(도형 시퀀스)은 도형 보기로 고른다.
                    FigureChoicesGrid(puzzle.figureChoices ?: emptyList(), enabled = !solved) { picked ->
                        submitCampaign(puzzle, index, picked, progress, hintsShown,
                            onCorrect = { feedback = AnswerFeedback.Correct; reveal = true; solved = true },
                            onWrong = { feedback = AnswerFeedback.Wrong; showWrongAd() })
                    }
                } else when (InputType.from(puzzle.inputType)) {
                    InputType.Keypad -> Keypad(
                        canSubmit = typed.isNotEmpty() && !solved,
                        onDigit = { if (!solved && typed.length < 4) typed += it.toString() },
                        onDelete = { if (!solved && typed.isNotEmpty()) typed = typed.dropLast(1) },
                        onSubmit = { submitCampaign(puzzle, index, typed, progress, hintsShown,
                            onCorrect = { feedback = AnswerFeedback.Correct; reveal = true; solved = true },
                            onWrong = { feedback = AnswerFeedback.Wrong; typed = ""; showWrongAd() }) },
                    )
                    InputType.Choices -> ChoicesGrid(puzzle.choices ?: emptyList(), enabled = !solved) { picked ->
                        submitCampaign(puzzle, index, picked, progress, hintsShown,
                            onCorrect = { feedback = AnswerFeedback.Correct; reveal = true; solved = true },
                            onWrong = { feedback = AnswerFeedback.Wrong; showWrongAd() })
                    }
                }
            }

            // 스테이지 배너 (맨 아래)
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.stage)
        }

            AnimatedVisibility(
                visible = feedback == AnswerFeedback.Correct,
                modifier = Modifier.align(Alignment.Center).offset(y = (-150).dp),
                enter = scaleIn(initialScale = 0.5f) + fadeIn(),
                exit = scaleOut(targetScale = 0.5f) + fadeOut(),
            ) { CorrectBadge() }

            if (showCalc) {
                Box(Modifier.align(Alignment.Center)) { CalculatorPanel { showCalc = false } }
            }
        }

        if (showHintShop) {
            HintShopDialog(billing = billing) { showHintShop = false }
        }
    }
}

/** 힌트 구매 팝업(5·10·20·50). 캠페인의 힌트 0 상황 + 설정의 "힌트 구매"에서 공용. */
@Composable
fun HintShopDialog(billing: BillingManager, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val ads = LocalAds.current
    val progress = LocalProgress.current

    // 팝업이 떠 있는 동안 광고를 미리 로드해 둔다.
    LaunchedEffect(Unit) { ads.load() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().card(20.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lightbulb, null, tint = AppColors.Star, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.iap_need_hints_title), color = AppColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.iap_need_hints_msg), color = AppColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)

            // 광고 보고 힌트 받기 (+1) — 준비됐을 때만 활성
            Row(
                Modifier.fillMaxWidth().card().clickable(enabled = ads.isReady) {
                    activity?.let { act ->
                        if (ads.showRewarded(act) { progress.addHints(1) }) onDismiss()
                    }
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayCircle, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                Box(Modifier.size(8.dp))
                Text(stringResource(R.string.iap_watch_ad), color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f))
                Text(
                    if (ads.isReady) "+1" else stringResource(R.string.iap_loading),
                    color = if (ads.isReady) AppColors.Accent else AppColors.TextTertiary,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            // 힌트 구매 (4 티어)
            BillingManager.HINT_TIERS.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().card().clickable {
                        activity?.let { billing.purchase(it, BillingManager.hintsId(n)); onDismiss() }
                    }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lightbulb, null, tint = AppColors.Star, modifier = Modifier.size(18.dp))
                    Box(Modifier.size(8.dp))
                    Text(stringResource(R.string.iap_hints_n, n), color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.weight(1f))
                    Text(
                        billing.hintsPrices[n].orEmpty().ifEmpty { stringResource(R.string.iap_loading) },
                        color = AppColors.Accent2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 결제 전 환불·이용약관 고지
            Text(
                stringResource(R.string.iap_refund_policy),
                color = AppColors.Accent2, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    val lang = java.util.Locale.getDefault().language
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://duo.jiny.shop/ctr/terms?lang=$lang")))
                }.padding(4.dp),
            )

            Text(
                stringResource(R.string.close),
                color = AppColors.TextSecondary, fontSize = 14.sp,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
            )
        }
    }
}

private fun submitCampaign(
    puzzle: Puzzle,
    index: Int,
    value: String,
    progress: com.jiny.catchtherule.data.ProgressStore,
    hintsShown: Int,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
) {
    if (value.isEmpty()) return
    if (puzzle.isCorrect(value)) {
        val earned = maxOf(1, 3 - hintsShown)
        progress.recordCampaignClear(puzzle, index, earned)
        onCorrect()
    } else {
        onWrong()
    }
}

@Composable
private fun HintButton(remaining: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(38.dp).card(12.dp).clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.Lightbulb, null, tint = if (enabled) AppColors.Star else AppColors.TextTertiary, modifier = Modifier.size(16.dp))
        Text("$remaining", color = if (enabled) AppColors.Star else AppColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CampaignComplete(track: String, onClose: () -> Unit) {
    val progress = LocalProgress.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Verified, null, tint = AppColors.Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.campaign_complete), color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.stars_earned, progress.earnedStars(track), progress.maxStars(track)), color = AppColors.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_wait_update), color = AppColors.TextTertiary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.go_home), icon = Icons.Filled.Home, modifier = Modifier.padding(horizontal = 40.dp)) { onClose() }
    }
}
