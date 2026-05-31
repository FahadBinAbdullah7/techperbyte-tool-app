package com.techperbyte.tools.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebView
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.techperbyte.tools.ui.components.ToolScaffold
import com.techperbyte.tools.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private fun buildSignatureHtml(
    name: String, title: String, company: String,
    email: String, phone: String, address: String,
    website: String, linkedin: String, twitter: String,
    logoBase64: String,
): String {
    val socials = buildList {
        if (linkedin.isNotBlank()) add("<a href='$linkedin' style='color:#007bff;text-decoration:none;margin-right:8px'>LinkedIn</a>")
        if (twitter.isNotBlank())  add("<a href='$twitter'  style='color:#007bff;text-decoration:none;margin-right:8px'>Twitter</a>")
    }
    val infoLines = buildList {
        if (name.isNotBlank())    add("<div style='margin-bottom:3px'><strong style='font-size:16px;color:#222'>$name</strong></div>")
        if (title.isNotBlank() || company.isNotBlank())
            add("<div style='margin-bottom:3px;color:#555'>${listOf(title, company).filter { it.isNotBlank() }.joinToString(", ")}</div>")
        if (email.isNotBlank())   add("<div style='margin-bottom:3px'>📧 <a href='mailto:$email' style='color:#007bff;text-decoration:none'>$email</a></div>")
        if (phone.isNotBlank())   add("<div style='margin-bottom:3px'>📞 $phone</div>")
        if (address.isNotBlank()) add("<div style='margin-bottom:3px'>📍 $address</div>")
        if (website.isNotBlank()) add("<div style='margin-bottom:3px'>🌐 <a href='$website' style='color:#007bff;text-decoration:none'>$website</a></div>")
        if (socials.isNotEmpty()) add("<div style='margin-top:4px'>${socials.joinToString("")}</div>")
    }
    val logoCell = if (logoBase64.isNotEmpty())
        "<td style='padding-right:15px;vertical-align:middle'><img src='$logoBase64' alt='Logo' style='max-height:70px;max-width:100px;object-fit:contain'></td>"
    else ""
    return """<table cellpadding="0" cellspacing="0" style="font-family:'Segoe UI',Arial,sans-serif;font-size:14px;color:#333">
<tr>
${logoCell}<td style="width:2px;background:#007bff;padding:0"></td>
<td style="padding:8px 0 8px 14px">${infoLines.joinToString("")}</td>
</tr>
</table>"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmailSignatureScreen(navController: NavController) {
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()

    var name       by remember { mutableStateOf("") }
    var title      by remember { mutableStateOf("") }
    var company    by remember { mutableStateOf("") }
    var email      by remember { mutableStateOf("") }
    var phone      by remember { mutableStateOf("") }
    var address    by remember { mutableStateOf("") }
    var website    by remember { mutableStateOf("") }
    var linkedin   by remember { mutableStateOf("") }
    var twitter    by remember { mutableStateOf("") }
    var logoUri    by remember { mutableStateOf<Uri?>(null) }
    var logoBase64 by remember { mutableStateOf("") }
    var sigHtml    by remember { mutableStateOf("") }
    var copied     by remember { mutableStateOf(false) }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            logoUri = it
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(it)!!.use { s -> s.readBytes() }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    withContext(Dispatchers.Main) { logoBase64 = "data:image/jpeg;base64,$b64" }
                } catch (_: Exception) { }
            }
        }
    }

    ToolScaffold(
        title = "Email Signature Generator",
        navController = navController,
        subtitle = "Build a professional HTML email signature. Copy it directly into Gmail or Outlook.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HowToCard(listOf(
                "Fill in your name, job title, company, email, and phone number.",
                "Optionally add social media links (LinkedIn, Twitter, GitHub, Website).",
                "The signature preview updates live as you type.",
                "Tap 'Copy HTML' to copy the ready-to-paste email signature code.",
            ))
            SigField("Full Name",      name)      { name = it }
            SigField("Job Title",      title)     { title = it }
            SigField("Company",        company)   { company = it }
            SigField("Email",          email)     { email = it }
            SigField("Phone / Mobile", phone)     { phone = it }
            SigField("Office Address", address)   { address = it }
            SigField("Website URL",    website)   { website = it }
            SigField("LinkedIn URL",   linkedin)  { linkedin = it }
            SigField("Twitter URL",    twitter)   { twitter = it }

            // Company logo
            Text("Company Logo (optional)", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { logoLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (logoUri != null) Accent else Border),
            ) {
                Text(
                    if (logoUri == null) "🖼️  Upload Company Logo" else "🖼️  Logo uploaded — tap to change",
                    color = if (logoUri == null) TextSecondary else Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (logoUri != null) {
                AsyncImage(
                    model = logoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            Button(
                onClick = { sigHtml = buildSignatureHtml(name, title, company, email, phone, address, website, linkedin, twitter, logoBase64) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape  = RoundedCornerShape(10.dp),
            ) { Text("✉️  Generate Signature", fontWeight = FontWeight.Bold) }

            if (sigHtml.isNotEmpty()) {
                // Live WebView preview
                Text("Preview", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 13.sp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Border),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                setBackgroundColor(android.graphics.Color.WHITE)
                            }
                        },
                        update = { wv -> wv.loadData(sigHtml, "text/html", "UTF-8") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                    )
                }

                // HTML code
                Text("HTML Code", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 13.sp)
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariant), border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(10.dp)) {
                    Text(sigHtml, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp, modifier = Modifier.padding(10.dp))
                }

                Button(
                    onClick = { clipboard.setText(AnnotatedString(sigHtml)); copied = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessDark),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text(if (copied) "✓ HTML Copied!" else "📋  Copy HTML", fontWeight = FontWeight.Bold) }

                Text("Paste the copied HTML into your email client's signature editor.", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary,
            cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
        ),
    )
}
