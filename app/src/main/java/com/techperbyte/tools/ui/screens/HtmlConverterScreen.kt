package com.techperbyte.tools.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream

private val SAMPLE_HTML = """<div>
  <h2>Hello World</h2>
  <p>This is a <strong>sample</strong> HTML snippet.</p>
  <ul><li>Item one</li><li>Item two</li></ul>
  <a href="https://techperbyte.vercel.app">Visit TechPerByte</a>
</div>"""

private fun extractText(html: String): String =
    Jsoup.parse(html).wholeText().trim()

private fun prettify(html: String): String =
    Jsoup.parse(html).outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(true)).html()

private fun minify(html: String): String =
    Jsoup.parse(html).outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false).indentAmount(0)).html()
        .replace(Regex("\\s{2,}"), " ")
        .replace(Regex("> <"), "><")
        .trim()

@Composable
fun HtmlConverterScreen(navController: NavController) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var mode         by remember { mutableStateOf("prettify") }
    var input        by remember { mutableStateOf("") }
    var copied       by remember { mutableStateOf(false) }
    var imageUri     by remember { mutableStateOf<Uri?>(null) }
    var imageHtml    by remember { mutableStateOf("") }
    var imageLoading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            imageUri = it; imageHtml = ""; imageLoading = true
            scope.launch(Dispatchers.IO) {
                try {
                    val maxSide = 1024
                    val bmp = decodeSampledBitmap(context, it, maxSide)
                        ?: throw IllegalStateException("Could not read the image")
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    val html = """<img src="data:image/jpeg;base64,$b64" alt="image" style="max-width:100%;height:auto">"""
                    withContext(Dispatchers.Main) { imageHtml = html; imageLoading = false }
                } catch (e: OutOfMemoryError) {
                    withContext(Dispatchers.Main) { imageHtml = "Error: Image is too large to process on this device."; imageLoading = false }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { imageHtml = "Error: ${e.message}"; imageLoading = false }
                }
            }
        }
    }

    val output = remember(input, mode) {
        if (input.isBlank()) "" else try {
            when (mode) {
                "text"    -> extractText(input)
                "minify"  -> minify(input)
                else      -> prettify(input)
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    ToolScaffold(
        title = "HTML Converter",
        navController = navController,
        subtitle = "Prettify, minify, or extract plain text from HTML. Powered by Jsoup — fully offline.",
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
                "Paste your HTML code into the input box.",
                "Choose a mode: Prettify (format it), Minify (compress it), or Extract Text.",
                "Tap 'Convert' to process the HTML.",
                "Tap 'Copy Result' to copy the output to your clipboard.",
            ))
            // Mode tabs
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "prettify" to "✨ Prettify",
                    "minify"   to "📦 Minify",
                    "text"     to "📝 Extract Text",
                    "image"    to "🖼️ Image → HTML",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = mode == key,
                        onClick  = { mode = key },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor     = androidx.compose.ui.graphics.Color.White,
                            containerColor         = SurfaceVariant,
                            labelColor             = TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = mode == key, borderColor = Border, selectedBorderColor = Primary),
                    )
                }
            }

            if (mode == "image") {
                // ── Image → HTML mode ─────────────────────────────────────
                Text(
                    "Pick any image — it will be embedded as a base64 <img> tag you can paste anywhere.",
                    color = TextSecondary, fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (imageUri != null) Accent else Border),
                ) {
                    Text(
                        if (imageUri == null) "🖼️  Pick an Image" else "🖼️  Image selected — tap to change",
                        color = if (imageUri == null) TextSecondary else Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (imageLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = SurfaceVariant)
                }

                if (imageUri != null && !imageLoading) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }

                if (imageHtml.isNotEmpty() && !imageHtml.startsWith("Error")) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Generated HTML", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(imageHtml)); copied = true }) {
                            Text(if (copied) "✓ Copied" else "📋 Copy", color = if (copied) Success else Accent, fontSize = 12.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        border = BorderStroke(1.dp, Border),
                        shape  = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            imageHtml.take(300) + if (imageHtml.length > 300) "\n…[base64 data truncated for display]" else "",
                            color = TextPrimary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Text("${imageHtml.length} characters total (includes base64 image data)", color = TextSecondary, fontSize = 11.sp)
                }

                if (imageHtml.startsWith("Error")) {
                    Text(imageHtml, color = Danger, fontSize = 13.sp)
                }

            } else {
                // ── Text-based modes (prettify / minify / extract text) ───

                // Description
                val desc = when (mode) {
                    "text"   -> "Strips all HTML tags and returns clean readable text."
                    "minify" -> "Removes whitespace and comments to shrink the HTML."
                    else     -> "Formats HTML with proper indentation."
                }
                Text(desc, color = TextSecondary, fontSize = 12.sp)

                // Input
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("HTML Input", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                    TextButton(onClick = { input = SAMPLE_HTML }) { Text("Load sample", color = Accent, fontSize = 12.sp) }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 280.dp),
                    placeholder = { Text("Paste HTML here…", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = htmlTextFieldColors(),
                )

                // Output
                if (output.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        val outLabel = when (mode) { "text" -> "Extracted Text"; "minify" -> "Minified HTML"; else -> "Formatted HTML" }
                        Text(outLabel, fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (mode == "minify" && input.isNotEmpty()) {
                                val pct = ((1f - output.length.toFloat() / input.length) * 100).toInt().coerceAtLeast(0)
                                Text("$pct% smaller", color = Success, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(output)); copied = true
                            }) { Text(if (copied) "✓ Copied" else "📋 Copy", color = if (copied) Success else Accent, fontSize = 12.sp) }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        border = BorderStroke(1.dp, Border),
                        shape  = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            output,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Text("${output.length} characters", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun htmlTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent, unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
)
