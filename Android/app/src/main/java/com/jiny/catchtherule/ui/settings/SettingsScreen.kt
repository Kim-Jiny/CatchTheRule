package com.jiny.catchtherule.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.R
import com.jiny.catchtherule.data.LocalBilling
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card

private const val CONTACT_EMAIL = "kjinyz@naver.com"
private const val APP_VERSION = "1.0.0"
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

    ScreenBackground {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.settings), color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

            // 프로필
            Column(Modifier.fillMaxWidth().card()) {
                SettingsRow(Icons.Filled.Person, stringResource(R.string.nickname), progress.nickname.ifEmpty { stringResource(R.string.not_set) }) {
                    draftNick = progress.nickname; showNickname = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Lightbulb, stringResource(R.string.hints_left), stringResource(R.string.hints_value, progress.hintsRemaining), chevron = false, onClick = null)
            }

            // 인앱결제
            Column(Modifier.fillMaxWidth().card()) {
                // 힌트 구매 — 단일 버튼(누르면 5·10·20·50 팝업)
                SettingsRow(Icons.Filled.Lightbulb, stringResource(R.string.iap_buy_hints), null) {
                    showHintShop = true
                }
                RowDivider()
                // 구매 복원
                SettingsRow(Icons.Filled.Restore, stringResource(R.string.iap_restore), null) {
                    billing.queryPurchases { restored -> iapMsg = if (restored) restoreDoneMsg else restoreNoneMsg }
                }
            }

            // 환경설정
            Column(Modifier.fillMaxWidth().card()) {
                ToggleRow(Icons.Filled.VolumeUp, stringResource(R.string.sound), progress.soundOn) { progress.setSound(it) }
                RowDivider()
                ToggleRow(Icons.Filled.Vibration, stringResource(R.string.haptics), progress.hapticsOn) { progress.setHaptics(it) }
            }

            // 지원
            Column(Modifier.fillMaxWidth().card()) {
                SettingsRow(Icons.Filled.Email, stringResource(R.string.contact), null) {
                    showInquiry = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Info, stringResource(R.string.support), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SUPPORT_URL?lang=$langCode"))) }
                }
                RowDivider()
                SettingsRow(Icons.Filled.Description, stringResource(R.string.terms), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$TERMS_URL?lang=$langCode"))) }
                }
                RowDivider()
                SettingsRow(Icons.Filled.PrivacyTip, stringResource(R.string.privacy), null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PRIVACY_URL?lang=$langCode"))) }
                }
            }

            // 초기화
            Row(
                Modifier.fillMaxWidth().card().clickable { showReset = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Filled.Delete, null, tint = AppColors.Danger, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.reset_progress), color = AppColors.Danger, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Text("CatchTheRule v$APP_VERSION", color = AppColors.TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String?, chevron: Boolean = true, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = AppColors.Accent2, modifier = Modifier.size(20.dp))
        Text(title, color = AppColors.TextPrimary, fontSize = 16.sp)
        Box(Modifier.weight(1f))
        if (value != null) Text(value, color = AppColors.TextTertiary, fontSize = 15.sp)
        if (chevron && onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = AppColors.Accent2, modifier = Modifier.size(20.dp))
        Text(title, color = AppColors.TextPrimary, fontSize = 16.sp)
        Box(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = AppColors.Accent,
                uncheckedTrackColor = AppColors.BgElevated,
            ),
        )
    }
}

@Composable
private fun RowDivider() {
    Divider(color = AppColors.Stroke, thickness = 1.dp)
}
