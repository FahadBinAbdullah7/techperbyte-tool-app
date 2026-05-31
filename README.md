# TechPerByte Tools — Native Android App

All 13 offline tools from techperbyte.com in a single native Android app. No internet required.

## Tools included

| Tool | Technology |
|---|---|
| QR Code Generator | ZXing |
| Image Compressor | Android Bitmap API |
| Image Resizer | Android Bitmap API |
| Image Enhancer | ColorMatrix + custom sharpening |
| PDF Merger | PdfBox Android |
| PDF Compressor | PdfBox Android (re-render at lower DPI/quality) |
| PDF Flipbook | Android PdfRenderer |
| Image → Text (OCR) | Google ML Kit (bundled, offline) |
| PDF OCR | ML Kit + PdfRenderer |
| HTML Converter | Jsoup (prettify / minify / extract text) |
| Email Signature Generator | WebView preview + HTML export |
| JSON → Table | Built-in org.json |
| Q&A → PPTX | Custom ZIP/XML PPTX generator |

Tools requiring internet (Weather, Tech Headlines, YouTube Thumbnail, Nearby Places) show an informational screen.

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 34
- JDK 17 (bundled with Android Studio)
- Minimum device: Android 8.0 (API 26)

## Build steps

1. Open Android Studio → **File → Open** → select this folder (`techperbyte-android/`)
2. Wait for Gradle sync to complete (first sync downloads ~200 MB of dependencies)
3. Connect a device or start an emulator
4. Click **Run ▶** (or `Shift+F10`)

### Command-line build

```bash
cd techperbyte-android
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

If `gradlew` is missing, generate it once with:
```bash
gradle wrapper --gradle-version 8.7
```

## Permissions

- `INTERNET` — declared but only used if you later add the internet-based tools
- No storage permissions needed — all file I/O goes through Android's SAF (system file picker)

## Output location

Saved images → `Pictures/TechPerByteTools/`  
Saved PDFs / text / PPTX → `Downloads/TechPerByteTools/`
