package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


@Composable
fun ImageCompressorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var sourceUri    by remember { mutableStateOf<Uri?>(null) }
    var sourceName   by remember { mutableStateOf("") }
    var sourceSize   by remember { mutableLongStateOf(0L) }
    var quality      by remember { mutableIntStateOf(80) }
    var format       by remember { mutableStateOf("JPEG") }
    var resultBytes  by remember { mutableStateOf<ByteArray?>(null) }
    var resultSize   by remember { mutableLongStateOf(0L) }
    var processing   by remember { mutableStateOf(false) }
    var status       by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri  = it
            sourceName = it.lastPathSegment ?: "image"
            sourceSize = uriFileSize(context, it)
            resultBytes = null
            status = ""
        }
    }

    fun compress() {
        val uri = sourceUri ?: return
        processing = true
        scope.launch(Dispatchers.IO) {
            try {
                val bmp: Bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                val out = ByteArrayOutputStream()
                val fmt = when (format) {
                    "PNG"  -> Bitmap.CompressFormat.PNG
                    "WEBP" -> Bitmap.CompressFormat.WEBP
                    else   -> Bitmap.CompressFormat.JPEG
                }
                bmp.compress(fmt, quality, out)
                val bytes = out.toByteArray()
                withContext(Dispatchers.Main) {
                    resultBytes = bytes
                    resultSize  = bytes.size.toLong()
                    processing  = false
                    status = ""
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { processing = false; status = "Error: ${e.message}" }
            }
        }
    }

    fun save() {
        val bytes = resultBytes ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val ext  = format.lowercase()
                val mime = when (format) { "PNG" -> "image/png"; "WEBP" -> "image/webp"; else -> "image/jpeg" }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "compressed_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechPerByteTools")
                }
                val out = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(out)!!.use { it.write(bytes) }
                withContext(Dispatchers.Main) { status = "✓ Saved to Pictures/TechPerByteTools" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Save failed: ${e.message}" }
            }
        }
    }

    if (showSaveDialog) SaveConfirmDialog(
        destination = "Pictures/TechPerByteTools",
        onConfirm   = { showSaveDialog = false; save() },
        onDismiss   = { showSaveDialog = false },
    )

    ToolScaffold(
        title = "Image Compressor",
        navController = navController,
        subtitle = "Reduce image file size by adjusting quality. Supports JPEG, PNG, and WebP output.",
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
                "Tap 'Pick an Image' to select a photo or image from your device.",
                "Drag the quality slider to control compression (lower = smaller file).",
                "Choose the output format: JPEG for photos, PNG for graphics/transparency.",
                "Tap 'Compress' and review the before/after file size.",
                "Tap 'Save' to store the compressed image in Pictures/TechPerByteTools.",
            ))
            // Pick button
            Button(
                onClick = { pickLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "🖼️  Pick an Image" else "🖼️  $sourceName (${fmtBytes(sourceSize)})",
                    color = if (sourceUri == null) TextSecondary else Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (sourceUri != null) {
                AsyncImage(
                    model = sourceUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
                // Quality
                Text("Quality: $quality%", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt(); resultBytes = null },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )

                // Format
                Text("Output Format", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("JPEG", "PNG", "WEBP").forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick  = { format = f; resultBytes = null },
                            label    = { Text(f, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor     = androidx.compose.ui.graphics.Color.White,
                                containerColor         = SurfaceVariant,
                                labelColor             = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = format == f,
                                borderColor = Border, selectedBorderColor = Primary,
                            ),
                        )
                    }
                }

                if (resultBytes == null) {
                    Button(
                        onClick = { compress() },
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(if (processing) "Compressing…" else "🗜️  Compress Image", fontWeight = FontWeight.Bold)
                    }
                }

                // Result
                resultBytes?.let {
                    val saved = ((1f - resultSize.toFloat() / sourceSize) * 100).toInt().coerceAtLeast(0)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(containerColor = Surface),
                        border   = BorderStroke(1.dp, SuccessDark),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatBox("Original",   fmtBytes(sourceSize), SurfaceVariant, Modifier.weight(1f))
                                StatBox("Compressed", fmtBytes(resultSize), if (saved > 0) SuccessDark else SurfaceVariant, Modifier.weight(1f))
                                StatBox("Saved",      if (saved > 0) "$saved%" else "—", if (saved > 0) Primary else SurfaceVariant, Modifier.weight(1f))
                            }
                            Button(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                                shape  = RoundedCornerShape(10.dp),
                            ) { Text("⬇  Save Compressed Image", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { resultBytes = null; sourceUri = null },
                                modifier = Modifier.fillMaxWidth(),
                                shape  = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Border),
                            ) { Text("Start Over", color = TextSecondary) }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) {
                Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
            }
        }
    }
}

