package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer as NativePdfRenderer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun PdfCompressorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var sourceUri     by remember { mutableStateOf<Uri?>(null) }
    var sourceName    by remember { mutableStateOf("") }
    var sourceSize    by remember { mutableLongStateOf(0L) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imgQuality    by remember { mutableIntStateOf(60) }
    var compressing   by remember { mutableStateOf(false) }
    var resultSize    by remember { mutableLongStateOf(0L) }
    var done          by remember { mutableStateOf(false) }
    var status        by remember { mutableStateOf("") }
    var resultBytes   by remember { mutableStateOf<ByteArray?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri  = it; done = false; resultBytes = null; status = ""; previewBitmap = null
            sourceName = it.lastPathSegment?.substringAfterLast('/') ?: "file.pdf"
            sourceSize = uriFileSize(context, it)
            scope.launch(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(it, "r")!!
                    val renderer = NativePdfRenderer(pfd)
                    val page0 = renderer.openPage(0)
                    val scale = 600f / maxOf(page0.width, page0.height).coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(
                        (page0.width * scale).toInt().coerceAtLeast(1),
                        (page0.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page0.render(bmp, null, null, NativePdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page0.close()
                    renderer.close(); pfd.close()
                    withContext(Dispatchers.Main) { previewBitmap = bmp }
                } catch (_: Exception) { }
            }
        }
    }

    fun compress() {
        val uri = sourceUri ?: return
        compressing = true
        scope.launch(Dispatchers.IO) {
            try {
                // Strategy: render each page to JPEG at reduced quality and rebuild PDF
                val inputBytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val src = PDDocument.load(inputBytes)
                val renderer = PDFRenderer(src)
                val dst = PDDocument()

                for (pageIdx in 0 until src.numberOfPages) {
                    val pageBmp = renderer.renderImageWithDPI(pageIdx, 96f)
                    val page = PDPage(src.getPage(pageIdx).mediaBox)
                    dst.addPage(page)
                    val jpegImage = JPEGFactory.createFromImage(dst, pageBmp, imgQuality / 100f)
                    PDPageContentStream(dst, page).use { cs ->
                        cs.drawImage(jpegImage, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                    }
                    pageBmp.recycle()
                }
                src.close()

                val out = ByteArrayOutputStream()
                dst.save(out)
                dst.close()
                val bytes = out.toByteArray()

                withContext(Dispatchers.Main) {
                    resultBytes = bytes; resultSize = bytes.size.toLong()
                    compressing = false; done = true; status = ""
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { compressing = false; status = "Error: ${e.message}" }
            }
        }
    }

    fun save() {
        val bytes = resultBytes ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "compressed_${System.currentTimeMillis()}.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/TechPerByteTools")
                }
                val outUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(outUri)!!.use { it.write(bytes) }
                withContext(Dispatchers.Main) { status = "✓ Saved to Downloads/TechPerByteTools" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Save failed: ${e.message}" }
            }
        }
    }

    if (showSaveDialog) SaveConfirmDialog(
        destination = "Downloads/TechPerByteTools",
        onConfirm   = { showSaveDialog = false; save() },
        onDismiss   = { showSaveDialog = false },
    )

    ToolScaffold(
        title = "PDF Compressor",
        navController = navController,
        subtitle = "Re-renders each page as JPEG to reduce file size. Lower quality = smaller file.",
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
                "Tap 'Pick a PDF' to select the PDF you want to compress.",
                "Drag the quality slider — lower quality means a smaller file size.",
                "Tap 'Compress PDF' and wait for processing to finish.",
                "Review the before/after size, then tap 'Save' to keep the result.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "📦  Pick a PDF" else "📦  $sourceName (${fmtBytes(sourceSize)})",
                    color = if (sourceUri == null) TextSecondary else Accent, fontWeight = FontWeight.SemiBold,
                )
            }

            if (sourceUri != null) {
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
                Text("Image Quality: $imgQuality%", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Lower = smaller file, less visual fidelity. 60–80% is usually a good balance.", color = TextSecondary, fontSize = 12.sp)
                Slider(value = imgQuality.toFloat(), onValueChange = { imgQuality = it.toInt(); done = false; resultBytes = null }, valueRange = 10f..95f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))

                if (!done) {
                    Button(onClick = { compress() }, enabled = !compressing, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(10.dp)) {
                        Text(if (compressing) "Compressing…" else "🗜️  Compress PDF", fontWeight = FontWeight.Bold)
                    }
                }

                if (done && resultBytes != null) {
                    val saved = ((1f - resultSize.toFloat() / sourceSize) * 100).toInt().coerceAtLeast(0)
                    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, SuccessDark), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatBox("Original",   fmtBytes(sourceSize), SurfaceVariant, Modifier.weight(1f))
                                StatBox("Compressed", fmtBytes(resultSize), SuccessDark,   Modifier.weight(1f))
                                StatBox("Saved",      if (saved > 0) "$saved%" else "0%", Primary, Modifier.weight(1f))
                            }
                            Button(onClick = { showSaveDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SuccessDark), shape = RoundedCornerShape(10.dp)) {
                                Text("⬇  Save Compressed PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}
