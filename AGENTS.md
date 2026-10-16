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

The REST client (`data/remote`) is plain HTTPS + `org.json` — no HTTP dependency. What it guarantees:

- **Paged reads.** Reads use `Range` headers (500/page). PostgREST caps responses at `max_rows` — unpaged reads silently truncate.
- **Retries with backoff** for transient failures only (offline, 5xx, 429). 4xx/malformed are never retried.
- **Timeouts and gzip** on every connection, and one `SupabaseException` type whose message is already student-readable.
- **HTTPS only**, enforced by `network_security_config.xml`.
- **Uploaded files are checked.** Downloads must start with `%PDF-` (spec allows up to 1024 leading bytes); otherwise deleted and reported.
- **Request/feedback text is length-capped**; email validated before sending.

### Security findings & remediations (from full audit)

| # | Severity | Finding | Fix |
|---|----------|---------|-----|
| C1 | Critical | Supabase anon key hardcoded in `SupabaseConfig.kt` | Moved to `android/local.properties` (`supabase.anon.key=...`), gitignored. Compiled-in fallback remains for fresh clones. |
| C2 | Critical | Release keystore passwords in `keystore/release-keystore.pw` | Already gitignored; never commit. Back up offline. |
| H1 | High | Admin session in `localStorage` (portal) | Migrated to `sessionStorage` in `admin-portal/js/db.js`. |
| H2 | High | Hardcoded admin email seeded in `supabase-setup.sql` | Removed seed; provision initial admin out-of-band (documented in SQL). |
| H3/H4 | High | `content_requests`/`feedback` RLS blocked all access | Added `admin read requests` / `admin read feedback` SELECT policies for authenticated admins. |
| M1 | Medium | Supabase anon key sent to GitHub API | Removed — GitHub public releases endpoint needs no auth. |

### Known backend issues (Supabase dashboard/functions, not this repo)

1. **`content-requests` / `feedback` Edge Functions trust client-generated identity keys.** Anyone with another user's `requesterKey`/`feedbackKey` can read/delete their requests. Fix: require Supabase auth session (`auth.uid()`), rate-limit endpoints.
2. **JWT verification off on functions.** Both functions answer without an `apikey` header. Turn verification back on once functions validate input themselves.
3. **No login rate-limiting on the admin portal.** Supabase Auth rate-limits by IP, but the portal doesn't track failed attempts client-side. Consider adding a lockout counter.

### Admin portal (`admin-portal/`)

Static admin UI → Cloudflare Worker `paperadda-admin`. Opens at `https://paperadda-admin.paperadda.workers.dev`. Sign in with admin credentials; manages subjects, papers, notes, student requests, feedback, team members, backups.

- `admin-portal/js/config.js` and `public/js/config.js` are **gitignored** — copy `config.example.js` and add your Supabase URL + anon key.
- `supabase-setup.sql` — run this in your Supabase SQL editor to create tables, RLS policies, and the storage bucket.
- All uploads are validated as real PDFs (magic bytes `%PDF-`), not just extension.
- Account changes require re-authenticating with the current password.


### Release

- Update channel: GitHub Releases (`psb-001/paperadda`). A release body may carry `<!-- min_supported: 1.5 -->` to force the update.
- Bump `versionCode` (+1) and `versionName` in `android/app/build.gradle.kts` for every upload.
- Verify: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
