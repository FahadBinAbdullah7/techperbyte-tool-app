package com.techperbyte.tools.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techperbyte.tools.ui.theme.*

fun fmtBytes(b: Long): String = when {
    b < 1024        -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024f)
    else            -> "%.2f MB".format(b / (1024f * 1024f))
}

fun uriFileSize(context: Context, uri: Uri): Long {
    return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.SIZE)
        if (c.moveToFirst() && idx >= 0) c.getLong(idx) else 0L
    } ?: 0L
}

/**
 * Decodes [uri] downsampled so neither side exceeds ~[reqSide] px, using
 * BitmapFactory's inSampleSize so the full-resolution image is never fully
 * allocated in memory. A 50-100MP camera photo decoded at native resolution
 * (~400MB+ as ARGB_8888) is a common OutOfMemoryError crash on real devices —
 * every screen that decodes a user-picked photo should go through this
 * instead of calling BitmapFactory.decodeStream directly.
 */
fun decodeSampledBitmap(context: Context, uri: Uri, reqSide: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    if (bounds.outHeight > reqSide || bounds.outWidth > reqSide) {
        val halfH = bounds.outHeight / 2
        val halfW = bounds.outWidth / 2
        while (halfH / sampleSize >= reqSide && halfW / sampleSize >= reqSide) sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
} catch (_: OutOfMemoryError) {
    null
}

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun StatBox(label: String, value: String, bg: Color, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun HowToCard(steps: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = SurfaceVariant),
        border    = BorderStroke(1.dp, Border),
        shape     = RoundedCornerShape(10.dp),
        onClick   = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ℹ️  How to use",
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    if (expanded) "▲  Hide" else "▼  Show",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                steps.forEachIndexed { idx, step ->
                    Row(
                        modifier = Modifier.padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Primary,
                            modifier = Modifier.size(18.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(step, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SaveConfirmDialog(
    destination: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text("Save File", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Text(
                "The file will be saved to\n$destination",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(8.dp),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun NoNetworkDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text("No Internet Connection", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Text(
                "This tool requires an internet connection. Please connect to Wi-Fi or mobile data and try again.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(8.dp),
            ) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(12.dp),
    )
}
