# PaperAdda

Offline-first Android app for previous-year question papers & study notes, plus a Cloudflare-hosted admin portal backed by Supabase.

## Layout

| Path | What it is |
|------|------------|
| `android/` | Kotlin + Jetpack Compose app (`com.paperadda.app`) |
| `admin-portal/` | Static admin UI → deployed as Cloudflare Worker `admin` at `https://admin.paperadda.workers.dev` |

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
# release signing: put keystore + release-keystore.pw under android/keystore/ (gitignored)
```

## Backend

Supabase project tables: `subjects`, `papers`, `notes` (see `admin-portal/supabase-setup.sql`).
