# 📱 ZeroGallery

[![CI](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml/badge.svg)](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml)
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

## 📄 License

This project is licensed under the **Apache License 2.0** – see [LICENSE](LICENSE) for details.

Third-party libraries in use are listed in [NOTICE](NOTICE).

