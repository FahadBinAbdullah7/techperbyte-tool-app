package com.techperbyte.tools.ui.screens

import android.net.Uri
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
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
fun ImageToTextScreen(navController: NavController) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var sourceUri  by remember { mutableStateOf<Uri?>(null) }
    var extracting by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var copied     by remember { mutableStateOf(false) }
    var status     by remember { mutableStateOf("") }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { sourceUri = it; resultText = ""; status = "" }
    }

    fun extract() {
        val uri = sourceUri ?: return
        extracting = true
        scope.launch(Dispatchers.IO) {
            try {
                // OCR accuracy doesn't benefit from native resolution — cap it to
                // avoid OOM on very large camera photos.
                val bmp = decodeSampledBitmap(context, uri, 2500)
                    ?: throw IllegalStateException("Could not read the image")
                val image = InputImage.fromBitmap(bmp, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val result = recognizer.process(image).await()
                withContext(Dispatchers.Main) {
                    resultText = result.text.ifEmpty { "(No text detected in this image)" }
                    extracting = false; status = ""
                }
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { extracting = false; status = "Error: Image is too large to process on this device." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { extracting = false; status = "Error: ${e.message}" }
            }
        }
    }

    ToolScaffold(
        title = "Image → Text (OCR)",
        navController = navController,
        subtitle = "Extract text from any image using Google ML Kit. Works 100% offline — no data sent anywhere.",
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
                "Tap 'Pick an Image' to select a photo that contains text.",
                "The text is extracted automatically using Google ML Kit (100% offline).",
                "Tap 'Copy Text' to copy the result to your clipboard.",
                "Works best on clear, well-lit images with readable fonts.",
            ))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariant), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(10.dp)) {
                Text(
                    "🔍  Powered by Google ML Kit — works 100% offline. The OCR model is bundled in the app.",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(12.dp), lineHeight = 18.sp,
                )
            }

            Button(
                onClick = { pickLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) {
                Text(
                    if (sourceUri == null) "🖼️  Pick an Image" else "🖼️  Image selected — tap to change",
                    color = if (sourceUri == null) TextSecondary else Accent, fontWeight = FontWeight.SemiBold,
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
                Button(
                    onClick = { extract() },
                    enabled = !extracting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text(if (extracting) "Extracting text…" else "🔍  Extract Text", fontWeight = FontWeight.Bold) }
            }

            if (extracting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = SurfaceVariant)
            }

            if (resultText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Extracted Text", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(resultText))
                                copied = true
                            }) {
                                Text(if (copied) "✓ Copied!" else "📋 Copy", color = if (copied) Success else Accent, fontSize = 13.sp)
                            }
                        }
                        Text("${resultText.length} characters · ${resultText.lines().size} lines", color = TextSecondary, fontSize = 11.sp)
                        HorizontalDivider(color = Border)
                        Text(
                            resultText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = Danger, fontSize = 13.sp)
        }
    }
}
