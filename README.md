# screensavarr

screensavarr is an Android TV screensaver that turns the catalog history in Sonarr and Radarr into a slow-moving media showcase. It is an independent project, not an official Jellyfin application, and it does not connect to Jellyfin. It is, however, a vibe-fork of the official Jellyfin Android TV client (more specifically, its screensaver).

The screensaver reads the Sonarr series and Radarr movie catalogs, including entries whose media files are no longer available (that's the entire point: show current _and_ removed media wallpaper for libraries that are frequently cleaned up, goes well together with some sort of automatic media procurement system). It uses the artwork already associated with those entries and does not download or manage media files.

## Configure

1. Install and open screensavarr on the Android TV device.
2. Enter the base URL and API key for Sonarr, Radarr, or both. Connection tests use only `GET /api/v3/system/status`.
3. Save the configuration, then select **screensavarr** from the device's system screensaver settings.

API keys are stored in the app's private preferences and are never sent to third-party artwork URLs. Use HTTPS URLs for both servers.

## Building

The project requires a compatible JDK and Android SDK. Build a debug APK with:

```shell
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.
