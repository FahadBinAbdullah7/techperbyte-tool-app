package com.techperbyte.tools

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.techperbyte.tools.ui.screens.EmailSignatureScreen
import com.techperbyte.tools.ui.screens.HomeScreen
import com.techperbyte.tools.ui.screens.HtmlConverterScreen
import com.techperbyte.tools.ui.screens.ImageCompressorScreen
import com.techperbyte.tools.ui.screens.ImageEnhancerScreen
import com.techperbyte.tools.ui.screens.ImageResizerScreen
import com.techperbyte.tools.ui.screens.ImageToTextScreen
import com.techperbyte.tools.ui.screens.JsonToTableScreen
import com.techperbyte.tools.ui.screens.PdfCompressorScreen
import com.techperbyte.tools.ui.screens.PdfFlipbookScreen
import com.techperbyte.tools.ui.screens.PdfMergerScreen
import com.techperbyte.tools.ui.screens.PdfOcrScreen
import com.techperbyte.tools.ui.screens.QrCodeScreen
import com.techperbyte.tools.ui.screens.WeatherForecastScreen
import com.techperbyte.tools.ui.screens.YouTubeThumbnailScreen

sealed class Screen(val route: String) {
    object Home             : Screen("home")
    object QrCode           : Screen("qr_code")
    object ImageCompress    : Screen("image_compressor")
    object ImageResize      : Screen("image_resizer")
    object ImageEnhancer    : Screen("image_enhancer")
    object PdfMerger        : Screen("pdf_merger")
    object PdfCompressor    : Screen("pdf_compressor")
    object PdfFlipbook      : Screen("pdf_flipbook")
    object ImageToText      : Screen("image_to_text")
    object PdfOcr           : Screen("pdf_ocr")
    object HtmlConverter    : Screen("html_converter")
    object EmailSig         : Screen("email_signature")
    object JsonToTable      : Screen("json_to_table")
    object WeatherForecast  : Screen("weather_forecast")
    object YouTubeThumbnail : Screen("youtube_thumbnail")
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Screen.Home.route) {
        composable(Screen.Home.route)             { HomeScreen(nav) }
        composable(Screen.QrCode.route)           { QrCodeScreen(nav) }
        composable(Screen.ImageCompress.route)    { ImageCompressorScreen(nav) }
        composable(Screen.ImageResize.route)      { ImageResizerScreen(nav) }
        composable(Screen.ImageEnhancer.route)    { ImageEnhancerScreen(nav) }
        composable(Screen.PdfMerger.route)        { PdfMergerScreen(nav) }
        composable(Screen.PdfCompressor.route)    { PdfCompressorScreen(nav) }
        composable(Screen.PdfFlipbook.route)      { PdfFlipbookScreen(nav) }
        composable(Screen.ImageToText.route)      { ImageToTextScreen(nav) }
        composable(Screen.PdfOcr.route)           { PdfOcrScreen(nav) }
        composable(Screen.HtmlConverter.route)    { HtmlConverterScreen(nav) }
        composable(Screen.EmailSig.route)         { EmailSignatureScreen(nav) }
        composable(Screen.JsonToTable.route)      { JsonToTableScreen(nav) }
        composable(Screen.WeatherForecast.route)  { WeatherForecastScreen(nav) }
        composable(Screen.YouTubeThumbnail.route) { YouTubeThumbnailScreen(nav) }
    }
}
