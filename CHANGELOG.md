# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Project setup: Apache 2.0 license, README, CI workflow (`ci.yml`)
- MediaStore data layer (`MediaRepository`, `MediaStoreRepository`) reading photos & videos via `ContentResolver`, auto-refreshing on changes
- Runtime permission handling (`MediaPermissions`) for granular media permissions (API 33+) with legacy storage permission fallback
- Gallery screen (`GalleryViewModel`, `GalleryUiState`, `GalleryScreen`) driving the permission flow and a placeholder media list

### Changed
- Migrated the default project template from Fragments/Navigation/ViewBinding to a single-activity Jetpack Compose + Material 3 UI

