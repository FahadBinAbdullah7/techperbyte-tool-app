package com.techperbyte.tools.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PdfFlipbookScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var sourceUri   by remember { mutableStateOf<Uri?>(null) }
    var pageCount   by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var loading     by remember { mutableStateOf(false) }
    var goingForward by remember { mutableStateOf(true) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri = it; currentPage = 0
            scope.launch(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(it, "r")!!
                    val renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    renderer.close(); pfd.close()
                    withContext(Dispatchers.Main) { pageCount = count }
                } catch (_: Exception) { }
            }
        }
    }

    fun loadPage(page: Int) {
        val uri = sourceUri ?: return
        loading = true
        scope.launch(Dispatchers.IO) {
            try {
                val pfd  = context.contentResolver.openFileDescriptor(uri, "r")!!
                val renderer = PdfRenderer(pfd)
                val p = renderer.openPage(page)
                val scale = 2f
                val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                p.close(); renderer.close(); pfd.close()
                withContext(Dispatchers.Main) { pageBitmap = bmp; loading = false }
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { loading = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loading = false }
            }
        }
    }

    LaunchedEffect(currentPage, pageCount) {
        if (pageCount > 0) loadPage(currentPage)
    }

    ToolScaffold(
        title = "PDF Flipbook",
        navController = navController,
        subtitle = "Browse a PDF page by page with smooth transitions. No internet needed.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HowToCard(listOf(
                "Tap 'Open PDF' to load a PDF file from your device.",
                "Use the ← → arrow buttons or swipe to turn pages.",
                "The page counter at the top shows your current position.",
                "Works fully offline — no internet connection needed.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "📖  Open a PDF" else "📖  $pageCount pages loaded",
                    color = if (sourceUri == null) TextSecondary else Accent, fontWeight = FontWeight.SemiBold,
                )
            }

            if (pageCount > 0) {
                // Page counter
                Text("Page ${currentPage + 1} of $pageCount", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // Page display with flip animation
                Card(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (loading) {
                            CircularProgressIndicator(color = Primary)
                        } else {
                            AnimatedContent(
                                targetState = pageBitmap,
                                transitionSpec = {
                                    if (goingForward) {
                                        slideInHorizontally { it } + fadeIn() togetherWith
                                        slideOutHorizontally { -it } + fadeOut()
                                    } else {
                                        slideInHorizontally { -it } + fadeIn() togetherWith
                                        slideOutHorizontally { it } + fadeOut()
                                    }
                                },
                                label = "page_flip",
                            ) { bmp ->
                                bmp?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Page ${currentPage + 1}",
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (currentPage > 0) { goingForward = false; currentPage-- } },
                        enabled = currentPage > 0,
                        colors  = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape   = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        Spacer(Modifier.width(4.dp))
                        Text("Prev")
                    }

                    // Jump-to slider
                    if (pageCount > 1) {
                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { goingForward = it > currentPage; currentPage = it.toInt() },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }

                    Button(
                        onClick = { if (currentPage < pageCount - 1) { goingForward = true; currentPage++ } },
                        enabled = currentPage < pageCount - 1,
                        colors  = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape   = RoundedCornerShape(10.dp),
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                    }
                }
            }
        }
    }
}
