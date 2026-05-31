package com.techperbyte.tools.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.techperbyte.tools.Screen
import com.techperbyte.tools.ui.theme.*

// ── Banner Ad ─────────────────────────────────────────────────────────────────
// Replace the adUnitId below with your real banner ID from admob.google.com
// Test ID (shows test ads only): ca-app-pub-3940256099942544/6300978111
private const val BANNER_AD_UNIT_ID = "ca-app-pub-1036439297645933/6495653989"

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// ── Category enum ─────────────────────────────────────────────────────────────

enum class ToolCategory(val label: String, val icon: String) {
    PDF("PDF Tools",     "📄"),
    Image("Image Tools", "🖼️"),
    Generate("Generate", "⚡"),
    Utilities("Utilities","🔄"),
}

// ── Tool data ─────────────────────────────────────────────────────────────────

data class ToolEntry(
    val title: String,
    val icon: String,
    val desc: String,
    val route: String,
    val offline: Boolean = true,
    val category: ToolCategory,
)

val ALL_TOOLS = listOf(
    ToolEntry("PDF Merger",         "📄", "Combine multiple PDFs into one",             Screen.PdfMerger.route,        category = ToolCategory.PDF),
    ToolEntry("PDF Compressor",     "🗜️", "Reduce PDF file size",                      Screen.PdfCompressor.route,    category = ToolCategory.PDF),
    ToolEntry("PDF Flipbook",       "📖", "Browse PDF pages as a flipbook",             Screen.PdfFlipbook.route,      category = ToolCategory.PDF),
    ToolEntry("PDF OCR",            "🤖", "Extract text from PDF pages",                Screen.PdfOcr.route,           category = ToolCategory.PDF),
    ToolEntry("Image Compressor",   "🖼️", "Compress images without quality loss",      Screen.ImageCompress.route,    category = ToolCategory.Image),
    ToolEntry("Image Resizer",      "↔️",  "Resize images to any dimension",            Screen.ImageResize.route,      category = ToolCategory.Image),
    ToolEntry("Image Enhancer",     "✨", "Brightness, contrast, sharpen & crop",      Screen.ImageEnhancer.route,    category = ToolCategory.Image),
    ToolEntry("Image → Text (OCR)", "🔍", "Extract text from any image (offline)",      Screen.ImageToText.route,      category = ToolCategory.Image),
    ToolEntry("QR Code Generator",  "📱", "Generate & save QR codes instantly",         Screen.QrCode.route,           category = ToolCategory.Generate),
    ToolEntry("Email Signature",    "✉️",  "Create professional email signatures",      Screen.EmailSig.route,         category = ToolCategory.Generate),
    ToolEntry("YouTube Thumbnail",  "▶️",  "Download YouTube video thumbnails",         Screen.YouTubeThumbnail.route, offline = false, category = ToolCategory.Generate),
    ToolEntry("HTML Converter",     "🔄", "Prettify, minify & extract text",            Screen.HtmlConverter.route,    category = ToolCategory.Utilities),
    ToolEntry("JSON → Table",       "📊", "Convert JSON data into a table",             Screen.JsonToTable.route,      category = ToolCategory.Utilities),
    ToolEntry("Weather Forecast",   "🌤️", "Get accurate weather forecasts",            Screen.WeatherForecast.route,  offline = false, category = ToolCategory.Utilities),
)

// ── Home screen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var activeCategory by remember { mutableStateOf<ToolCategory?>(null) }

    val offlineCount = ALL_TOOLS.count { it.offline }
    val filteredTools = if (activeCategory == null) ALL_TOOLS
                        else ALL_TOOLS.filter { it.category == activeCategory }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
    ) {
        // ── Hero header ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column {
                Text(
                    "🛠️  TechPerByte Tools",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderBadge("$offlineCount offline tools")
                    HeaderBadge("No sign-up")
                    HeaderBadge("Free tools")
                }
            }
        }

        // ── Category filter bar ───────────────────────────────────────────────
        Surface(color = Surface) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip(
                        label = "All",
                        icon  = "🛠️",
                        count = ALL_TOOLS.size,
                        selected = activeCategory == null,
                        onClick  = { activeCategory = null },
                    )
                    ToolCategory.entries.forEach { cat ->
                        CategoryChip(
                            label    = cat.label,
                            icon     = cat.icon,
                            count    = ALL_TOOLS.count { it.category == cat },
                            selected = activeCategory == cat,
                            onClick  = { activeCategory = if (activeCategory == cat) null else cat },
                        )
                    }
                }
                HorizontalDivider(color = Border, thickness = 1.dp)
            }
        }

        // ── Tool grid ─────────────────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (activeCategory == null) {
                // Show grouped by category with section headers
                ToolCategory.entries.forEach { cat ->
                    val tools = filteredTools.filter { it.category == cat }
                    if (tools.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CategorySectionHeader(cat)
                        }
                        items(tools) { tool ->
                            ToolCard(tool = tool, onClick = { navController.navigate(tool.route) })
                        }
                    }
                }
            } else {
                items(filteredTools) { tool ->
                    ToolCard(tool = tool, onClick = { navController.navigate(tool.route) })
                }
            }

            // Footer as last grid item
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeFooter(onVisitWebsite = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://techperbyte.com")))
                })
            }
        }

        // ── Banner ad pinned at the bottom ───────────────────────────────────
        HorizontalDivider(color = Border, thickness = 1.dp)
        BannerAd()
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun HeaderBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Border),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor     = if (selected) Primary           else SurfaceVariant
    val borderColor = if (selected) PrimaryLight      else Border
    val textColor   = if (selected) Color.White       else TextSecondary
    val countBg     = if (selected) Color(0x3DFFFFFF) else Color(0x1AFFFFFF)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(icon, fontSize = 13.sp)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Surface(shape = RoundedCornerShape(10.dp), color = countBg) {
                Text(
                    "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(category: ToolCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(category.icon, fontSize = 18.sp)
        Text(
            category.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Border),
        )
    }
}

@Composable
fun ToolCard(tool: ToolEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tool.offline) Surface else Color(0xFF0E1A26),
        ),
        border = BorderStroke(1.dp, if (tool.offline) Border else Color(0xFF1A3A5C)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (tool.offline) SurfaceVariant else Color(0xFF0F2236),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tool.icon, fontSize = 22.sp)
                }
                if (!tool.offline) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0F2236),
                        border = BorderStroke(1.dp, Color(0xFF1A4A7A)),
                    ) {
                        Text(
                            "WiFi",
                            fontSize = 10.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tool.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (tool.offline) TextPrimary else TextMuted,
                lineHeight = 17.sp,
            )
            Text(
                tool.desc,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 3.dp),
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun HomeFooter(onVisitWebsite: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("🌐", fontSize = 14.sp)
            Text(
                "TechPerByte.com",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
            )
        }
        Text(
            "Free tools for everyone — no sign-up required.",
            fontSize = 12.sp,
            color = TextSecondary,
        )
        Button(
            onClick = onVisitWebsite,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("🔗  Visit Website", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}
