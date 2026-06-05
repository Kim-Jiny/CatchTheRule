package com.jiny.catchtherule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.jiny.catchtherule.R
import com.jiny.catchtherule.data.Inquiry
import com.jiny.catchtherule.data.InquiryService
import com.jiny.catchtherule.data.LocalProgress
import com.jiny.catchtherule.ui.PrimaryButton
import com.jiny.catchtherule.ui.SectionHeader
import com.jiny.catchtherule.ui.theme.AppColors
import com.jiny.catchtherule.ui.theme.ScreenBackground
import com.jiny.catchtherule.ui.theme.card
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun InquiryScreen(modifier: Modifier = Modifier, onClose: () -> Unit) {
    val progress = LocalProgress.current
    val context = LocalContext.current
    val service = remember { InquiryService(context) }
    val scope = rememberCoroutineScope()

    var content by remember { mutableStateOf("") }
    var inquiries by remember { mutableStateOf<List<Inquiry>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sendFailedMsg = stringResource(R.string.inquiry_send_failed)

    suspend fun reload() {
        inquiries = runCatching { service.myInquiries() }.getOrDefault(inquiries)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

    ScreenBackground {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            // 헤더
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).card(12.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.contact), color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(20.dp))

            // 새 문의 작성
            SectionHeader(stringResource(R.string.inquiry_new))
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 120.dp).card(14.dp).padding(14.dp),
            ) {
                if (content.isEmpty()) {
                    Text(stringResource(R.string.inquiry_placeholder), color = AppColors.TextTertiary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Spacer(Modifier.size(8.dp))
                Text(it, color = AppColors.Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.size(12.dp))
            PrimaryButton(
                text = stringResource(if (sending) R.string.sending else R.string.inquiry_send),
                icon = Icons.Filled.Send,
                enabled = content.trim().isNotEmpty() && !sending,
            ) {
                val text = content.trim()
                if (text.isEmpty()) return@PrimaryButton
                sending = true; error = null
                scope.launch {
                    val ok = runCatching {
                        service.submit(text, progress.nickname.ifBlank { null })
                    }.isSuccess
                    if (ok) { content = ""; reload() }
                    else error = sendFailedMsg
                    sending = false
                }
            }

            if (inquiries.isNotEmpty()) {
                Spacer(Modifier.size(24.dp))
                SectionHeader(stringResource(R.string.my_inquiries))
                Spacer(Modifier.size(8.dp))
                inquiries.forEach { inq ->
                    InquiryCard(inq)
                    Spacer(Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun InquiryCard(inquiry: Inquiry) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (inquiry.isReplied) AppColors.Success else AppColors.Star
            Box(
                Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(stringResource(if (inquiry.isReplied) R.string.status_replied else R.string.status_pending), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f))
            Text(day(inquiry.createdAt), color = AppColors.TextTertiary, fontSize = 12.sp)
        }
        Text(inquiry.content, color = AppColors.TextPrimary, fontSize = 15.sp)
        val reply = inquiry.reply
        if (!reply.isNullOrEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Accent.copy(alpha = 0.10f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, tint = AppColors.Accent2, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.admin_reply), color = AppColors.Accent2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(reply, color = AppColors.TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

private fun day(iso: String?): String {
    if (iso == null) return ""
    // "2026-06-04T06:27:16.853Z" → "2026.06.04"
    val datePart = iso.substringBefore('T')
    val parts = datePart.split('-')
    return if (parts.size == 3) "${parts[0]}.${parts[1]}.${parts[2]}" else datePart
}
