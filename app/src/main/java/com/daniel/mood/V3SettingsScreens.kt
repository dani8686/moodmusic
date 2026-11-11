package com.daniel.mood

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.util.Date

@Composable
fun ProfileScreen(fa: Boolean, onDna: () -> Unit, onHistory: () -> Unit, onAiSettings: () -> Unit, onSettings: () -> Unit, onPlaylists: () -> Unit, onQueue: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V3Header(tr(fa, "Your profile", "پروفایل شما"), tr(fa, "Data, intelligence and privacy controls", "کنترل داده، هوش و حریم خصوصی"), Icons.Default.Person) }
        item { V3Action(tr(fa, "Music DNA", "DNA موسیقی"), tr(fa, "Taste profile and evolution", "پروفایل و تحول سلایق"), Icons.Default.Insights, onDna) }
        item { V3Action(tr(fa, "Time Machine", "ماشین زمان"), tr(fa, "Calendar and listening statistics", "تقویم و آمار شنیدن"), Icons.Default.History, onHistory) }
        item { V3Action(tr(fa, "AI & online services", "هوش مصنوعی و سرویس‌های آنلاین"), tr(fa, "Providers, consent, limits and cache", "Provider، رضایت، محدودیت و کش"), Icons.Default.Cloud, onAiSettings) }
        item { V3Action(tr(fa, "Playlists", "پلی‌لیست‌ها"), tr(fa, "Create and manage collections", "ساخت و مدیریت مجموعه‌ها"), Icons.Default.LibraryMusic, onPlaylists) }
        item { V3Action(tr(fa, "Play queue", "صف پخش"), tr(fa, "Reorder and remove tracks", "جابه‌جایی و حذف آهنگ"), Icons.Default.QueueMusic, onQueue) }
        item { V3Action(tr(fa, "App settings", "تنظیمات برنامه"), tr(fa, "Language, analysis and battery", "زبان، تحلیل و باتری"), Icons.Default.Settings, onSettings) }
    }
}

