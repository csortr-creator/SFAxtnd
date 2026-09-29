package io.nekohasekai.sfa.compose.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RoutingSettingsScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Роутинг")
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(
                        onClick = onBack,
                    ) {
                        androidx.compose.material.icons.Icons.Default
                        androidx.compose.material3.Text("‹")
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Пустой экран.
            // Настройки DNS и роутинга будут добавляться постепенно.
        }
    }
}
