# InkPad

Personal digital notebook for Android tablets (stylus-first). Offline handwriting, PDF annotation, folders, templates, backup/restore, optional Supabase sync.

## Stack

- Kotlin + Jetpack Compose + Material 3
- Room / SQLite (vector strokes)
- Optional Supabase Storage backup

## Build

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/pasic/AppData/Local/Android/Sdk"
./gradlew :app:assembleDebug
```

## Supabase setup (so notes aren’t lost)

1. Create a project at https://supabase.com
2. In SQL Editor, run `supabase/setup.sql`
3. Enable Authentication → Providers → Anonymous
4. Copy Project URL and anon public key from Project Settings → API
5. In the app: Settings → Supabase sync → paste URL + key → enable → Save → Test connection → Upload now

Sync uploads a full ZIP backup (notebooks, strokes, PDFs, images) to the `inkpad` storage bucket. Download now restores the latest cloud backup onto this device.

Keys stay on-device in DataStore — never hard-coded in the app.

## In-app updates (GitHub Releases)

Repo: https://github.com/httprimo/InkPad

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
2. `./gradlew assembleRelease`
3. GitHub → Releases → tag `v2` (must match new `versionCode`) → attach the APK
4. Users: **Settings → Check for update**

See [UPDATE.md](UPDATE.md).
