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
./gradlew testDebugUnitTest
```

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
