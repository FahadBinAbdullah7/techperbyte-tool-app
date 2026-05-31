package com.techperbyte.tools.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ── Models ────────────────────────────────────────────────────────────────────

private data class CitySuggestion(
    val name: String,
    val country: String,
    val region: String?,
    val lat: Double,
    val lon: Double,
) {
    val display get() = buildString {
        append(name)
        if (!region.isNullOrBlank()) append(", $region")
        append(", $country")
    }
}

private data class DayForecast(
    val date: String,       // "2024-07-01"
    val maxC: Double,
    val minC: Double,
    val weatherCode: Int,
)

private data class WeatherData(
    val locationLabel: String,
    val tempC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windKmph: Double,
    val uvIndex: Double,
    val weatherCode: Int,
    val dailyForecast: List<DayForecast>,
)

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun weatherDescription(code: Int): String = when (code) {
    0           -> "Clear sky"
    1           -> "Mainly clear"
    2           -> "Partly cloudy"
    3           -> "Overcast"
    in 45..48   -> "Foggy"
    in 51..55   -> "Drizzle"
    in 61..65   -> "Rain"
    in 71..75   -> "Snow"
    in 80..82   -> "Rain showers"
    in 95..99   -> "Thunderstorm"
    else        -> "—"
}

private fun weatherEmoji(code: Int): String = when (code) {
    0           -> "☀️"
    1           -> "🌤️"
    2           -> "⛅"
    3           -> "☁️"
    in 45..48   -> "🌫️"
    in 51..55   -> "🌦️"
    in 61..65   -> "🌧️"
    in 71..75   -> "🌨️"
    in 80..82   -> "🌦️"
    in 95..99   -> "⛈️"
    else        -> "🌡️"
}

private fun dayLabel(dateStr: String, index: Int): String {
    if (index == 0) return "Today"
    if (index == 1) return "Tomorrow"
    return try {
        LocalDate.parse(dateStr).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    } catch (_: Exception) { dateStr }
}

// ── API ───────────────────────────────────────────────────────────────────────

private fun fetchSuggestions(query: String): List<CitySuggestion> {
    val enc  = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    val json = URL("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=6&language=en&format=json").readText()
    val obj  = JSONObject(json)
    if (!obj.has("results")) return emptyList()
    val arr  = obj.getJSONArray("results")
    return (0 until arr.length()).map { i ->
        val r = arr.getJSONObject(i)
        CitySuggestion(
            name    = r.optString("name"),
            country = r.optString("country"),
            region  = r.optString("admin1").ifBlank { null },
            lat     = r.getDouble("latitude"),
            lon     = r.getDouble("longitude"),
        )
    }
}

