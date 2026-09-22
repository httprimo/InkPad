# How to publish an update (in-app updater)

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`
2. Build release: `./gradlew assembleRelease`
3. On GitHub → **Releases** → **Draft a new release**
   - Tag: `v2` (must match the new `versionCode`, with a `v` prefix)
   - Title: e.g. `1.0.1`
   - Upload `app/build/outputs/apk/release/app-release.apk` (rename to `InkPad-1.0.1.apk` if you like)
4. Publish — users tap **Settings → Check for update**
