# PaperAdda

Offline-first Android app for previous-year question papers & study notes, plus a Cloudflare-hosted admin portal backed by Supabase.

## Layout

| Path | What it is |
|------|------------|
| `android/` | Kotlin + Jetpack Compose app (`com.paperadda.app`) |
| `admin-portal/` | Static admin UI → Cloudflare Worker `paperadda-admin` at `https://paperadda-admin.paperadda.workers.dev` |

## Secrets policy

**Never commit:**

- `keystore/`, `*.jks`, `*.pw` (release signing)
- `local.properties` (machine SDK path)
- `.wrangler/` (Cloudflare account cache)
- `.env*`
- `admin-portal/js/config.js` and `admin-portal/public/js/config.js` (Supabase project URL + anon key for the portal)

**Safe / public by design:**

- Supabase **anon** key in the Android client (`SupabaseConfig.ANON_KEY`) — read-only under RLS; writes require an authenticated admin session.
- No `service_role` key exists in this repo.

### Portal config (local only)

```bash
cd admin-portal
cp js/config.example.js js/config.js
cp js/config.example.js public/js/config.js
# edit both config.js files with your Supabase URL + anon key
```

### Deploy portal

```bash
cd admin-portal
# keep public/ in sync with source, then:
npx wrangler deploy
```

### Android

```bash
cd android
./gradlew testDebugUnitTest   # 117 Robolectric + Roborazzi unit tests
./gradlew assembleRelease     # signed sideload APK
```

Open `android/` in Android Studio after a Gradle sync. `applicationId` is
`com.paperadda.app`; the Kotlin namespace is `com.paperadda.app` too, so stack
traces point at real classes.

### Android architecture

| Package | What it holds |
|---------|---------------|
| `data.local` | Room database, entities, DAOs, migrations |
| `data.remote` | Supabase REST + Storage client (plain HTTPS, no extra dependencies) |
| `data.repository` | Catalog + notes caching, downloads, file export, in-app updates |
| `ui` | `MainViewModel` (navigation + screen state), Compose screens, PDF reader |
| `ui.pdf` | PDFBox-Android based reader engine |

**Catalog sync** is offline-first: Room cache renders first, then
`refreshFromRemote()` replaces the cache and reports failures in the UI (sync
errors are visible, never silent). Downloads live in
`Android/data/com.paperadda.app/files/Download`; `FileExporter` copies them into
the public `Downloads/PaperAdda` folder.

**In-app updates** come from GitHub Releases (`psb-001/paperadda`), because a
sideloaded APK gets no store update path. A release body may carry
`<!-- min_supported: 1.5 -->` to force the update.

### Room migration policy

- `exportSchema = true` and `room.schemaLocation` are on, so every build writes
  `android/app/schemas/`. **Commit those JSON files** — they are the input for
  auto-migrations and `MigrationTestHelper`.
- Any schema change bumps the version and ships a real `Migration` in
  `Migrations.kt` (see `RoomMigrationTest`, which builds a v7 database, migrates
  it, and asserts no row is lost).
- `fallbackToDestructiveMigration()` stays as a last resort for pre-v7 databases
  only (those were built with `exportSchema = false`, so no schema exists to
  migrate from). It is survivable: `DownloadedPaperReconciler` rebuilds the
  download index from the PDFs still on disk, and the catalog re-syncs on next
  launch.

### Backend connectivity (Supabase)

The REST client (`data/remote`) is plain HTTPS + `org.json` — no HTTP
dependency. What it guarantees:

- **Paged reads.** Reads use `Range` headers in pages of 500. PostgREST caps a
  response at the project's `max_rows` (1000 by default) and an unpaged
  `select=*` silently returns only the first page — the app would have shown a
  complete-looking catalog quietly missing every paper past the cap.
- **Retries with backoff** for transient failures only (offline, 5xx, 429). A
  401/403 or a malformed row is deterministic, so it is never retried.
- **Timeouts and gzip** on every connection, and one `SupabaseException` type
  whose message is already student-readable, which is what the sync banner
  shows.
- **HTTPS only**, enforced by `res/xml/network_security_config.xml` rather than
  relying on the platform default.
- **Uploaded files are checked.** A download must start with `%PDF-` (the spec
  allows up to 1024 leading bytes); otherwise it is deleted and reported instead
  of being shown as a broken paper.

### Known backend issues to fix in Supabase (not in this repo)

These need the dashboard/functions, so they are written down here rather than
fixed in the client:

1. **`content-requests` and `feedback` trust a client-generated key.** The app
   sends a random `requesterKey` / `feedbackKey` stored in `SharedPreferences`,
   and the functions use it as the caller's identity for `list` and `delete`.
   Anyone who learns another user's key can read or delete their requests.
   Fix: require a Supabase auth session, derive identity from the JWT
   (`auth.uid()`), and rate-limit the endpoints. Until then, treat request
   contents as low-sensitivity.
2. **JWT verification.** Both functions answer without an `apikey` header, which
   means they run with `verify_jwt` disabled. That is acceptable only while the
   functions validate every input themselves and write through RLS-safe paths;
   turning verification back on requires the client to send the anon key.

## Release (Android)

Release builds **require** a keystore — they will fail (not debug-sign) if it is missing.

### One-time: create a keystore

```bash
cd android
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/paperadda-release.jks \
  -alias paperadda \
  -keyalg RSA -keysize 2048 -validity 10000
# store the password in keystore/release-keystore.pw (two lines, no quotes):
#   storePassword=YOUR_PASSWORD
```

Both files are gitignored. **Back them up offline** — losing the keystore means you cannot update the app on Play.

### Build a release

```bash
cd android
./gradlew bundleRelease   # AAB for Play Store
./gradlew assembleRelease # APK for sideload
```

Verify the APK is release-signed (not debug):

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### Version bumps

Edit `android/app/build.gradle.kts`:

- `versionCode` — integer, +1 every upload
- `versionName` — user-visible string, e.g. `"1.0.1"`

## Backend

Supabase project tables: `subjects`, `papers`, `notes` (see `admin-portal/supabase-setup.sql`).

In the Supabase dashboard, disable public sign-ups (Authentication → Providers → Email) so only the admin account can log in.
