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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card

private const val CONTACT_EMAIL = "kjinyz@naver.com"
private const val APP_VERSION = "1.0"
private const val TERMS_URL = "https://duo.jiny.shop/ctr/terms"
private const val PRIVACY_URL = "https://duo.jiny.shop/ctr/privacy"

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val progress = LocalProgress.current
    val context = LocalContext.current
    var showReset by remember { mutableStateOf(false) }
    var showNickname by remember { mutableStateOf(false) }
    var draftNick by remember { mutableStateOf("") }
    var showInquiry by remember { mutableStateOf(false) }

    if (showInquiry) {
        InquiryScreen(modifier = modifier, onClose = { showInquiry = false })
        return
    }

    ScreenBackground {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("설정", color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

            // 프로필
            Column(Modifier.fillMaxWidth().card()) {
                SettingsRow(Icons.Filled.Person, "닉네임", progress.nickname.ifEmpty { "미설정" }) {
                    draftNick = progress.nickname; showNickname = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Lightbulb, "남은 힌트", "${progress.hintsRemaining}개", chevron = false, onClick = null)
            }

            // 환경설정
            Column(Modifier.fillMaxWidth().card()) {
                ToggleRow(Icons.Filled.VolumeUp, "효과음", progress.soundOn) { progress.setSound(it) }
                RowDivider()
                ToggleRow(Icons.Filled.Vibration, "햅틱", progress.hapticsOn) { progress.setHaptics(it) }
            }

            // 지원
            Column(Modifier.fillMaxWidth().card()) {
                SettingsRow(Icons.Filled.Email, "문의하기", null) {
                    showInquiry = true
                }
                RowDivider()
                SettingsRow(Icons.Filled.Description, "이용약관", null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) }
                }
                RowDivider()
                SettingsRow(Icons.Filled.PrivacyTip, "개인정보처리방침", null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
                }
            }

            // 초기화
            Row(
                Modifier.fillMaxWidth().card().clickable { showReset = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Filled.Delete, null, tint = AppColors.Danger, modifier = Modifier.size(20.dp))
                Text("진행도 초기화", color = AppColors.Danger, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Text("CatchTheRule v$APP_VERSION", color = AppColors.TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("진행도를 초기화할까요?") },
            text = { Text("모든 단계 진행과 별, 기록이 삭제됩니다. 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { progress.resetProgress(); showReset = false }) { Text("초기화", color = AppColors.Danger) } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("취소") } },
            containerColor = AppColors.Card,
        )
    }

    if (showNickname) {
        AlertDialog(
            onDismissRequest = { showNickname = false },
            title = { Text("닉네임 변경") },
            text = { OutlinedTextField(value = draftNick, onValueChange = { draftNick = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val t = draftNick.trim()
                    if (t.isNotEmpty()) progress.updateNickname(t)
                    showNickname = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showNickname = false }) { Text("취소") } },
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
