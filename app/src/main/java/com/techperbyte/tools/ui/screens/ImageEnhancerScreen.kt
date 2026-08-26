package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.*
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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

// ── Image processing helpers ──────────────────────────────────────────────────

private fun applyEnhancements(
    source: Bitmap,
    brightness: Float,   // -100 .. 100
    contrast: Float,     // -100 .. 100  (maps to factor 0..2)
    saturation: Float,   // -100 .. 100
    sharpness: Float,    // 0 .. 3
): Bitmap {
    val w = source.width; val h = source.height
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint  = Paint()

    val cm = ColorMatrix()

    // Saturation
    cm.setSaturation((saturation / 100f + 1f).coerceIn(0f, 2f))

    // Contrast + brightness
    val cf   = ((contrast + 100f) / 100f).coerceIn(0f, 2f)
    val bv   = brightness * 2.55f
    val trans = (-0.5f * cf + 0.5f) * 255f + bv
    val contrastMatrix = ColorMatrix(floatArrayOf(
        cf, 0f, 0f, 0f, trans,
        0f, cf, 0f, 0f, trans,
        0f, 0f, cf, 0f, trans,
        0f, 0f, 0f, 1f, 0f,
    ))
    cm.postConcat(contrastMatrix)

    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(source, 0f, 0f, paint)

    // Simple unsharp mask sharpening
    if (sharpness > 0f) {
        return sharpen(result, sharpness)
    }
    return result
}

private fun sharpen(src: Bitmap, amount: Float): Bitmap {
    val w = src.width; val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    // Box blur for unsharp mask
    val blurred = boxBlur(pixels, w, h, 1)
    val out = IntArray(pixels.size)
    for (i in pixels.size - 1 downTo 0) {
        val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
        val br = Color.red(blurred[i]); val bg = Color.green(blurred[i]); val bb = Color.blue(blurred[i])
        val nr = (r + amount * (r - br)).toInt().coerceIn(0, 255)
        val ng = (g + amount * (g - bg)).toInt().coerceIn(0, 255)
        val nb = (b + amount * (b - bb)).toInt().coerceIn(0, 255)
        out[i] = Color.rgb(nr, ng, nb)
    }
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(out, 0, w, 0, 0, w, h)
    return result
}

private fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int): IntArray {
    val tmp  = IntArray(pixels.size)
    val out  = IntArray(pixels.size)
    // Horizontal pass
    for (y in 0 until h) {
        var rS = 0; var gS = 0; var bS = 0; var n = 0
        for (x in max(0, -r)..min(w - 1, r)) { rS += Color.red(pixels[y*w+x]); gS += Color.green(pixels[y*w+x]); bS += Color.blue(pixels[y*w+x]); n++ }
        for (x in 0 until w) {
            tmp[y*w+x] = Color.rgb(rS/n, gS/n, bS/n)
            val ax = min(w-1, x+r+1); rS += Color.red(pixels[y*w+ax]); gS += Color.green(pixels[y*w+ax]); bS += Color.blue(pixels[y*w+ax]); n++
            val rx = max(0, x-r);     rS -= Color.red(pixels[y*w+rx]); gS -= Color.green(pixels[y*w+rx]); bS -= Color.blue(pixels[y*w+rx]); n--
        }
    }
    // Vertical pass
    for (x in 0 until w) {
        var rS = 0; var gS = 0; var bS = 0; var n = 0
        for (y in max(0, -r)..min(h-1, r)) { rS += Color.red(tmp[y*w+x]); gS += Color.green(tmp[y*w+x]); bS += Color.blue(tmp[y*w+x]); n++ }
        for (y in 0 until h) {
            out[y*w+x] = Color.rgb(rS/n, gS/n, bS/n)
            val ay = min(h-1, y+r+1); rS += Color.red(tmp[ay*w+x]); gS += Color.green(tmp[ay*w+x]); bS += Color.blue(tmp[ay*w+x]); n++
            val ry = max(0, y-r);     rS -= Color.red(tmp[ry*w+x]); gS -= Color.green(tmp[ry*w+x]); bS -= Color.blue(tmp[ry*w+x]); n--
        }
    }
    return out
}

private data class Preset(val name: String, val icon: String, val brightness: Float, val contrast: Float, val saturation: Float, val sharpness: Float)

private val PRESETS = listOf(
    Preset("Auto Enhance",  "🪄",  5f,  20f, 20f, 1.2f),
    Preset("Vivid",         "🌈",  0f,  30f, 40f, 1.0f),
    Preset("Sharpen",       "🔪",  0f,  10f,  5f, 2.5f),
    Preset("Soft Glow",     "✨",  10f, -10f, 10f, 0.3f),
    Preset("High Contrast", "🏔️",  0f,  50f,  0f, 1.0f),
    Preset("Custom",        "🎛️",  0f,   0f,  0f, 0f),
)

