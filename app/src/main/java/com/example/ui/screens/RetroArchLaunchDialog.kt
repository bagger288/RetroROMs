package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.emulator.CoreOption
import com.example.data.emulator.CoreSelectionSource
import com.example.ui.theme.ArcadeCardBorder
import com.example.ui.theme.ArcadeSurfaceContainer
import com.example.ui.theme.ArcadeSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OnPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.RetroArchTipData

@Composable
fun RetroArchLaunchDialog(
    tip: RetroArchTipData,
    onLaunchNow: () -> Unit,
    onSelectCore: (CoreOption) -> Unit,
    onOpenRetroArchMenu: () -> Unit,
    onOpenOtherEmulator: () -> Unit,
    onDismiss: () -> Unit,
    onNeverShowAgain: (Boolean) -> Unit
) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Запуск в RetroArch",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tip.game.title,
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info pill with platform and currently active core
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ArcadeSurfaceContainer)
                        .border(1.dp, ArcadeCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Платформа: ",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = tip.game.consoleName.ifBlank { tip.game.consoleSlug.uppercase() },
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Активное ядро: ",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = tip.selectedCore.displayName,
                                color = EmeraldGreen,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Source badge
                        val (badgeText, badgeColor, badgeIcon) = when (tip.source) {
                            CoreSelectionSource.HISTORY -> Triple("★ Найдено в истории RetroArch (установлено)", EmeraldGreen, Icons.Default.CheckCircle)
                            CoreSelectionSource.USER_SELECTED -> Triple("Выбрано вами", NeonCyan, Icons.Default.Check)
                            CoreSelectionSource.DEFAULT -> Triple("Рекомендованное по умолчанию", GoldenAmber, Icons.Default.Star)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Available cores selector (if more than 1 core exists for this console)
                if (tip.availableCores.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Выберите ядро для этой платформы:",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        tip.availableCores.forEach { coreOption ->
                            val isSelected = coreOption.id == tip.selectedCore.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else ArcadeSurfaceContainer)
                                    .border(
                                        1.dp,
                                        if (isSelected) EmeraldGreen else ArcadeCardBorder.copy(alpha = 0.6f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectCore(coreOption) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Gamepad,
                                        contentDescription = null,
                                        tint = if (isSelected) EmeraldGreen else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = coreOption.displayName,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (coreOption.description.isNotBlank()) {
                                            Text(
                                                text = coreOption.description,
                                                color = TextMuted,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Black screen warning & instructions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldenAmber.copy(alpha = 0.08f))
                        .border(1.dp, GoldenAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = GoldenAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Если в RetroArch черный экран:",
                                color = GoldenAmber,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "RetroArch виснет на черном экране, если выбранное ядро не установлено. " +
                                    "Если у вас установлено другое ядро — выберите его из списка выше. " +
                                    "Либо скачайте «${tip.selectedCore.displayName}» через онлайн-апдейтер в RetroArch.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Touch controls (overlay) note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ArcadeSurfaceContainer)
                        .border(1.dp, ArcadeCardBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Кнопки управления: конфигурация retroarch.cfg передаётся автоматически для отображения оверлея.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Quick actions
                OutlinedButton(
                    onClick = onOpenRetroArchMenu,
                    modifier = Modifier.fillMaxWidth().testTag("open_retroarch_menu_button"),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Открыть меню RetroArch (Загрузка ядер)", fontSize = 12.sp)
                }

                // Checkbox: Do not show again
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { doNotShowAgain = !doNotShowAgain }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = EmeraldGreen,
                            checkmarkColor = OnPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Больше не спрашивать (запускать сразу)",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onNeverShowAgain(doNotShowAgain)
                    onLaunchNow()
                },
                modifier = Modifier.testTag("launch_game_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = OnPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Запустить игру", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenOtherEmulator) {
                    Text("Другой эмулятор", color = NeonCyan, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = TextMuted, fontSize = 12.sp)
                }
            }
        },
        containerColor = ArcadeSurfaceVariant
    )
}
