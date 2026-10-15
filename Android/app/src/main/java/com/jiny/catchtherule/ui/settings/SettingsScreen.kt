package com.jiny.catchtherule.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.jiny.catchtherule.ui.home.pressable
import com.jiny.catchtherule.ui.home.rememberCountUp
import com.jiny.catchtherule.ui.home.staggerIn
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.data.BillingManager
import com.jiny.catchtherule.data.LocalBilling
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card

private const val CONTACT_EMAIL = "kjinyz@naver.com"
private const val TERMS_URL = "https://duo.jiny.shop/ctr/terms"
private const val PRIVACY_URL = "https://duo.jiny.shop/ctr/privacy"
private const val SUPPORT_URL = "https://duo.jiny.shop/ctr/support"

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val progress = LocalProgress.current
    val billing = LocalBilling.current
    val context = LocalContext.current
    val activity = context as? Activity
    val langCode = java.util.Locale.getDefault().language
    val restoreDoneMsg = stringResource(R.string.iap_restore_done)
    val restoreNoneMsg = stringResource(R.string.iap_restore_none)
    var showReset by remember { mutableStateOf(false) }
    var showNickname by remember { mutableStateOf(false) }
    var draftNick by remember { mutableStateOf("") }
    var showInquiry by remember { mutableStateOf(false) }
    var showHintShop by remember { mutableStateOf(false) }
    var iapMsg by remember { mutableStateOf<String?>(null) }

    if (showInquiry) {
        InquiryScreen(modifier = modifier, onClose = { showInquiry = false })
        return
    }

    var idx = 0
    ScreenBackground(animated = true) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                stringResource(R.string.settings),
                style = TextStyle(brush = AppColors.AccentGradient, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(top = 8.dp).staggerIn(idx++),
            )

            // 프로필 카드
            ProfileCard(
                nickname = progress.nickname,
                stars = progress.totalStars,
                modifier = Modifier.staggerIn(idx++),
            ) { draftNick = progress.nickname; showNickname = true }

            // 프로필 설정
            GroupCard(Modifier.staggerIn(idx++)) {
                SettingsRow(Icons.Filled.Person, Cyan, stringResource(R.string.nickname), progress.nickname.ifEmpty { stringResource(R.string.not_set) }) {
                    draftNick = progress.nickname; showNickname = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Lightbulb, Amber, stringResource(R.string.hints_left), stringResource(R.string.hints_value, progress.hintsRemaining), chevron = false, onClick = null)
            }

            // 인앱결제
            Column(Modifier.staggerIn(idx++), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupCard {
                    // 광고 제거 — 구매 전에만 노출(구매 시 자동으로 사라짐)
                    if (!billing.removeAdsPurchased) {
                        SettingsRow(
                            Icons.Filled.Block, Pink, stringResource(R.string.iap_remove_ads),
                            billing.removeAdsPrice.ifEmpty { stringResource(R.string.iap_loading) },
                        ) {
                            activity?.let { billing.purchase(it, BillingManager.REMOVE_ADS_ID) }
                        }
                        RowDivider()
                    }
                    // 힌트 구매 — 단일 버튼(누르면 5·10·20·50 팝업)
                    SettingsRow(Icons.Filled.Lightbulb, Amber, stringResource(R.string.iap_buy_hints), null) {
                        showHintShop = true
                    }
                    RowDivider()
                    // 구매 복원
                    SettingsRow(Icons.Filled.Restore, Green, stringResource(R.string.iap_restore), null) {
                        billing.queryPurchases { restored -> iapMsg = if (restored) restoreDoneMsg else restoreNoneMsg }
                    }
                }

                // 광고 제거 범위 고지(배너·전면만 제거, 보상형 힌트 광고는 유지)
                if (!billing.removeAdsPurchased) {
                    Text(
                        stringResource(R.string.iap_remove_ads_note),
                        color = AppColors.TextTertiary, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // 환경설정
            GroupCard(Modifier.staggerIn(idx++)) {
                ToggleRow(Icons.Filled.VolumeUp, Cyan, stringResource(R.string.sound), progress.soundOn) { progress.setSound(it) }
                RowDivider()
                ToggleRow(Icons.Filled.Vibration, Violet, stringResource(R.string.haptics), progress.hapticsOn) { progress.setHaptics(it) }
            }

            // 설정탭 배너 (햅틱 ↔ 문의하기)
            com.jiny.catchtherule.ui.BannerAd(com.jiny.catchtherule.ui.BannerUnits.settings)

            // 지원
            GroupCard(Modifier.staggerIn(idx++)) {
                SettingsRow(Icons.Filled.Email, Green, stringResource(R.string.contact), null) {
                    showInquiry = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Info, SlateBlue, stringResource(R.string.support), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SUPPORT_URL?lang=$langCode"))) }
                }
                RowDivider()
                SettingsRow(Icons.Filled.Description, SlateBlue, stringResource(R.string.terms), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$TERMS_URL?lang=$langCode"))) }
                }
                RowDivider()
                SettingsRow(Icons.Filled.PrivacyTip, SlateBlue, stringResource(R.string.privacy), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PRIVACY_URL?lang=$langCode"))) }
                }
            }

            // 초기화 (위험)
            val dangerShape = RoundedCornerShape(20.dp)
            Row(
                Modifier.staggerIn(idx++).fillMaxWidth()
                    .clip(dangerShape)
                    .background(AppColors.Danger.copy(alpha = 0.08f).compositeOver(AppColors.Card))
                    .border(1.dp, AppColors.Danger.copy(alpha = 0.35f), dangerShape)
                    .pressable { showReset = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconChip(Icons.Filled.Delete, AppColors.Danger)
                Text(stringResource(R.string.reset_progress), color = AppColors.Danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            AppInfoSection(Modifier.staggerIn(idx++))
        }
    }

    iapMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { iapMsg = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { iapMsg = null }) { Text(stringResource(R.string.close)) } },
            containerColor = AppColors.Card,
        )
    }

    if (showHintShop) {
        com.jiny.catchtherule.ui.play.HintShopDialog(billing) { showHintShop = false }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_msg)) },
            confirmButton = { TextButton(onClick = { progress.resetProgress(); showReset = false }) { Text(stringResource(R.string.reset), color = AppColors.Danger) } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.Card,
        )
    }

    if (showNickname) {
        AlertDialog(
            onDismissRequest = { showNickname = false },
            title = { Text(stringResource(R.string.change_nickname)) },
            text = { OutlinedTextField(value = draftNick, onValueChange = { draftNick = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val t = draftNick.trim()
                    if (t.isNotEmpty()) progress.updateNickname(t)
                    showNickname = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showNickname = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.Card,
        )
    }
}

private val Cyan = Color(0xFF18C8FF)
private val Violet = Color(0xFFA78BFA)
private val Amber = Color(0xFFFFB020)
private val Green = Color(0xFF3DDC97)
private val Pink = Color(0xFFFF5C8A)
private val SlateBlue = Color(0xFF8FA3C7)

private val TNUM = TextStyle(fontFeatureSettings = "tnum")

/** 컬러 아이콘 칩: 항목 성격별 네온색 틴트 배경 + 글로우 테두리. */
@Composable
private fun IconChip(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().card(22.dp)) { content() }
}

@Composable
private fun ProfileCard(nickname: String, stars: Int, modifier: Modifier, onEdit: () -> Unit) {
    val count by rememberCountUp(stars.toFloat(), 800)
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth()
            .drawBehind {
                val pad = 12.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(AppColors.Accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) / 2 + pad,
                    ),
                    topLeft = Offset(-pad, -pad), size = Size(size.width + pad * 2, size.height + pad * 2),
                    cornerRadius = CornerRadius(24.dp.toPx() + pad),
                )
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.Accent.copy(alpha = 0.16f).compositeOver(AppColors.Card), AppColors.Accent2.copy(alpha = 0.04f).compositeOver(AppColors.Card))))
            .border(1.dp, AppColors.Accent.copy(alpha = 0.4f), shape)
            .pressable(onClick = onEdit)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(68.dp).border(3.dp, AppColors.AccentGradient, CircleShape).padding(5.dp)
                .clip(CircleShape).background(AppColors.BgElevated),
            contentAlignment = Alignment.Center,
        ) {
            val initial = nickname.trim().firstOrNull()?.uppercase()
            if (initial != null) Text(initial, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            else Icon(Icons.Filled.Person, null, tint = AppColors.TextSecondary, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                nickname.ifEmpty { stringResource(R.string.not_set) },
                color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Star, null, tint = AppColors.Star, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.home_total_stars, count.roundToInt()),
                    style = TNUM.copy(color = AppColors.Star, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, color: Color, title: String, value: String?, chevron: Boolean = true, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.pressable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconChip(icon, color)
        Text(title, color = AppColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f, fill = value == null))
        if (value != null) Text(value, color = AppColors.TextTertiary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (chevron && onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, color: Color, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconChip(icon, color)
        Text(title, color = AppColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.Accent,
                checkedBorderColor = AppColors.Accent2,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = AppColors.BgElevated,
                uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp, modifier = Modifier.padding(start = 62.dp))
}

/** 앱 정보: 내 버전 + Play 인앱업데이트로 스토어 새 버전(=새 문제) 안내.
 *  주의: Play 인앱업데이트는 Play 로 설치한 앱에서만 동작(에뮬·사이드로드는 '최신'으로 표시). */
@Composable
private fun AppInfoSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pkg = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkg?.versionName ?: "1.0"
    @Suppress("DEPRECATION") val versionCode = pkg?.versionCode ?: 0

    var checked by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var storeCode by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching {
            val info = AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                updateAvailable = true
                storeCode = info.availableVersionCode()
            }
        }
        checked = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.padding(top = 4.dp)) {
        // 새 문제(스토어 새 버전) 안내 배너
        if (updateAvailable) {
            Row(
                Modifier.fillMaxWidth()
                    .drawBehind {
                        val pad = 8.dp.toPx()
                        drawRoundRect(
                            AppColors.Accent.copy(alpha = 0.3f),
                            topLeft = Offset(-pad, -pad / 2), size = Size(size.width + pad * 2, size.height + pad),
                            cornerRadius = CornerRadius(16.dp.toPx() + pad),
                        )
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.AccentGradient)
                    .pressable { openPlayStore(context) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.version_new_available), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.version_update_action), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.fillMaxWidth().card(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Info, Pink)
                Text(stringResource(R.string.my_version), color = AppColors.TextTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(Pink.copy(alpha = 0.16f)).border(1.dp, Pink.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("v$versionName ($versionCode)", color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (updateAvailable) {
                    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(AppColors.Accent2).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.settings_update_new), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.store_version), color = AppColors.TextTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    when {
                        !checked -> stringResource(R.string.version_checking)
                        updateAvailable -> "$storeCode"
                        else -> "—"
                    },
                    color = if (updateAvailable) AppColors.Accent2 else AppColors.TextSecondary, fontSize = 13.sp,
                )
            }
            if (checked && !updateAvailable) {
                Text(stringResource(R.string.version_up_to_date), color = AppColors.Success, fontSize = 13.sp)
            }
        }
    }
}

private fun openPlayStore(context: Context) {
    val id = context.packageName
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
        }
    }
}
