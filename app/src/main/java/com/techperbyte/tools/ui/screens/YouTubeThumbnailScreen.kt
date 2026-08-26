package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val VIDEO_ID_REGEX = Regex(
    """(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""
)

private fun extractVideoId(input: String): String? {
    val trimmed = input.trim()
    // Direct 11-char ID
    if (trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) return trimmed
    return VIDEO_ID_REGEX.find(trimmed)?.groupValues?.get(1)
}

private fun thumbnailUrl(videoId: String, quality: String): String = when (quality) {
    "Max"    -> "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
    "High"   -> "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    else     -> "https://img.youtube.com/vi/$videoId/sddefault.jpg"
}

@Composable
fun YouTubeThumbnailScreen(navController: NavController) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()

    var input       by remember { mutableStateOf("") }
    var videoId     by remember { mutableStateOf<String?>(null) }
    var quality     by remember { mutableStateOf("Max") }
    var error       by remember { mutableStateOf("") }
    var showNoNet   by remember { mutableStateOf(false) }
    var saveDialog  by remember { mutableStateOf(false) }
    var status      by remember { mutableStateOf("") }

    fun fetch() {
        if (!isNetworkAvailable(context)) { showNoNet = true; return }
        val id = extractVideoId(input)
        if (id == null) { error = "Could not find a valid YouTube video ID in that URL."; return }
        error = ""; status = ""; videoId = id
    }

    fun saveThumb() {
        val id = videoId ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val url = thumbnailUrl(id, quality)
                val loader = ImageLoader(context)
                val req    = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = loader.execute(req)
                val bmp    = ((result as SuccessResult).drawable as BitmapDrawable).bitmap
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "yt_thumb_${id}_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechPerByteTools")
                }
                val outUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(outUri)!!.use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                withContext(Dispatchers.Main) { status = "✓ Saved to Pictures/TechPerByteTools" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Save failed: ${e.message}" }
            }
        }
    }

    if (showNoNet) NoNetworkDialog { showNoNet = false }
    if (saveDialog) SaveConfirmDialog(
        destination = "Pictures/TechPerByteTools",
        onConfirm   = { saveDialog = false; saveThumb() },
        onDismiss   = { saveDialog = false },
    )

    ToolScaffold(
        title = "YouTube Thumbnail",
        navController = navController,
        subtitle = "Paste a YouTube video URL or video ID to download its thumbnail.",
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
                "Paste a YouTube video URL into the input field.",
                "Choose a thumbnail quality: Max Res, High, or Standard.",
                "Tap 'Load Thumbnail' to preview the thumbnail image.",
                "Tap 'Save to Gallery' to download the thumbnail to your device.",
                "Requires an active internet connection.",
            ))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = ""; videoId = null; status = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtube.com/watch?v=...", color = TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                ),
            )

            // Quality picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Text("Quality:", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                listOf("Max", "High", "SD").forEach { q ->
                    FilterChip(
                        selected = quality == q,
                        onClick  = { quality = q; videoId?.let { videoId = it } },
                        label    = { Text(q, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                            containerColor = SurfaceVariant, labelColor = TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = quality == q, borderColor = Border, selectedBorderColor = Primary),
                    )
                }
            }

            Button(
                onClick = { fetch() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(10.dp),
            ) { Text("▶  Load Thumbnail", fontWeight = FontWeight.Bold) }

            if (error.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Danger), shape = RoundedCornerShape(10.dp)) {
                    Text(error, color = Danger, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            videoId?.let { id ->
                val url = thumbnailUrl(id, quality)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border),
                    shape  = RoundedCornerShape(12.dp),
                ) {
                    Column {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = "YouTube Thumbnail",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Video ID: $id", color = TextSecondary, fontSize = 12.sp)
                            Button(
                                onClick = { saveDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text("⬇  Save Thumbnail ($quality)", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}
