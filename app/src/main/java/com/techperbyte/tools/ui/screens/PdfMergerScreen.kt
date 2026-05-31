package com.techperbyte.tools.ui.screens

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun PdfMergerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var files        by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var names        by remember { mutableStateOf<List<String>>(emptyList()) }
    var merging      by remember { mutableStateOf(false) }
    var resultBytes  by remember { mutableStateOf<ByteArray?>(null) }
    var status       by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            files  = files + uris
            names  = names + uris.map { it.lastPathSegment?.substringAfterLast('/') ?: "file.pdf" }
            status = ""; resultBytes = null
        }
    }

    fun moveUp(i: Int) {
        if (i == 0) return
        val f = files.toMutableList(); val n = names.toMutableList()
        f[i-1] = f[i].also { f[i] = f[i-1] }
        n[i-1] = n[i].also { n[i] = n[i-1] }
        files = f; names = n
    }

    fun moveDown(i: Int) {
        if (i == files.lastIndex) return
        val f = files.toMutableList(); val n = names.toMutableList()
        f[i+1] = f[i].also { f[i] = f[i+1] }
        n[i+1] = n[i].also { n[i] = n[i+1] }
        files = f; names = n
    }

    fun remove(i: Int) {
        files = files.filterIndexed { idx, _ -> idx != i }
        names = names.filterIndexed { idx, _ -> idx != i }
        resultBytes = null
    }

    fun merge() {
        if (files.size < 2) return
        merging = true; resultBytes = null; status = ""
        scope.launch(Dispatchers.IO) {
            try {
                val merger  = PDFMergerUtility()
                val streams = files.map { context.contentResolver.openInputStream(it)!! }
                streams.forEach { merger.addSource(it) }
                val out = ByteArrayOutputStream()
                merger.destinationStream = out
                merger.mergeDocuments(null)
                streams.forEach { it.close() }
                val bytes = out.toByteArray()
                withContext(Dispatchers.Main) { resultBytes = bytes; merging = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { merging = false; status = "Error: ${e.message}" }
            }
        }
    }

    fun save() {
        val bytes = resultBytes ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "merged_${System.currentTimeMillis()}.pdf")
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
        title = "PDF Merger",
        navController = navController,
        subtitle = "Combine multiple PDF files into one. Drag arrows to reorder pages before merging.",
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
                "Tap 'Add PDF Files' to pick one or more PDFs from your device.",
                "Use the ▲ ▼ arrow buttons to reorder the files before merging.",
                "Tap ✕ to remove a file from the list.",
                "Tap 'Merge PDFs' — then review the result size and tap 'Save'.",
            ))
            Button(
                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, Border),
            ) { Text("📄  Add PDF Files", color = Accent, fontWeight = FontWeight.SemiBold) }

            if (files.isNotEmpty()) {
                Text("${files.size} file${if (files.size > 1) "s" else ""} — arrows to reorder:", color = TextMuted, fontSize = 13.sp)
                files.forEachIndexed { i, _ ->
                    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i+1}", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                            Text("📄 ${names[i]}", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { moveUp(i) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = if (i == 0) Border else TextSecondary)
                            }
                            IconButton(onClick = { moveDown(i) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = if (i == files.lastIndex) Border else TextSecondary)
                            }
                            IconButton(onClick = { remove(i) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Danger)
                            }
                        }
                    }
                }
            }

            if (files.size >= 2 && resultBytes == null) {
                Button(
                    onClick = { merge() },
                    enabled = !merging,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text(if (merging) "Merging…" else "🔗  Merge ${files.size} PDFs into One", fontWeight = FontWeight.Bold) }
            } else if (files.size == 1) {
                Text("Add at least one more PDF to merge.", color = TextSecondary, fontSize = 13.sp)
            }

            resultBytes?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, SuccessDark), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("✓ Merged ${files.size} PDFs — ${fmtBytes(it.size.toLong())}", color = Success, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("⬇  Save Merged PDF", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("✓")) Success else Danger, fontSize = 13.sp)
        }
    }
}