@Composable
fun ImageEnhancerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var sourceUri   by remember { mutableStateOf<Uri?>(null) }
    var sourceBmp   by remember { mutableStateOf<Bitmap?>(null) }
    var resultBmp   by remember { mutableStateOf<Bitmap?>(null) }
    var processing  by remember { mutableStateOf(false) }
    var activePreset by remember { mutableStateOf("Auto Enhance") }
    var brightness  by remember { mutableFloatStateOf(5f) }
    var contrast    by remember { mutableFloatStateOf(20f) }
    var saturation  by remember { mutableFloatStateOf(20f) }
    var sharpness   by remember { mutableFloatStateOf(1.2f) }
    var status      by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            sourceUri = it
            resultBmp = null
            scope.launch(Dispatchers.IO) {
                val bmp: Bitmap = context.contentResolver.openInputStream(it)!!.use {
                    val raw = BitmapFactory.decodeStream(it)
                    // Scale down if very large to avoid OOM
                    val maxSide = 2000
                    if (raw.width > maxSide || raw.height > maxSide) {
                        val ratio = min(maxSide.toFloat() / raw.width, maxSide.toFloat() / raw.height)
                        Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt(), (raw.height * ratio).toInt(), true)
                    } else raw
                }
                withContext(Dispatchers.Main) { sourceBmp = bmp }
            }
        }
    }

    fun applyPreset(preset: Preset) {
        activePreset = preset.name
        if (preset.name != "Custom") {
            brightness = preset.brightness; contrast = preset.contrast
            saturation = preset.saturation; sharpness = preset.sharpness
        }
    }

    // Live preview: re-render automatically whenever the source image or any
    // adjustment changes, debounced so dragging a slider doesn't spam full-res renders.
    LaunchedEffect(sourceBmp, brightness, contrast, saturation, sharpness) {
        val bmp = sourceBmp ?: return@LaunchedEffect
        delay(150)
        processing = true
        val enhanced = withContext(Dispatchers.Default) {
            applyEnhancements(bmp, brightness, contrast, saturation, sharpness)
        }
        resultBmp = enhanced
        processing = false
        status = ""
    }

    fun save() {
        val bmp = resultBmp ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "enhanced_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechPerByteTools")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
        title = "Image Enhancer",
        navController = navController,
        subtitle = "Adjust brightness, contrast, saturation and sharpness. Apply one-tap presets or fine-tune manually.",
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
                "Tap 'Pick an Image' to load a photo from your device.",
                "Choose a quick preset (Vivid, Sharp, etc.) or adjust sliders manually.",
                "Use the Crop & Rotate panel to trim or straighten your image.",
                "Drag the before/after slider on the preview to compare changes.",
                "Tap 'Save Enhanced Image' to store the result in your gallery.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(if (sourceUri == null) "🖼️  Pick an Image" else "🖼️  Image loaded — tap to change", color = if (sourceUri == null) TextSecondary else Accent, fontWeight = FontWeight.SemiBold)
            }

            sourceBmp?.let { src ->
                Image(
                    bitmap = src.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            if (sourceBmp != null) {
                // Presets
                Text("Presets", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { preset ->
                                val sel = activePreset == preset.name
                                OutlinedButton(
                                    onClick = { applyPreset(preset) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (sel) Primary else Border),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) androidx.compose.ui.graphics.Color(0x1F1F6FEB) else Surface),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(preset.icon, fontSize = 16.sp)
                                        Text(preset.name, fontSize = 9.sp, color = if (sel) Accent else TextSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Sliders
                Text("Manual Adjustments", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SliderRow("Brightness", brightness, -100f, 100f) { brightness = it; activePreset = "Custom" }
                        SliderRow("Contrast",   contrast,   -100f, 100f) { contrast   = it; activePreset = "Custom" }
                        SliderRow("Saturation", saturation, -100f, 100f) { saturation = it; activePreset = "Custom" }
                        SliderRow("Sharpness",  sharpness,  0f,    3f)   { sharpness  = it; activePreset = "Custom" }
                    }
                }

                // Live preview — updates automatically as sliders/presets change
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, SuccessDark), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Live Preview", color = Success, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (processing) CircularProgressIndicator(color = Success, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        }
                        (resultBmp ?: sourceBmp)?.let { bmp ->
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Enhanced", modifier = Modifier.fillMaxWidth())
                        }
                        Button(
                            onClick = { showSaveDialog = true },
                            enabled = resultBmp != null && !processing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("⬇  Save Enhanced PNG", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, color = TextMuted)
            Text("%.1f".format(value), fontSize = 12.sp, color = TextSecondary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            modifier = Modifier.height(28.dp))
    }
}
