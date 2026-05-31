package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrCodeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var text      by remember { mutableStateOf("https://techperbyte.com") }
    var qrSize    by remember { mutableIntStateOf(600) }
    var qrBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var status    by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun generate() {
        scope.launch(Dispatchers.Default) {
            try {
                val writer = QRCodeWriter()
                val matrix = writer.encode(text.trim().ifEmpty { " " }, BarcodeFormat.QR_CODE, qrSize, qrSize)
                val bmp = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888)
                for (x in 0 until qrSize) {
                    for (y in 0 until qrSize) {
                        bmp.setPixel(x, y, if (matrix[x, y]) AColor.BLACK else AColor.WHITE)
                    }
                }
                withContext(Dispatchers.Main) { qrBitmap = bmp; status = "" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Error: ${e.message}" }
            }
        }
    }

    fun saveToGallery() {
        val bmp = qrBitmap ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "qrcode_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TechPerByteTools")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) { status = "✓ Saved to Pictures/TechPerByteTools" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Save failed: ${e.message}" }
            }
        }
    }

    LaunchedEffect(Unit) { generate() }

    if (showSaveDialog) SaveConfirmDialog(
        destination = "Pictures/TechPerByteTools",
        onConfirm   = { showSaveDialog = false; saveToGallery() },
        onDismiss   = { showSaveDialog = false },
    )

    ToolScaffold(
        title = "QR Code Generator",
        navController = navController,
        subtitle = "Generate a QR code for any URL, text, email, or phone number. Works fully offline.",
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
                "Type any text, URL, email, or phone number in the input field.",
                "The QR code generates instantly as you type.",
                "Use the size slider to adjust the QR code resolution.",
                "Tap 'Save QR Code' to store the image in your Pictures folder.",
            ))
            // Input
            Text("Text or URL", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Enter URL, text, WiFi credentials…", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = Accent,
                    focusedContainerColor   = Surface,
                    unfocusedContainerColor = Surface,
                ),
            )

            // Size row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Size:", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                listOf(300, 600, 1000).forEach { s ->
                    FilterChip(
                        selected = qrSize == s,
                        onClick  = { qrSize = s },
                        label    = { Text("${s}px", fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor     = androidx.compose.ui.graphics.Color.White,
                            containerColor         = SurfaceVariant,
                            labelColor             = TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled               = true,
                            selected              = qrSize == s,
                            borderColor           = Border,
                            selectedBorderColor   = Primary,
                        ),
                    )
                }
            }

            Button(
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Generate QR Code", fontWeight = FontWeight.Bold)
            }

            // Preview
            qrBitmap?.let { bmp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = Surface),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(220.dp),
                            )
                        }
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("⬇  Save to Gallery", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = if (status.startsWith("✓")) Success else Danger,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
