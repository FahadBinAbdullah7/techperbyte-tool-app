package com.techperbyte.tools.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

private val SAMPLE = """[
  {"id":1,"name":"Alice","address":{"city":"Dhaka","country":"BD"},"score":95,"active":true},
  {"id":2,"name":"Bob",  "address":{"city":"London","country":"UK"},"score":78,"active":false},
  {"id":3,"name":"Carol","address":{"city":"Tokyo", "country":"JP"},"score":99,"active":true}
]"""

private fun flattenObject(obj: JSONObject, prefix: String = ""): Map<String, String> {
    val result = mutableMapOf<String, String>()
    obj.keys().forEach { key ->
        val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
        when (val value = obj.get(key)) {
            is JSONObject -> result.putAll(flattenObject(value, fullKey))
            is JSONArray  -> {
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is JSONObject) result.putAll(flattenObject(item, "$fullKey[$i]"))
                    else result["$fullKey[$i]"] = item?.toString() ?: ""
                }
            }
            else -> result[fullKey] = value?.toString() ?: ""
        }
    }
    return result
}

private fun parseJson(raw: String): Pair<List<Map<String, String>>, List<String>> {
    val rows = mutableListOf<Map<String, String>>()
    when (val parsed = when {
        raw.trimStart().startsWith("[") -> JSONArray(raw)
        else -> JSONArray().apply { put(JSONObject(raw)) }
    }) {
        is JSONArray -> for (i in 0 until parsed.length()) {
            val item = parsed.optJSONObject(i) ?: JSONObject().apply { put("value", parsed.opt(i)) }
            rows.add(flattenObject(item))
        }
    }
    val cols = rows.flatMap { it.keys }.distinct()
    return Pair(rows, cols)
}

@Composable
fun JsonToTableScreen(navController: NavController) {
    val clipboard = LocalClipboardManager.current

    var input   by remember { mutableStateOf("") }
    var rows    by remember { mutableStateOf<List<Map<String, String>>?>(null) }
    var cols    by remember { mutableStateOf<List<String>>(emptyList()) }
    var visible by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error   by remember { mutableStateOf("") }
    var copied  by remember { mutableStateOf(false) }

    fun generate() {
        error = ""
        try {
            val (r, c) = parseJson(input.trim())
            rows = r; cols = c; visible = c.toSet()
        } catch (e: Exception) {
            rows = null; error = "Invalid JSON: ${e.message}"
        }
    }

    fun toCsv(): String {
        val r = rows ?: return ""
        val visCols = cols.filter { it in visible }
        val escape = { v: String -> if (v.contains(",") || v.contains('"')) "\"${v.replace("\"", "\"\"")}\"" else v }
        return buildString {
            appendLine(visCols.joinToString(","))
            r.forEach { row -> appendLine(visCols.joinToString(",") { escape(row[it] ?: "") }) }
        }
    }

    ToolScaffold(
        title = "JSON → Table",
        navController = navController,
        subtitle = "Paste any JSON array and visualise it as a table. Nested objects are auto-flattened.",
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
                "Paste a JSON array (list of objects) into the input box.",
                "Tap 'Load example' to try a sample dataset first.",
                "Tap 'Generate Table' to convert the JSON into a readable table.",
                "Use the column chips to show or hide individual columns.",
                "Tap 'Copy CSV' to export the data as a comma-separated file.",
            ))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("JSON Input", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                TextButton(onClick = { input = SAMPLE }) { Text("Load example", color = Accent, fontSize = 12.sp) }
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it; rows = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                placeholder = { Text("[{\"key\": \"value\"}]", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                ),
            )

            Button(
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape  = RoundedCornerShape(10.dp),
            ) { Text("📊  Generate Table", fontWeight = FontWeight.Bold) }

            if (error.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0x1AF85149)), border = BorderStroke(1.dp, Danger), shape = RoundedCornerShape(8.dp)) {
                    Text(error, color = Danger, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            rows?.let { r ->
                // Column toggle
                Text("Columns (${visible.size}/${cols.size} shown)", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.forEach { col ->
                        val sel = col in visible
                        FilterChip(
                            selected = sel,
                            onClick  = { visible = if (sel) visible - col else visible + col },
                            label    = { Text(col, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = SurfaceVariant, labelColor = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, borderColor = Border, selectedBorderColor = Primary),
                        )
                    }
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${r.size} rows · ${visible.size} cols", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(toCsv())); copied = true }) {
                        Text(if (copied) "✓ Copied" else "Copy CSV", color = if (copied) Success else Accent, fontSize = 12.sp)
                    }
                }

                // Table
                val visCols = cols.filter { it in visible }
                if (visCols.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(10.dp)) {
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Column {
                                // Header
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    TableCell("#", isHeader = true, width = 36)
                                    visCols.forEach { TableCell(it, isHeader = true) }
                                }
                                HorizontalDivider(color = Border)
                                // Rows
                                r.forEachIndexed { idx, row ->
                                    Row {
                                        TableCell("${idx + 1}", isHeader = false, isIndex = true, width = 36)
                                        visCols.forEach { col ->
                                            val v = row[col] ?: ""
                                            TableCell(v, isHeader = false, isBool = v == "true" || v == "false")
                                        }
                                    }
                                    if (idx < r.lastIndex) HorizontalDivider(color = Border.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                    Text("💡 Nested objects auto-flattened to dot notation (e.g. address.city)", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    isHeader: Boolean,
    isIndex: Boolean = false,
    isBool: Boolean  = false,
    width: Int = 120,
) {
    val color = when {
        isHeader -> TextMuted
        isIndex  -> TextSecondary
        isBool && text == "true"  -> Success
        isBool && text == "false" -> Danger
        else -> TextPrimary
    }
    val bg = if (isHeader) SurfaceVariant else Surface
    Box(
        modifier = Modifier
            .width(width.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 2,
        )
    }
}
