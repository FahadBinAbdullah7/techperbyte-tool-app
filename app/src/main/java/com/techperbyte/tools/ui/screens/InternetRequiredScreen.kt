package com.techperbyte.tools.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.TextMuted
import com.techperbyte.tools.ui.theme.TextSecondary

@Composable
fun InternetRequiredScreen(navController: NavController, toolName: String) {
    ToolScaffold(title = toolName, navController = navController) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📡", fontSize = 64.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Internet Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "$toolName requires a live internet connection and cannot run fully offline. " +
                "This app focuses on the 13 tools that work 100% natively without any internet.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}
