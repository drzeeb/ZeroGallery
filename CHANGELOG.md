# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `WindowWidthSizeClass` (Compact/Medium/Expanded, Material 3 breakpoints at 600dp/840dp), unit-tested via `windowWidthSizeClassOf()`
- `MediaGrid` now scales minimum thumbnail size and grid spacing up on Medium/Expanded windows (tablets) instead of using fixed phone-sized values
- Permission rationale and empty-gallery messages are now width-constrained (max 480dp) so their text stays readable instead of stretching edge to edge on tablets
- Adaptive thumbnail grid (`MediaGrid`) using `LazyVerticalGrid(GridCells.Adaptive)` - column count grows automatically with available width, no hard-coded breakpoints needed for phones vs. tablets
- Coil-based thumbnail loading for both images and videos through a single `AsyncImage(model = uri)` call, backed by a custom `ImageLoader` (`ZeroGalleryApplication`) registering Coil's video frame decoder
- Duration badge overlay on video thumbnails (`formatDuration`, unit-tested)
- Project setup: Apache 2.0 license, README, CI workflow (`ci.yml`)
- MediaStore data layer (`MediaRepository`, `MediaStoreRepository`) reading photos & videos via `ContentResolver`, auto-refreshing on changes
- Runtime permission handling (`MediaPermissions`) for granular media permissions (API 33+) with legacy storage permission fallback
- Gallery screen (`GalleryViewModel`, `GalleryUiState`, `GalleryScreen`) driving the permission flow and a placeholder media list

### Changed
- Migrated the default project template from Fragments/Navigation/ViewBinding to a single-activity Jetpack Compose + Material 3 UI