private fun fetchWeather(city: CitySuggestion): WeatherData {
    val url = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=${city.lat}&longitude=${city.lon}" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,uv_index,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
        "&timezone=auto&forecast_days=7"
    val obj   = JSONObject(URL(url).readText())
    val curr  = obj.getJSONObject("current")
    val daily = obj.getJSONObject("daily")

    val times    = daily.getJSONArray("time")
    val maxTemps = daily.getJSONArray("temperature_2m_max")
    val minTemps = daily.getJSONArray("temperature_2m_min")
    val codes    = daily.getJSONArray("weather_code")

    val forecast = (0 until times.length()).map { i ->
        DayForecast(
            date        = times.getString(i),
            maxC        = maxTemps.getDouble(i),
            minC        = minTemps.getDouble(i),
            weatherCode = codes.getInt(i),
        )
    }

    return WeatherData(
        locationLabel = city.display,
        tempC         = curr.getDouble("temperature_2m"),
        feelsLikeC    = curr.getDouble("apparent_temperature"),
        humidity      = curr.getInt("relative_humidity_2m"),
        windKmph      = curr.getDouble("wind_speed_10m"),
        uvIndex       = curr.getDouble("uv_index"),
        weatherCode   = curr.getInt("weather_code"),
        dailyForecast = forecast,
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun WeatherForecastScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var query       by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<CitySuggestion>>(emptyList()) }
    var searching   by remember { mutableStateOf(false) }
    var weather     by remember { mutableStateOf<WeatherData?>(null) }
    var loading     by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf("") }
    var showNoNet   by remember { mutableStateOf(false) }
    var searchJob   by remember { mutableStateOf<Job?>(null) }

    fun onQueryChange(value: String) {
        query = value; weather = null; error = ""; suggestions = emptyList()
        searchJob?.cancel()
        if (value.length < 2) { searching = false; return }
        searchJob = scope.launch {
            delay(350)
            if (!isNetworkAvailable(context)) return@launch
            searching = true
            try {
                val r = withContext(Dispatchers.IO) { fetchSuggestions(value) }
                suggestions = r
            } catch (_: Exception) {}
            searching = false
        }
    }

    fun selectCity(city: CitySuggestion) {
        query = city.name; suggestions = emptyList()
        if (!isNetworkAvailable(context)) { showNoNet = true; return }
        loading = true; error = ""
        scope.launch(Dispatchers.IO) {
            try {
                val data = fetchWeather(city)
                withContext(Dispatchers.Main) { weather = data; loading = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Could not load weather. Try again."; loading = false }
            }
        }
    }

    if (showNoNet) NoNetworkDialog { showNoNet = false }

    ToolScaffold(
        title = "Weather Forecast",
        navController = navController,
        subtitle = "Type a city name to get current weather and 7-day forecast.",
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
                "Type a city name in the search box — suggestions appear as you type.",
                "Tap a suggestion to load current weather and the 7-day forecast.",
                "Scroll down to see humidity, wind speed, UV index, and daily highs/lows.",
                "Requires an active internet connection (uses Open-Meteo free API).",
            ))
            // ── Search ────────────────────────────────────────────────────────
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search city…", color = TextSecondary) },
                    singleLine = true,
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Accent, focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                    ),
                )
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Border),
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 0.dp, topEnd = 0.dp),
                        elevation = CardDefaults.cardElevation(4.dp),
                    ) {
                        suggestions.forEachIndexed { idx, city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectCity(city) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("📍", fontSize = 16.sp)
                                Column {
                                    Text(city.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        buildString {
                                            if (!city.region.isNullOrBlank()) append("${city.region}, ")
                                            append(city.country)
                                        },
                                        color = TextSecondary, fontSize = 12.sp,
                                    )
                                }
                            }
                            if (idx < suggestions.lastIndex) HorizontalDivider(color = Border.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = SurfaceVariant)

            if (error.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Danger), shape = RoundedCornerShape(10.dp)) {
                    Text(error, color = Danger, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            weather?.let { w ->
                // ── Current conditions ────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Border),
                    shape  = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(w.locationLabel, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${w.tempC.toInt()}°C", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                Text(
                                    "${weatherEmoji(w.weatherCode)}  ${weatherDescription(w.weatherCode)}",
                                    color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                )
                                Text("Feels like ${w.feelsLikeC.toInt()}°C", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(color = Border)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeatherStat("Humidity",  "${w.humidity}%",            Modifier.weight(1f))
                            WeatherStat("Wind",      "${w.windKmph.toInt()} km/h",Modifier.weight(1f))
                            WeatherStat("UV Index",  "${w.uvIndex.toInt()}",       Modifier.weight(1f))
                        }
                    }
                }

                // ── 7-day forecast ────────────────────────────────────────────
                if (w.dailyForecast.isNotEmpty()) {
                    Text(
                        "7-Day Forecast",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Border),
                        shape  = RoundedCornerShape(12.dp),
                    ) {
                        w.dailyForecast.forEachIndexed { idx, day ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Day name
                                Text(
                                    dayLabel(day.date, idx),
                                    fontSize = 14.sp,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (idx == 0) TextPrimary else TextMuted,
                                    modifier = Modifier.width(90.dp),
                                )
                                // Weather emoji
                                Text(
                                    weatherEmoji(day.weatherCode),
                                    fontSize = 20.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                // Condition label
                                Text(
                                    weatherDescription(day.weatherCode),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(2f),
                                )
                                // High / Low
                                Text(
                                    "${day.maxC.toInt()}°",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.width(36.dp),
                                )
                                Text(
                                    "${day.minC.toInt()}°",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.width(36.dp),
                                )
                            }
                            if (idx < w.dailyForecast.lastIndex) HorizontalDivider(color = Border.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                Text("Data: Open-Meteo.com (free, no API key)", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
