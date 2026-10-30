# Diell IPTV

An Android app for phones, tablets and Android TV that plays M3U playlists and lets you manage their channels.

## What it does

- **Playlists:** four ways to add one:
  - **M3U link** from your provider
  - **Xtream login:** server address, username and password
  - **MAC portal:** the app shows its own MAC address (Add playlist → MAC portal) for your provider to activate, then you enter the portal address
  - **`.m3u` file**, or an empty list you fill yourself
- **Online updates that keep your changes:** link, Xtream and portal playlists update themselves when the app opens (if older than 6 hours), or with **Update now**. Channels you removed stay removed, channels you added stay, and your names, groups, favorites and order are kept. **Bring back removed channels** undoes your removals.
- Rename, save back to a `.m3u` file (not for portals), delete.
- **Channels:** browse by group, search, favorites. Add, edit, remove (one or many at once, with Undo) and reorder channels.
- **Player:** plays HLS (`.m3u8`), MPEG-TS, DASH and normal video/audio streams. On a TV remote, Up/Down or Channel +/- switches channel, OK shows channel info, Back exits. On a phone, tap to show the controls.
- **Open with:** tapping an `.m3u` file in a file manager can open it straight into Diell IPTV.

The app ships with no channels. You add your own playlist.

## Build the APK

1. Install **Android Studio** (free): https://developer.android.com/studio
2. **File → Open** and pick this `DiellIPTV` folder. Wait for "Gradle sync" to finish (first time downloads a few hundred MB).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. The file appears at `app/build/outputs/apk/debug/app-debug.apk`.

Or from a terminal in this folder: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

## Build the APK online with GitHub (no Android Studio needed)

1. Create a free account at https://github.com and make a new repository.
2. Upload everything in this `DiellIPTV` folder (Add file → Upload files), including the hidden `.github` folder.
3. Open the **Actions** tab. The "Build APK" job runs by itself (or press **Run workflow**). It takes about 5–10 minutes.
4. Open the finished run and download **DiellIPTV-apk** at the bottom. Unzip it to get `DiellIPTV.apk`.

The same APK works on Samsung and every other Android phone, tablet and Android TV box.

## Install it

- **Phone:** copy the APK to the phone, open it, and allow "Install unknown apps" for your file manager when asked.
- **Android TV / TV box:** put the APK on a USB stick and open it with a file manager on the TV, or upload it to a link and use the *Downloader* app, or use `adb install app-debug.apk` over the network.

## Project layout

```
app/src/main/java/com/diell/iptv/
  MainActivity.kt          app entry, screen switching, "Open with" for .m3u files
  model/Models.kt          Channel and Playlist
  data/M3u.kt              M3U reader/writer
  data/PlaylistRepository.kt  saving playlists, reading/writing files
  data/Providers.kt        HTTP, Xtream login, MAC portal, device MAC
  data/Merge.kt            keeps your edits when a playlist updates
  ui/AppViewModel.kt       add/remove/edit/reorder/favorite/undo logic
  ui/PlaylistsScreen.kt    home screen
  ui/ChannelsScreen.kt     channel list with groups, search and multi-select
  ui/ChannelEditDialog.kt  add / edit a channel
  ui/PlayerScreen.kt       full-screen player (Media3 ExoPlayer)
  ui/Components.kt         Diell sun logo, channel logo, TV focus outline
```

## Before publishing on Google Play

- Release builds are currently signed with the debug key so they install right away. Create your own keystore (**Build → Generate Signed Bundle / APK**) before publishing.
- Logins (Xtream passwords) are stored in the app's private storage on the device.
- Only distribute playlists and streams you have the rights to.
