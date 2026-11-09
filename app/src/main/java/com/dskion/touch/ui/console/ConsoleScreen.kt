package com.dskion.touch.ui.console

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dskion.touch.R

/**
 * Placeholder until the accessibility service lands (milestone 2): will show live
 * accessibility/voice/screen state and a "현재 화면 덤프 복사" action for pulling real
 * 배민/쿠팡이츠 node dumps.
 */
@Composable
fun ConsoleScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_console)) }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "접근성 서비스가 추가되면 여기서 실시간 화면 인식 상태를 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
