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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun ImageResizerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var sourceUri  by remember { mutableStateOf<Uri?>(null) }
    var origW      by remember { mutableIntStateOf(0) }
    var origH      by remember { mutableIntStateOf(0) }
    var widthStr   by remember { mutableStateOf("") }
    var heightStr  by remember { mutableStateOf("") }
    var lockRatio  by remember { mutableStateOf(true) }
    var format     by remember { mutableStateOf("JPEG") }
    var resultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var resultW    by remember { mutableIntStateOf(0) }
    var resultH    by remember { mutableIntStateOf(0) }
    var status     by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri = it
            resultBytes = null
            scope.launch(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s, null, opts) }
                withContext(Dispatchers.Main) {
                    origW = opts.outWidth; origH = opts.outHeight
                    widthStr = origW.toString(); heightStr = origH.toString()
                }
            }
        }
    }

    fun onWidthChange(v: String) {
        widthStr = v
        val w = v.toIntOrNull() ?: return
        if (lockRatio && origW > 0) heightStr = (w * origH / origW).toString()
        resultBytes = null
    }

    fun onHeightChange(v: String) {
        heightStr = v
        val h = v.toIntOrNull() ?: return
        if (lockRatio && origH > 0) widthStr = (h * origW / origH).toString()
        resultBytes = null
    }

    fun resize() {
        val uri = sourceUri ?: return
        val w = widthStr.toIntOrNull()?.coerceAtLeast(1) ?: return
        val h = heightStr.toIntOrNull()?.coerceAtLeast(1) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val bmp: Bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                val out = ByteArrayOutputStream()
                val fmt = when (format) { "PNG" -> Bitmap.CompressFormat.PNG; "WEBP" -> Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }
                scaled.compress(fmt, 92, out)
                withContext(Dispatchers.Main) {
                    resultBytes = out.toByteArray(); resultW = w; resultH = h; status = ""
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
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
                    put(MediaStore.Images.Media.DISPLAY_NAME, "resized_${resultW}x${resultH}_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechPerByteTools")
                }
                val outUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(outUri)!!.use { it.write(bytes) }
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
        title = "Image Resizer",
        navController = navController,
        subtitle = "Resize images to exact pixel dimensions. Lock aspect ratio to avoid distortion.",
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
                "Tap 'Pick an Image' to select the image you want to resize.",
                "Enter the target width or height in pixels, or pick a preset size.",
                "Enable the 🔒 lock icon to maintain the original aspect ratio.",
                "Choose the output format (JPEG or PNG), then tap 'Resize'.",
                "Tap 'Save' to store the resized image in Pictures/TechPerByteTools.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "📐  Pick an Image"
                    else "📐  Loaded — original ${origW}×${origH}px",
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
                // W / H inputs
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Width (px)", color = TextMuted, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = widthStr,
                                    onValueChange = { onWidthChange(it) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = textFieldColors(),
                                )
                            }
                            IconButton(onClick = { lockRatio = !lockRatio }) {
                                Text(if (lockRatio) "🔒" else "🔓", fontSize = 20.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Height (px)", color = TextMuted, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = heightStr,
                                    onValueChange = { onHeightChange(it) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = textFieldColors(),
                                )
                            }
                        }

                        // Presets
                        Text("Quick presets:", color = TextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(1920 to 1080, 1280 to 720, 800 to 600, 400 to 400).forEach { (pw, ph) ->
                                OutlinedButton(
                                    onClick = { lockRatio = false; widthStr = pw.toString(); heightStr = ph.toString(); resultBytes = null },
                                    shape  = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) { Text("${pw}×${ph}", fontSize = 10.sp, color = TextSecondary) }
                            }
                        }
                    }
                }

                // Format
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Format:", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    listOf("JPEG", "PNG", "WEBP").forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick  = { format = f; resultBytes = null },
                            label    = { Text(f, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = SurfaceVariant, labelColor = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = format == f, borderColor = Border, selectedBorderColor = Primary),
                        )
                    }
                }

                if (resultBytes == null) {
                    Button(
                        onClick = { resize() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape  = RoundedCornerShape(10.dp),
                    ) { Text("↔  Resize Image", fontWeight = FontWeight.Bold) }
                }

                resultBytes?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, SuccessDark), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("✓ Resized to ${resultW}×${resultH}px", color = Success, fontWeight = FontWeight.Bold)
                            Button(onClick = { showSaveDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SuccessDark), shape = RoundedCornerShape(10.dp)) {
                                Text("⬇  Save ${resultW}×${resultH} Image", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { resultBytes = null; sourceUri = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Border)) {
                                Text("Start Over", color = TextSecondary)
                            }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
)
