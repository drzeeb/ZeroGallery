# 📱 ZeroGallery

[![CI](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml/badge.svg)](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/drzeeb/ZeroGallery/branch/main/graph/badge.svg)](https://codecov.io/gh/drzeeb/ZeroGallery)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://docs.renovatebot.com/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

**ZeroGallery** is an extremely lightweight, ad-free and **100% offline** gallery app for photos and videos on Android.

No tracking. No internet access. No cloud. Just your media, your control.

🌐 [Website](https://drzeeb.github.io/ZeroGallery/) · 🔒 [Privacy Policy](https://drzeeb.github.io/ZeroGallery/privacy.html)

## ✨ Features

- 📷 **Fast, native scanning** of photos & videos via `MediaStore`
- 🖼️ **Performant grid** with cached thumbnails (images & videos)
- 📱💻 **Adaptive UI** – responsive layout for phones *and* tablets (dynamic column count, breakpoints)
- 🔍 **Fullscreen viewer** with swipe navigation (`HorizontalPager`), pinch- and double-tap-to-zoom
- ▶️ **Video playback** via [Media3/ExoPlayer](https://developer.android.com/media/media3) (Apache 2.0, hardware-accelerated)
- 🔒 **No `INTERNET` permission** – the app is technically incapable of network access

## 🚫 Why no internet permission?

ZeroGallery **deliberately does not** declare `android.permission.INTERNET`. This is not a configuration option but an architectural guarantee: without this permission, the app can never send or receive data — regardless of what the code does.

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- Clean Architecture (Data / Domain / UI)
- [Coil](https://coil-kt.github.io/coil/) for thumbnail loading & caching
- [Media3/ExoPlayer](https://developer.android.com/media/media3) for video playback
- Min SDK 26, latest target SDK

## 📦 Installation

The project is currently under active development. Releases will be published on the **Google Play Store** in due course.

```bash
git clone https://github.com/drzeeb/ZeroGallery.git
cd ZeroGallery
./gradlew assembleDebug
```

## 🗺️ Roadmap

- [x] Project setup, license, CI
- [x] MediaStore data layer & permission handling
- [x] Basic grid (phone)
- [x] Adaptive layout (tablet support)
- [x] Detail viewer & video player

See [CHANGELOG.md](CHANGELOG.md) for a detailed history of changes.

## 🤝 Contributing

Contributions are welcome! Please open an issue before working on larger features to avoid duplicate work.

## 🚀 Releasing

Cutting a release is a manual [`workflow_dispatch`](.github/workflows/release.yml) run (Actions tab
→ "Release" → "Run workflow"), where you type in the version name (and optionally the version
code, otherwise it's just incremented by 1). It bumps `versionName`/`versionCode` in
`app/build.gradle.kts`, builds the release `.aab`/`.apk`, tags the commit (`vX.Y.Z`) and publishes
both artifacts as a GitHub Release.

Building an actually *signed* bundle (required for a Play Store upload) additionally needs these
repository secrets set once, under *Settings → Secrets and variables → Actions*:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Your upload keystore's `.jks`/`.keystore` file, base64-encoded (`base64 -w0 your.keystore`) |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore's password |
| `RELEASE_KEY_ALIAS` | The signing key's alias inside that keystore |
| `RELEASE_KEY_PASSWORD` | The signing key's own password |

...plus a repository **variable** (not secret) `RELEASE_SIGNING_ENABLED` set to `true`, so the
workflow knows to actually look for them. Without all of these, the workflow still runs and
produces an *unsigned* bundle/APK - useful for a dry run, but not installable/uploadable as-is.

The keystore and its passwords are intentionally never committed to this repository, not even
encrypted - see the comment above `releaseSigningConfigured` in `app/build.gradle.kts` for why.

## 📄 License

This project is licensed under the **Apache License 2.0** – see [LICENSE](LICENSE) for details.

Third-party libraries in use are listed in [NOTICE](NOTICE).

