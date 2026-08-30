# 📱 ZeroGallery

[![CI](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml/badge.svg)](https://github.com/drzeeb/ZeroGallery/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

**ZeroGallery** ist eine extrem schlanke, werbefreie und **100 % offline** nutzbare Galerie-App für Fotos und Videos auf Android.

Kein Tracking. Kein Internet-Zugriff. Keine Cloud. Nur deine Medien, deine Kontrolle.

## ✨ Features

- 📷 **Schnelles, natives Einlesen** von Fotos & Videos via `MediaStore`
- 🖼️ **Performantes Grid** mit gecachten Thumbnails (Bilder & Videos)
- 📱💻 **Adaptive UI** – responsive Layout für Phones *und* Tablets (dynamische Spaltenanzahl, Breakpoints)
- 🔍 **Vollbild-Viewer** mit Swipe-Navigation (`HorizontalPager`)
- ▶️ **Video-Wiedergabe** via [Media3/ExoPlayer](https://developer.android.com/media/media3) (Apache 2.0, hardwarebeschleunigt)
- 🔒 **Keine `INTERNET`-Permission** – die App kann technisch nicht ins Netz

## 🚫 Warum keine Internet-Permission?

ZeroGallery deklariert **bewusst kein** `android.permission.INTERNET`. Das ist keine Konfigurationsoption, sondern eine architektonische Garantie: Ohne diese Permission kann die App – unabhängig vom Code – niemals Daten versenden oder empfangen.

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- Clean Architecture (Data / Domain / UI)
- [Coil](https://coil-kt.github.io/coil/) für Thumbnail-Loading & Caching
- [Media3/ExoPlayer](https://developer.android.com/media/media3) für Video-Wiedergabe
- Min SDK 24, Ziel SDK aktuell

## 📦 Installation

Aktuell befindet sich das Projekt in aktiver Entwicklung. Releases werden zu gegebener Zeit über GitHub Releases bzw. F-Droid bereitgestellt.

```bash
git clone https://github.com/drzeeb/ZeroGallery.git
cd ZeroGallery
./gradlew assembleDebug
```

## 🗺️ Roadmap

- [x] Projekt-Setup, Lizenz, CI
- [ ] MediaStore Data-Layer & Permission-Handling
- [ ] Basis-Grid (Phone)
- [ ] Adaptive Layout (Tablet-Support)
- [ ] Detail-Viewer & Video-Player

## 🤝 Contributing

Contributions sind willkommen! Bitte öffne ein Issue, bevor du an größeren Features arbeitest, um Doppelarbeit zu vermeiden.

## 📄 Lizenz

Dieses Projekt steht unter der **Apache License 2.0** – siehe [LICENSE](LICENSE) für Details.

Verwendete Drittanbieter-Bibliotheken werden in [NOTICE](NOTICE) aufgeführt.

