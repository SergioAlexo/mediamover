# MediaMover

Android app (built for a Pixel 8 Pro) that automatically moves media from messenger folders —
WhatsApp, Telegram, Viber, Instagram, Messenger — into a folder you choose, e.g. `Download/`.

## How it works

- **Rules**: one or more source folders → a destination. Quick-pick chips detect where each messenger
  actually stores files on the phone. Per rule: move or copy, file types, include subfolders,
  skip WhatsApp `Sent` folders, keep folder structure, name-clash policy, minimum file age.
- **Automation**: a WorkManager job triggered by MediaStore changes (runs ~15 s after new media appears),
  plus a periodic safety-net run (15 min – 6 h, or off). "Run now" and "Preview" (dry run) on the home screen.
- **Activity log** with "Undo last" for moves.
- Moves are same-volume renames (instant); falls back to copy → verify size → delete. MediaStore is
  rescanned so Photos/Files see the new location.

## Android storage notes

- Needs **All files access** (`MANAGE_EXTERNAL_STORAGE`) — the only way to read `Android/media/com.whatsapp/…`.
  Fine for a sideloaded app; Play Store would require a review.
- `Android/data/…` is off-limits to every app. Telegram keeps its cache there, so turn on
  **Telegram → Settings → Chat Settings → Save to Gallery**; files then land in `Pictures/Telegram` / `Download/Telegram`.
- Moving WhatsApp/Viber media makes it show as "media not found" inside the chat. Use **Copy** mode if that matters.

## Build

Requirements: JDK 17, Android SDK (platform 35, build-tools 35). `local.properties` points at the SDK.

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Release signing reads `keystore.properties`
(gitignored: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the debug key is used.
Keep the same key for every build, or Android will refuse to update the installed app.

On Windows, if Gradle fails with "Unable to establish loopback connection" (a short 8.3 `TEMP` path like
`USERNA~1` breaks JDK's loopback socket), set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/some/short/dir`.

## Install on the phone

USB: enable Developer options → USB debugging, then `adb install -r app/build/outputs/apk/release/app-release.apk`.
Or copy the APK to the phone and open it (allow "Install unknown apps" for Files).

## License

[MIT](LICENSE)
