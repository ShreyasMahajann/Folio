# Folio

A local PDF reader for Android with a reading experience modelled on e-book readers: the page fills the
screen, controls stay hidden until you tap, and the app reopens every document where you stopped.

No store, no account, no network permission, no ads, no analytics.

## Features

| Area | What works in v1 |
|---|---|
| Import | Android file picker, "Open with", Share Sheet |
| Library | Cover grid, progress, last-opened order, rename, remove |
| Reader | Full screen, tap centre to show or hide controls, tap edges to turn the page |
| Reading modes | Page turn (default) and vertical scroll |
| Page fit | Whole page or full width |
| Zoom | Pinch and double tap, up to 4x |
| Navigation | Page scrubber, page number, percent read |
| Position | Saved on every page turn. Survives app restart and device restart |
| Bookmarks | Toggle per page, bookmark list with jump |
| Themes | Light, sepia, dark |
| Comfort | Brightness (reader only), keep screen awake, orientation lock |

Not in v1: highlights, annotations, text search, table of contents, thumbnails, reading statistics,
password-protected PDFs.

## Build and run

Requirements: Android Studio 2025.x or newer, Android SDK platform 36, a phone with Android 12 or newer.

### Android Studio

1. **File > Open**, select this folder.
2. Wait for Gradle sync.
3. On the phone: **Settings > Developer options > USB debugging**, then connect the USB cable.
4. Press **Run**.

### Command line (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug          # APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat connectedDebugAndroidTest   # database tests, needs a connected phone
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`local.properties` holds the SDK path (`sdk.dir`). Android Studio writes it on first open.

You can also copy `app-debug.apk` to the phone and open it there. Android asks for permission to install
from that source.

## Architecture

Single activity, Jetpack Compose, MVVM. ViewModels expose `StateFlow`. No dependency injection framework:
`AppContainer` builds the three shared objects once.

```
app/src/main/java/com/shreyas/pdfreader/
  ReaderApp.kt            Application and AppContainer
  MainActivity.kt         Hosts Compose, receives "Open with" and Share intents
  navigation/AppNav.kt    library -> reader/{documentId}
  data/
    db/                   Room: documents, bookmarks
    LibraryRepository.kt  Import, remove, rename, progress, bookmarks, covers
    SettingsStore.kt      DataStore: reader settings
  pdf/
    PdfDocumentRenderer.kt  Wraps android.graphics.pdf.PdfRenderer
    PageBitmapCache.kt      LRU cache bounded by bytes
  ui/
    library/              Library screen, card, ViewModel
    reader/               Reader screen, pages, zoom, bars, sheets, ViewModel
    components/           Icons drawn for this app
    theme/                App theme, reader theme, page filters
  util/Progress.kt
```

### Decisions

- **Kotlin and Compose, not Flutter.** Rendering, storage access, brightness and full-screen mode are
  platform APIs. Flutter needs a plugin for each.
- **Framework `PdfRenderer`.** No native library in the APK, no licence limits, reads `content://` files
  without a copy. The alternatives were rejected: `androidx.pdf` is beta and has no page-turn mode,
  AndroidPdfViewer is abandoned, MuPDF is AGPL and large, pdf.js in a WebView is slow.
- **Files are not copied** when they come from the file picker. The app keeps a persistent read grant.
  Files from "Open with" and the Share Sheet are copied into app storage, because Android ends that
  grant when the activity closes.
- **Progress is stored on the document row**, written on every page turn.

### Memory

- Only the visible page and its neighbours are rendered.
- All PDF access runs on one thread. `PdfRenderer` allows one open page at a time.
- A rendered page is capped at 8 million pixels (page mode) or 4 million pixels (scroll mode).
- The cache holds a quarter of the app memory class, between 32 MB and 128 MB.

## Known limitations

- **Dark and sepia are filters, not recolouring.** A PDF stores fixed colours. Dark mode inverts the whole
  page, so photos and diagrams are inverted too. Sepia tints the page.
- **Very high zoom is slightly soft.** A zoomed page is rendered again as one bitmap up to the pixel cap.
  On a 1080 px wide phone that is sharp to about 2.2x in page mode and 1.5x in scroll mode.
- **A tap waits about 0.3 s** before the controls appear. Android needs that time to rule out a double tap.
- **Password-protected PDFs** do not open.
- **A moved or deleted file** shows "File not available" in the library. Remove it and import it again.
- **Pages of mixed sizes** shift a little in scroll mode the first time they appear.
- No text layer: no selection, search, highlights, or links.

## Future improvements

1. Text search, selection and highlights with the Android 15 `PdfRenderer` text APIs
   (`PdfRendererPreV` on Android 12 to 14).
2. Table of contents with `io.legere:pdfiumandroid` (Apache-2.0).
3. Tile rendering for sharp zoom at any level.
4. Thumbnail strip in the scrubber.
5. Password prompt for protected PDFs.
6. Release build with R8. The debug APK is about 32 MB, a release build is far smaller.

## Test status

- Unit tests: progress and zoom maths.
- Instrumented tests: database queries. They need a phone or emulator.
