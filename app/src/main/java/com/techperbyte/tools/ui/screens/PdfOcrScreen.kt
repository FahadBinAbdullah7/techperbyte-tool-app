package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun PdfOcrScreen(navController: NavController) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var sourceUri     by remember { mutableStateOf<Uri?>(null) }
    var sourceName    by remember { mutableStateOf("") }
    var pageCount     by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processing    by remember { mutableStateOf(false) }
    var progress      by remember { mutableFloatStateOf(0f) }
    var resultText    by remember { mutableStateOf("") }
    var status        by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri  = it
            sourceName = it.lastPathSegment?.substringAfterLast('/') ?: "file.pdf"
            resultText = ""; status = ""; progress = 0f; previewBitmap = null
            scope.launch(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(it, "r")!!
                    val renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    val page0 = renderer.openPage(0)
                    val scale = 600f / maxOf(page0.width, page0.height).coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(
                        (page0.width * scale).toInt().coerceAtLeast(1),
                        (page0.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page0.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page0.close()
                    renderer.close(); pfd.close()
                    withContext(Dispatchers.Main) { pageCount = count; previewBitmap = bmp }
                } catch (_: Exception) { }
            }
        }
    }

    fun runOcr() {
        val uri = sourceUri ?: return
        processing = true; progress = 0f; resultText = ""
        scope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
                val renderer = PdfRenderer(pfd)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val sb = StringBuilder()

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val image = InputImage.fromBitmap(bmp, 0)
                    val result = recognizer.process(image).await()
                    if (result.text.isNotBlank()) {
                        sb.append("--- Page ${i + 1} ---\n${result.text}\n\n")
                    }
                    bmp.recycle()

                    val prog = (i + 1).toFloat() / renderer.pageCount
                    withContext(Dispatchers.Main) { progress = prog }
                }

                renderer.close(); pfd.close()
                withContext(Dispatchers.Main) {
                    resultText = sb.toString().trim().ifEmpty { "No text found in PDF." }
                    processing = false; status = ""
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { processing = false; status = "Error: ${e.message}" }
            }
        }
    }

    fun saveText() {
        if (resultText.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "pdf_ocr_${System.currentTimeMillis()}.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/TechPerByteTools")
                }
                val outUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(outUri)!!.use { it.write(resultText.toByteArray()) }
                withContext(Dispatchers.Main) { status = "✓ Text saved to Downloads/TechPerByteTools" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Save failed: ${e.message}" }
            }
        }
    }

    if (showSaveDialog) SaveConfirmDialog(
        destination = "Downloads/TechPerByteTools",
        onConfirm   = { showSaveDialog = false; saveText() },
        onDismiss   = { showSaveDialog = false },
    )

    ToolScaffold(
        title = "PDF OCR",
        navController = navController,
        subtitle = "Extracts text from every page of a PDF using ML Kit — works fully offline.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HowToCard(listOf(
                "Tap 'Open PDF' to select a PDF file from your device.",
                "Tap 'Extract Text (OCR)' — each page is scanned automatically.",
                "The extracted text appears below; tap 'Copy Text' to copy it.",
                "Tap 'Save as .txt' to save the result to your Downloads folder.",
                "Works fully offline using Google ML Kit.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "🤖  Open a PDF" else "🤖  $sourceName ($pageCount pages)",
                    color = if (sourceUri == null) TextSecondary else Accent, fontWeight = FontWeight.SemiBold,
                )
            }

            previewBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            if (pageCount > 0 && !processing && resultText.isEmpty()) {
                Button(
                    onClick = { runOcr() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text("🔍  Extract Text from All $pageCount Pages", fontWeight = FontWeight.Bold) }
            }

            if (processing) {
                Text("Processing page ${(progress * pageCount).toInt() + 1} of $pageCount…", color = TextSecondary, fontSize = 13.sp)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = SurfaceVariant)
            }

            if (resultText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, SuccessDark), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Extracted Text", fontWeight = FontWeight.Bold, color = Success, fontSize = 14.sp)
                        Text("${resultText.length} characters", color = TextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { clipboard.setText(AnnotatedString(resultText)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape  = RoundedCornerShape(8.dp),
                            ) { Text("📋 Copy All", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                                shape  = RoundedCornerShape(8.dp),
                            ) { Text("⬇ Save .txt", fontWeight = FontWeight.Bold) }
                        }
                        HorizontalDivider(color = Border)
                        Text(resultText, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}