@Composable
fun AiSettingsScreen(songs: SongDao, intelligence: IntelligenceDao, fa: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AiSettings.prefs(context) }
    val credentials = remember { CredentialStore(context) }
    var mode by remember { mutableStateOf(AiSettings.mode(context)) }
    var automatic by remember { mutableStateOf(AiSettings.automatic(context)) }
    var ask by remember { mutableStateOf(AiSettings.askBeforeUpload(context)) }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifiOnly", true)) }
    var daily by remember { mutableIntStateOf(AiSettings.dailyLimit(context)) }
    var maxUpload by remember { mutableIntStateOf(prefs.getInt("maxUploadMb", 15)) }
    var model by remember { mutableStateOf(AiSettings.model(context)) }
    var endpoint by remember { mutableStateOf(AiSettings.endpoint(context)) }
    var apiKey by remember { mutableStateOf("") }
    var backendToken by remember { mutableStateOf("") }
    var catalogEnabled by remember { mutableStateOf(prefs.getBoolean("catalogEnabled", true)) }
    var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyricsEnabled", true)) }
    var configured by remember { mutableStateOf(ProviderRegistry(context).configured()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var successful by remember { mutableIntStateOf(0) }
    var lastSuccess by remember { mutableStateOf<Long?>(null) }
    var retention by remember { mutableIntStateOf(prefs.getInt("historyRetention", 365)) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { successful = intelligence.successfulAiRequests(); lastSuccess = intelligence.lastAiSuccess() } }
    val used = prefs.getInt("usageCount", 0)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V3Header(tr(fa, "AI & online services", "هوش مصنوعی و سرویس آنلاین"), tr(fa, "Hybrid is recommended • Offline remains fully functional", "Hybrid پیشنهاد می‌شود • آفلاین کامل باقی می‌ماند"), Icons.Default.Cloud) }
        item { V3Card {
            listOf(AiSettings.OFFLINE, AiSettings.HYBRID, AiSettings.ONLINE).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == item, onClick = { mode = item; prefs.edit().putString("aiMode", item).apply() })
                    Text(when (item) { AiSettings.OFFLINE -> tr(fa, "Offline only", "فقط آفلاین"); AiSettings.ONLINE -> tr(fa, "Online AI only", "فقط هوش مصنوعی آنلاین"); else -> tr(fa, "Hybrid (recommended)", "ترکیبی (پیشنهادی)") })
                }
            }
            AiSwitch(tr(fa, "Automatically analyze low-confidence songs online", "تحلیل خودکار آهنگ‌های کم‌اطمینان به‌صورت آنلاین"), automatic) { automatic = it; prefs.edit().putBoolean("autoOnline", it).apply() }
            AiSwitch(tr(fa, "Ask before uploading audio", "پرسیدن قبل از آپلود صدا"), ask) { ask = it; prefs.edit().putBoolean("askUpload", it).apply() }
            AiSwitch(tr(fa, "Wi-Fi only (mobile data off)", "فقط Wi-Fi (داده موبایل خاموش)"), wifiOnly) { wifiOnly = it; prefs.edit().putBoolean("wifiOnly", it).apply() }
            Text(tr(fa, "Daily online request limit: $daily", "سقف درخواست روزانه: $daily"))
            Slider(value = daily.toFloat(), onValueChange = { daily = it.toInt(); prefs.edit().putInt("dailyLimit", daily).apply() }, valueRange = 1f..100f)
            Text(tr(fa, "Maximum audio upload: $maxUpload MB", "حداکثر حجم آپلود صدا: $maxUpload مگابایت"))
            Slider(value = maxUpload.toFloat(), onValueChange = { maxUpload = it.toInt(); prefs.edit().putInt("maxUploadMb", maxUpload).apply() }, valueRange = 1f..15f)
        } }
        item { V3Card {
            Text(tr(fa, "Audio AI provider", "Provider تحلیل صوتی"), style = MaterialTheme.typography.titleMedium)
            Text(if (configured) tr(fa, "Connected", "متصل") else tr(fa, "Not configured — offline fallback active", "تنظیم نشده — Fallback آفلاین فعال است"))
            OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr(fa, "Model name", "نام مدل")) })
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(tr(fa, "Optional secure HTTPS backend", "Backend امن HTTPS (اختیاری)")) }, placeholder = { Text("https://…/generateContent") })
            OutlinedTextField(value = backendToken, onValueChange = { backendToken = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(tr(fa, "Backend token (not stored in source)", "توکن Backend (در سورس نیست)")) })
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(tr(fa, "Personal Gemini API key (advanced)", "کلید شخصی Gemini (پیشرفته)")) })
            Text(tr(fa, "For production/shared APKs use a secured backend. A personal key is encrypted with Android Keystore but Google recommends server-side credentials for client apps.", "برای APK عمومی از Backend امن استفاده کنید. کلید شخصی با Android Keystore رمز می‌شود، اما Google برای برنامه کلاینت اعتبار سروری را توصیه می‌کند."), style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                prefs.edit().putString("model", model.trim()).putString("secureEndpoint", endpoint.trim()).apply()
                if (apiKey.isNotBlank()) credentials.put("geminiKey", apiKey.trim())
                if (backendToken.isNotBlank()) credentials.put("backendToken", backendToken.trim())
                apiKey = ""; backendToken = ""; configured = ProviderRegistry(context).configured()
                status = tr(fa, "Provider settings saved", "تنظیمات Provider ذخیره شد")
            }, modifier = Modifier.fillMaxWidth()) { Text(tr(fa, "Save / connect", "ذخیره / اتصال")) }
            OutlinedButton(enabled = configured && !busy, onClick = {
                scope.launch {
                    busy = true; status = ""
                    OnlineGate(context).check()?.let { status = it; busy = false; return@launch }
                    try {
                        OnlineGate(context).record()
                        val result = withContext(Dispatchers.IO) { ProviderRegistry(context).audio.testConnection() }
                        status = tr(fa, "Connection successful", "اتصال موفق") + ": " + result.take(100)
                        intelligence.log(AiProcessingLog(provider = "Gemini", model = model, processingMode = mode, operation = "test", status = "success", createdAt = System.currentTimeMillis()))
                    } catch (e: Exception) {
                        status = e.message ?: "Connection failed"
                        intelligence.log(AiProcessingLog(provider = "Gemini", model = model, processingMode = mode, operation = "test", status = "error", createdAt = System.currentTimeMillis(), details = status.take(300)))
                    }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(tr(fa, "Test connection", "آزمون اتصال")) }
            if (busy) LoadingLine()
            if (status.isNotBlank()) Text(status)
            TextButton(onClick = { confirmDisconnect = true }) { Text(tr(fa, "Disconnect providers and delete credentials", "قطع Provider و حذف اعتبارنامه‌ها")) }
        } }
        item { V3Card {
            Text(tr(fa, "Other online providers", "سایر Providerهای آنلاین"), style = MaterialTheme.typography.titleMedium)
            AiSwitch(tr(fa, "Apple iTunes catalog (no API key)", "کاتالوگ Apple iTunes (بدون کلید)"), catalogEnabled) { catalogEnabled = it; prefs.edit().putBoolean("catalogEnabled", it).apply() }
            AiSwitch(tr(fa, "LRCLIB authorized lyrics (no API key)", "متن آهنگ LRCLIB (بدون کلید)"), lyricsEnabled) { lyricsEnabled = it; prefs.edit().putBoolean("lyricsEnabled", it).apply() }
        } }
        item { V3Card {
            Text(tr(fa, "Usage and cache", "مصرف و کش"), style = MaterialTheme.typography.titleMedium)
            Text(tr(fa, "Requests today: $used • Successful operations: $successful", "درخواست امروز: $used • عملیات موفق: $successful"))
            Text(tr(fa, "Last successful sync: ${lastSuccess?.let { Date(it).toString() } ?: "—"}", "آخرین همگام‌سازی موفق: ${lastSuccess?.let { Date(it).toString() } ?: "—"}"))
            OutlinedButton(onClick = {
                scope.launch(Dispatchers.IO) { intelligence.clearAllRecommendations(); songs.clearOnlineAnalysis() }
                status = tr(fa, "AI result/recommendation cache cleared", "کش نتایج و پیشنهادهای AI پاک شد")
            }, modifier = Modifier.fillMaxWidth()) { Text(tr(fa, "Clear AI cache", "پاک‌کردن کش AI")) }
        } }
        item { V3Card {
            Text(tr(fa, "Privacy and data usage", "حریم خصوصی و مصرف داده"), style = MaterialTheme.typography.titleMedium)
            Text(tr(fa, "Audio is sent only after consent and provider configuration. Google free-tier inputs may be used to improve products; paid/free quotas and regional availability can change. iTunes receives catalog queries. LRCLIB receives title/artist lyrics queries.", "صدا فقط با رضایت و پیکربندی Provider ارسال می‌شود. ورودی Free Tier گوگل ممکن است برای بهبود محصول استفاده شود؛ سهمیه و دسترسی منطقه‌ای ممکن است تغییر کند. iTunes عبارت جستجو و LRCLIB عنوان/هنرمند را دریافت می‌کند."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { retention = when (retention) { 30 -> 90; 90 -> 365; 365 -> 0; else -> 30 }; prefs.edit().putInt("historyRetention", retention).apply() }) {
                Text(tr(fa, "History retention: ${if (retention == 0) "forever" else "$retention days"}", "نگهداری تاریخچه: ${if (retention == 0) "همیشه" else "$retention روز"}"))
            }
            TextButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/docs/pricing"))) } catch (_: Exception) {} }) { Text(tr(fa, "Official provider pricing", "قیمت رسمی Provider")) }
            TextButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/docs/api-key"))) } catch (_: Exception) {} }) { Text(tr(fa, "Official API-key security guidance", "راهنمای رسمی امنیت کلید")) }
        } }
    }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false }, title = { Text(tr(fa, "Disconnect?", "اتصال قطع شود؟")) },
        confirmButton = { TextButton(onClick = {
            credentials.remove("geminiKey"); credentials.remove("backendToken")
            prefs.edit().remove("secureEndpoint").apply(); endpoint = ""; configured = false; confirmDisconnect = false
        }) { Text(tr(fa, "Disconnect", "قطع اتصال")) } }, dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(tr(fa, "Cancel", "لغو")) } })
}
@Composable private fun AiSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange) }
}
