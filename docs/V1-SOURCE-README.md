# Mood Music Sorter — source package (UNVERIFIED PROTOTYPE)

**Important:** This is not the complete production app in the request. No APK has been built from it, and it has not been compiled or run on any device. The GitHub build may fail; an APK produced by it still needs testing. There are significant missing requirements and no Redmi Note 10S compatibility, reliability, speed, safety, or classification-accuracy guarantee. See Scope below before relying on it. Do not delete your original audio files based on the results.

## What is here

- Kotlin/Compose application source, Room storage, Media3 playback service, audio analysis based solely on samples, and a workflow intended to produce a signed universal, single release APK. The app targets arm64 and has no intentionally requested INTERNET permission; the workflow rejects a final APK if `aapt` reports that permission.
- `signing.p12` is the signing key. **Keep it private and back it up safely.** Its password is in `app/build.gradle.kts`. Anyone who gets both the file and password can sign impersonating updates. Do not upload this project to a public repository, share the source archive, or publish build logs containing private material. If this key has been exposed, generate and use your own key before installing your first release. Replacing it later means previously installed apps cannot be updated in place.
- A bundled DejaVu Sans font; see `FONT-LICENSE.txt`.
- Only one APK is expected in a successful build, not an app bundle or split APK. The project intentionally does not include Gradle, the Android SDK, or their caches.

## Phone/browser guide: private GitHub Actions build

1. Unzip the outer delivery ZIP on your phone. You should see `mood-source.zip`, `WORKFLOW_TO_COPY.txt`, and this README. **Do not unzip `mood-source.zip` for this method.**
2. Sign in to GitHub in your browser. Create a new repository, choose **Private**, initialize it with a README, and name it e.g. `mood-music`. Keep the repository private.
3. On the repository's Code tab choose **Add file → Upload files**, choose `mood-source.zip`, then **Commit changes**. The source ZIP includes the signing key, so don't share it.
4. Choose **Add file → Create new file**. Set the filename to exactly `.github/workflows/build.yml`. Open `WORKFLOW_TO_COPY.txt` on your phone, copy all its text, and paste it into GitHub's file editor. Choose **Commit changes** to the default branch. The workflow unpacks your uploaded source ZIP before it builds.
5. Open the repository's **Actions** tab. Select **Build signed APK**. If Actions asks, enable workflows. Select **Run workflow**, choose your default branch, and run it. A push may also trigger a build if your branch is named `main`.
6. Wait for the run to finish. Green means the cloud build and the manifest permission check passed; red means it failed. For a red run, open the failed step's log to find the error—no APK is available from that run. Send the log back for debugging; do not assume this source was already compiled successfully.
7. On a green run, open the run and under **Artifacts** download `MoodMusicSorter-signed-APK`. GitHub wraps artifacts in a ZIP: extract it and tap the **one** `app-release.apk` inside. Android may ask you to allow installs from your browser or file manager. You do not need an OBB or a model file.
8. For future updates keep the exact same `signing.p12` and package name. Increase `versionCode` in `app/build.gradle.kts`, recreate and re-upload `mood-source.zip`, and run the workflow again. Android will not install an update signed by a different key. To edit source, extract the inner ZIP and rebuild the ZIP with the project files at its root (not inside an extra enclosing folder).

## Computer build, Android Studio, or command line

Extract `mood-source.zip`. Install JDK 17, Gradle 8.7 and Android SDK platform 34/build-tools 34.0.0; set `ANDROID_HOME` or configure the SDK in Android Studio. Use Android Studio to open the extracted directory and synchronize, or execute:

```
gradle --no-daemon :app:assembleRelease
```

The intended output is `app/build/outputs/apk/release/app-release.apk`. Inspect permissions with `$ANDROID_HOME/build-tools/34.0.0/aapt dump permissions app/build/outputs/apk/release/app-release.apk`. Dependencies are fetched from Google/Maven during the build, but are packaged into the app. Gradle/plugin/dependency versions are pinned in the build files; the source package deliberately has no wrapper. Android Studio can create its own wrapper only if desired.

## Scope and known limitations

Implemented in source, **not verified**:
- MediaStore scan with duration setting; stored changes/deletions; bounded background queue; Room caching; pause/resume/cancel while service is alive; progress and rough throughput/ETA; basic benchmark. An interrupted scan can be started again manually and completed records are reused. Scan workers are capped at three to reduce codec contention rather than always using cores minus one.
- Three short PCM slices with built-in decoder, FFT, signal statistics, tempo/chroma estimates, heuristic mood and runner-up scores. Bad/silent files are marked Unsorted. There is no metadata input to the classifier.
- Persian/English UI and layout direction, optional Persian digits, basic light/dark theme, filename cleanup, categories, search and 100-item database pages, manual reassignment and reanalysis, basic battery help and permission retry.
- Media3 session service, basic playback controls, next/previous, seek, shuffle/repeat and playback speed. Basic About links. No network permission is deliberately requested.

Important requested functionality **missing or incomplete**:
- This is not a complete, validated implementation of the first and second priorities. No automatic resume after process death or reboot (restart the scan manually); no guarantee of precise resume, conflict-free service lifetime, correct foreground-service behavior under MIUI/Android 14, or 60 fps. No global crash guard; database and service failure paths are not exhaustively protected. Notifications may not display if notification permission is denied. Some system-generated player notifications and app label are not localized. A Persian label is not registered as an Android localized app name.
- No per-library percentile normalization, reliable major/minor or beat calibration, validated emotion model, confidence calibration, cultural robustness, or evidence that categories are balanced. Feature extraction is heuristic; derived features are not all persisted/displayed. Scores should not be interpreted as measured probabilities. No promise of 0.3–0.8 seconds per song, rapid rescans, or reliability across 1,200–20,000 tracks. The benchmark averages elapsed time divided by successful analyses and may include time spent on failures.
- No cover art/collages, category gradients, artist/album tabs, robust filters, artwork/full-screen player, visualizer, sleep timer, favorites/recent, automatic thermal management, or paging library. UI uses simple 100-item pages; playing a category loads its queue into memory. The complete queued list ignores the current search/sort. The player does not surface detailed playback errors. Manual correction does not train later decisions.
- No boot receiver, robust Android 14+ background restart, exclusions by folder, explicit DRM identification, persistent structured error log, or configurable wake lock renewal. Phone codecs differ. Some problematic files could still expose defects or block a decoder. Filename cleaning is basic and does not remove all numeric clutter.
- Missing creative extras: time/weather suggestion, mood wheel, custom categories, playlist export/custom playlists, stats, duplicate finder, widget, JSON backup/restore, AMOLED/Material You themes, animations/haptics, three-slide onboarding, share summary. Included extras are manual mood correction, light/dark toggle, reanalysis controls, and benchmark only. These omissions reflect limited implementation and lack of verification, **not an established APK size limit**.

## Verification checklist

- Workspace inspected: Android SDK and Gradle were not installed. No SDK or Gradle cache was downloaded into the workspace.
- Signing archive was created and its structure inspected with OpenSSL. The font license is included. The source and workflow were read manually, but this is not equivalent to compiling.
- **Not performed:** Kotlin/Gradle compilation, APK generation, manifest merge inspection, install/update test, Android 11–14 tests, Xiaomi MIUI/HyperOS tests, performance measurements, codec tests, locale/accessibility tests, or end-to-end review of every failure path.
