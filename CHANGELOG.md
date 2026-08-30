# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- New app icon: a clean, flat photo-stack mark (two overlapping cards with a mountain/sun glyph punched through the front one) on a deep indigo backdrop, replacing the default Android Studio template icon (green debug grid + generic robot artwork). Fully vector-based (`ic_launcher_background`/`ic_launcher_foreground`), including a themed/monochrome variant for Android 13+
- Gallery grouping: a new button in the top bar cycles the thumbnail grid between no grouping (flat, purely reverse-chronological, the previous behaviour), by date (one section per month, e.g. "August 2026", with a full-width header above each section) and by folder - the latter first shows a folder *picker* (one tile per device folder/album, with a cover photo, name and item count, most recently active folder first), and only opens a folder's individual items once tapped, rather than dumping every item from every folder into one giant scrolling grid (`groupMedia`, unit-tested)
- Seek bar for video playback, shown at the bottom of the screen alongside the rest of the overlay chrome (play/pause, aspect-ratio button) - drag it to scrub to any position, with elapsed/total time labels either side
- Detail viewer's title bar is now a tap-to-toggle overlay drawn on top of the media instead of a bar that reserves its own space above it - photos/videos always fill the entire screen edge-to-edge, and a single tap hides/shows the bar, like most other gallery apps
- True immersive fullscreen: hiding that title bar overlay also hides the system status/navigation bars (`WindowInsetsControllerCompat`, edge swipe still reveals them temporarily), restored when the bar is toggled back on or when leaving the screen
- VLC-style aspect-ratio toggle for video playback - Fit (letterboxed, default) and Stretch (fills the screen edge-to-edge with no black bars, cropping whatever overflows to keep the picture undistorted) - cycled via a button in the top-right corner with a fading label confirming the mode
- Remembers a previously granted media permission across app launches (`MediaPermissions.hasAll`, re-checked on `Lifecycle.Event.ON_START`) instead of showing the permission rationale screen again on every cold start
- Detail viewer's title bar now truncates long filenames to a single line with an ellipsis instead of wrapping/overflowing
- Double-tap-to-zoom for photos in the detail viewer, like most other gallery apps: double-tapping toggles between the fitted 1x view and 3x zoom, centered on the tapped point rather than always the image center (`computeDoubleTapOffset`, unit-tested). Double-tapping again while zoomed in zooms back out to 1x. Both directions are animated with a short, snappy 200ms tween (`Animatable`) instead of jumping instantly; pinch/pan continue to update instantly (`snapTo`) since the user's fingers are actively driving them

### Fixed
- Folder view's "show hidden folders" toggle didn't actually find real hidden folders: Android's `MediaStore` media scanner architecturally never indexes any dot-prefixed directory (the convention several vault/messaging apps use to hide their media), so no `ContentResolver` query could ever surface that content, no matter how the toggle filtered its results. `HiddenMediaScanner` now reads such folders directly off the filesystem when the user opts in, merging their contents into the regular `MediaStore`-backed list; since this requires raw filesystem access, enabling the toggle for the first time now prompts for the "All files access" (`MANAGE_EXTERNAL_STORAGE`, API 30+) special permission via a rationale dialog and a system settings screen (`AllFilesAccessPermission`)
- Video playback kept running in the background - putting the app aside (home button, switching apps, locking the screen) didn't pause it. `VideoPlayer` now pauses on `Lifecycle.Event.ON_STOP`, same as swiping the video off-screen within the pager already did; it deliberately doesn't auto-resume when the app comes back to the foreground, requiring an explicit tap on play, same as e.g. YouTube
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

