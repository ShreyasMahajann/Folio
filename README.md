# Folio

A local PDF reader for Android with a reading experience modelled on e-book readers: the page fills the
screen, controls stay hidden until you tap, and the app reopens every document where you stopped.

No store, no account, no ads, no analytics. Reading works offline. The network is used only by the
optional web search.

## Screenshots

Taken on a Samsung Galaxy A35 (Android 16) with the release build.

| Library | Reading | Controls |
|---|---|---|
| ![Library](docs/screenshots/library.png) | ![Reading](docs/screenshots/reading.png) | ![Controls, dark theme](docs/screenshots/controls-dark.png) |

| Light | Sepia | Settings |
|---|---|---|
| ![Light theme](docs/screenshots/controls-light.png) | ![Sepia theme](docs/screenshots/controls-sepia.png) | ![Settings](docs/screenshots/settings.png) |

| Web search | Preview before adding |
|---|---|
| ![Web search](docs/screenshots/search.png) | ![Preview](docs/screenshots/preview.png) |

## Features

| Area | What works in v1 |
|---|---|
| Import | Android file picker, "Open with", Share Sheet |
| Web search | Searches the web for `filetype:pdf <name>` with Google or DuckDuckGo. A tapped PDF opens as a preview. It enters the library only after "Add to library" |
| Library | Cover grid, progress, last-opened order, rename, remove |
| Reader | Full screen, tap centre to show or hide controls, tap edges to turn the page |
| Reading modes | Page turn (default) and vertical scroll |
| Page fit | Whole page or full width |
| Zoom | Pinch and double tap, up to 4x |
| Navigation | Page scrubber, page number, percent read |
| Position | Saved on every page turn. Survives app restart and device restart |
| Bookmarks | Toggle per page, bookmark list with jump |
| Themes | Light, sepia, dark. One-tap switch in the top bar. Starts dark when the phone is in dark mode |
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

## Releases

`.github/workflows/ci.yml` runs unit tests, lint and a debug build on every push and pull request.
A tag that starts with `v` also builds the signed, minified APK and attaches it to a GitHub release.

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow needs four repository secrets
(**Settings > Secrets and variables > Actions > New repository secret**):

| Secret | Value |
|---|---|
| `FOLIO_KEYSTORE_BASE64` | The release keystore, base64 encoded |
| `FOLIO_KEYSTORE_PASSWORD` | Keystore password |
| `FOLIO_KEY_ALIAS` | Key alias |
| `FOLIO_KEY_PASSWORD` | Key password |

Keep the keystore safe. Android installs an update only when it is signed with the same key.

To build a signed release on your own PC, set `FOLIO_KEYSTORE_FILE`, `FOLIO_KEYSTORE_PASSWORD`,
`FOLIO_KEY_ALIAS` and `FOLIO_KEY_PASSWORD`, then run `gradlew assembleRelease`.

## Architecture

Single activity, Jetpack Compose, MVVM. ViewModels expose `StateFlow`. No dependency injection framework:
`AppContainer` builds the three shared objects once.

```
app/src/main/java/com/shreyas/pdfreader/
  ReaderApp.kt            Application and AppContainer
  MainActivity.kt         Hosts Compose, receives "Open with" and Share intents
  navigation/AppNav.kt    library -> reader/{documentId}, library -> search
  data/
    db/                   Room: documents, bookmarks
    LibraryRepository.kt  Import, remove, rename, progress, bookmarks, covers
    SettingsStore.kt      DataStore: reader settings
    PdfDownloader.kt      Downloads a PDF from the web into the cache
  pdf/
    PdfDocumentRenderer.kt  Wraps android.graphics.pdf.PdfRenderer
    PageBitmapCache.kt      LRU cache bounded by bytes
  ui/
    library/              Library screen, card, ViewModel
    reader/               Reader screen, pages, zoom, bars, sheets, ViewModel
    search/               Web search, PDF preview, ViewModel
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
- **Web search shows the search engine in a WebView.** The app does not read or scrape the results.
  It only catches a tap on a PDF link. The page gets no access to files or to app code.
  A downloaded file stays in the cache until the user adds it. A discarded file is deleted.

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
- No text layer: no selection, search inside a PDF, highlights, or links.
- **Google can ask for a CAPTCHA** in the web search. Solve it once, or switch to DuckDuckGo.
- **Web search finds only PDFs that are public on the web.** Sites that need a login do not work.
  You are responsible for the right to download a file.

## Future improvements

1. Text search, selection and highlights with the Android 15 `PdfRenderer` text APIs
   (`PdfRendererPreV` on Android 12 to 14).
2. Table of contents with `io.legere:pdfiumandroid` (Apache-2.0).
3. Tile rendering for sharp zoom at any level.
4. Thumbnail strip in the scrubber.
5. Password prompt for protected PDFs.

## Test status

- Unit tests: progress, zoom maths, download file names.
- Instrumented tests: database queries. They need a phone or emulator.
- Manual test on a Samsung Galaxy A35, Android 16, release build: import, 600-page and 914-page PDFs,
  page turn, scroll mode, zoom, scrubber, bookmarks, themes, position after force stop, web search,
  preview, add and discard.

APK size: about 3 MB for the release build, about 32 MB for the debug build.
