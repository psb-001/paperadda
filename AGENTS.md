# PaperAdda

Offline-first Android app for previous-year question papers & study notes, plus a Cloudflare-hosted admin portal backed by Supabase.

## Repo layout

| Path | What it is |
|------|------------|
| `android/` | Kotlin + Jetpack Compose app (`com.paperadda.app`) |
| `admin-portal/` | Static admin UI → Cloudflare Worker `paperadda-admin` |

## Android

Open `android/` in Android Studio, then Gradle sync.

```bash
cd android
./gradlew testDebugUnitTest   # 158 Robolectric unit tests, must be 0 failures
./gradlew assembleRelease     # signed sideload APK (keystore is local-only, never committed)
```

- ApplicationId: `com.paperadda.app`; Kotlin namespace: `com.paperadda.app`.
- `applicationId` never changes between releases — user data is keyed off it.
- `keystore/`, `local.properties`, `.wrangler/`, `*.env*` are gitignored and never committed.

### Architecture

| Package | Responsibility |
|---------|----------------|
| `data.local` | Room DB (v8), entities, DAOs, `Migrations.kt` (hand-written migrations only) |
| `data.remote` | Supabase REST/Storage client: `HttpSupport.kt` (paging, retries, gzip, timeouts), `SupabaseRemoteDataSource.kt` |
| `data.repository` | `PaperRepository`, `NoteRepository`, `ContentRequestRepository`, `FeedbackRepository`, `FileExporter`, `UpdateInstaller`, `UpdateRepository`, `DownloadedPaperReconciler` |
| `data.update` | `UpdateManager.kt` — GitHub Release update flow, extracted from the ViewModel |
| `data.model` | `AppRelease`, `QuestionPaper`, `Subject`, `Branch`, version comparison |
| `ui` | `MainViewModel` (navigation + screen state), Compose screens |
| `ui.pdf` | PDFBox-Android reader engine (`PdfDocument`, `PdfReaderScreen`, `PdfBitmapCache`) |

### Room migration policy

- `exportSchema = true` + `room.schemaLocation` → `android/app/schemas/` is written every build. **Commit those JSON files.**
- Any schema change bumps the version and ships a real `Migration` in `Migrations.kt`. `RoomMigrationTest` builds an old-version database, migrates it, and asserts no row is lost.
- `fallbackToDestructiveMigration()` only covers pre-v7 databases (no exported schema exists for them). It is survivable: `DownloadedPaperReconciler` rebuilds the download index from PDFs still on disk, and the catalog re-syncs on next launch.

### Backend connectivity (Supabase)

- Plain HTTPS + `org.json`, no HTTP dependency.
- Reads are paged with `Range` headers (500/page). PostgREST caps responses at `max_rows` — unpaged reads silently truncate.
- Retries with backoff for transient failures only (offline, 5xx, 429). 4xx/malformed are never retried.
- `network_security_config.xml` enforces HTTPS only.
- Downloads must start with `%PDF-`; otherwise deleted and reported.
- Request/feedback text is length-capped; email validated before sending.

### Known backend issues (Supabase dashboard/functions, not this repo)

1. `content-requests` / `feedback` Edge Functions trust a client-generated `requesterKey`/`feedbackKey` as caller identity. Anyone with another user's key can read/delete their requests. Fix: require a Supabase auth session (`auth.uid()`), rate-limit the endpoints.
2. Both functions answer without an `apikey` header (JWT verification off). Turn verification back on once the functions validate input themselves.

### Release

- Update channel: GitHub Releases (`psb-001/paperadda`). A release body may carry `<!-- min_supported: 1.5 -->` to force the update.
- Bump `versionCode` (+1) and `versionName` in `android/app/build.gradle.kts` for every upload.
- Verify: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
