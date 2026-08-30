# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Detail viewer's title bar is now a tap-to-toggle overlay drawn on top of the media instead of a bar that reserves its own space above it - photos/videos always fill the entire screen edge-to-edge, and a single tap hides/shows the bar, like most other gallery apps
- True immersive fullscreen: hiding that title bar overlay also hides the system status/navigation bars (`WindowInsetsControllerCompat`, edge swipe still reveals them temporarily), restored when the bar is toggled back on or when leaving the screen
- VLC-style aspect-ratio toggle for video playback - Fit (letterboxed, default), Crop and Stretch (both fill the screen edge-to-edge with no black bars, cropping whatever overflows to keep the picture undistorted) - cycled via a button in the top-right corner with a fading label confirming the mode
- Remembers a previously granted media permission across app launches (`MediaPermissions.hasAll`, re-checked on `Lifecycle.Event.ON_START`) instead of showing the permission rationale screen again on every cold start
- Detail viewer's title bar now truncates long filenames to a single line with an ellipsis instead of wrapping/overflowing
- Double-tap-to-zoom for photos in the detail viewer, like most other gallery apps: double-tapping toggles between the fitted 1x view and 3x zoom, centered on the tapped point rather than always the image center (`computeDoubleTapOffset`, unit-tested). Double-tapping again while zoomed in zooms back out to 1x. Both directions are animated with a short, snappy 200ms tween (`Animatable`) instead of jumping instantly; pinch/pan continue to update instantly (`snapTo`) since the user's fingers are actively driving them

### Fixed
- Tapping a video to hide the overlay chrome also toggled play/pause, so there was no way to hide it for a clean fullscreen view without also stopping playback. Tapping the video now only toggles the chrome; a dedicated play/pause button (shown only while the chrome is visible, alongside the aspect-ratio button) controls playback instead - while the chrome is hidden, it's truly just the video, no buttons or icons at all
- Video aspect-ratio "Stretch" mode used to scale width/height independently, visibly distorting the picture (circles turning into ovals). Verified against VLC's own actual behaviour - even its "Fill" option never distorts, it always preserves the video's proportions and crops the overflow instead - so Stretch now does the same (`RESIZE_MODE_ZOOM`)
- Detail viewer: swiping between photos didn't work at all. `ZoomableAsyncImage` used `detectTransformGestures`, which treats *any* single-finger movement as a "pan" and unconditionally consumes it - silently eating every horizontal drag before the enclosing `HorizontalPager` ever saw it. Replaced with a custom gesture detector that only consumes single-finger drags while the image is actually zoomed in (panning); at the default 1x fit, drags are left unconsumed so the pager can treat them as a normal swipe. Two-finger pinch-to-zoom still always works
- Video thumbnail badge: the play icon used `Modifier.aspectRatio(1f)` with no bounded size, so inside its `Row` it inflated to fill the loose constraints passed down from the grid tile, making the badge's rounded background grow inconsistently and reveal an uneven strip of the thumbnail behind it. Replaced with a fixed `Modifier.size(12.dp)` so the badge always hugs its content tightly

### Added

- GitHub Pages site (`docs/`): landing page (`index.html`) and a Play Store-ready privacy policy (`privacy.html`) explaining that ZeroGallery collects, stores and transmits no data whatsoever
- Full-screen detail viewer (`MediaDetailScreen`): swipeable `HorizontalPager` opened by tapping a grid tile
- Pinch-to-zoom/pan for photos (`ZoomableAsyncImage`), resetting automatically when swiping to a different page
- Inline video playback via Media3/ExoPlayer (`VideoPlayer`, Apache 2.0 - no GPL/LGPL licensing conflicts), auto-pausing as soon as a video page is swiped away
- Explicitly stripped the `ACCESS_NETWORK_STATE` permission that Media3/ExoPlayer's manifest requests by default (used for adaptive streaming, which ZeroGallery never performs) to keep the "no network capability whatsoever" guarantee airtight
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

