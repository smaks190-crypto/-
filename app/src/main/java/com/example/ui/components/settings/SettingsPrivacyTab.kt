package com.example.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate400

@Composable
fun SettingsPrivacySubContent(
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .testTag("back_to_settings_button_privacy")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo500.copy(alpha = 0.15f))
                        .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Конфиденциальность",
                        tint = Indigo400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Политика конфиденциальности",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Безопасность и обработка данных",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 350.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Сбор и хранение данных:\n" +
                        "• Все финансовые записи, транзакции, категории, суммы, цели и настройки бюджета сохраняются исключительно локально в базе данных вашего устройства.\n" +
                        "• Приложение не отправляет финансовую информацию на сервера разработчиков и не имеет общего сетевого хранилища.\n\n" +
                        "Использование Gemini API (Google):\n" +
                        "• Если вы указали API-ключ Gemini, данные сумм, категорий и описаний передаются в Google Gemini API напрямую со смартфона для анализа трат и формирования отчетов.\n" +
                        "• Голосовой ввод передается в Gemini API для распознавания текста и распределения транзакций.\n" +
                        "• Запросы отправляются со смартфона прямо на сервера Google, минуя промежуточные сервера разработчиков.\n\n" +
                        "Контроль:\n" +
                        "• Вы можете удалить свои данные или отозвать согласие в любой момент.",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Единая нижняя панель управления с квадратной кнопкой закрытия ✕ справа
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
