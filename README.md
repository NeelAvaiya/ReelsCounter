# Reels Counter (Android)

Counts how many times you swipe to the next Reel in the official Instagram Android app, using an
Android `AccessibilityService`. Feed, Stories, profile, search and comments scrolling are not
counted. Counts are stored on the device only. Release builds do not request the INTERNET
permission.

- React Native 0.87 (New Architecture), TypeScript
- Native Kotlin TurboModule + AccessibilityService
- minSdk 24, target 36, compile 37

## Run

```sh
npm install
npm run android          # debug build on a connected device / emulator
npm test                 # JS tests (Jest)
npm run test:android     # Kotlin unit tests for the detector and state machine
npm run typecheck
```

After installing, open the app and follow the onboarding screen to enable the
**Reels Counter** accessibility service. On Android 13+ with a sideloaded APK, first allow
*restricted settings* in App info (the onboarding screen links there).

## Architecture

```
App.tsx                         routing: Onboarding (service off) / Home / Settings
src/
  specs/NativeReelsTracker.ts   TurboModule codegen spec (the only native boundary)
  services/tracker.ts           start/stop, status, settings intents, native events
  storage/usageStorage.ts       history, reset, detection overrides (native-backed)
  hooks/useTracker.ts           live snapshot: events + refresh on foreground
  screens/                      HomeScreen, OnboardingScreen, SettingsScreen
  components/                   CounterCard, TrackingStatus, Card, Button, Screen
android/app/src/main/java/com/reelscounter/reels/
  InstagramReelsAccessibilityService.kt   events -> snapshot -> detector -> tracker -> store
  ReelsUsageStore.kt                      SharedPreferences counts, sessions, live status
  ReelsTrackerModule.kt / ReelsTrackerPackage.kt   TurboModule
  detection/                              ALL Instagram-specific logic
    InstagramDetectionConfig.kt           selectors + thresholds, JSON-overridable
    InstagramReelsDetector.kt             which screen is showing, which reel is visible
    ReelTransitionTracker.kt              state machine: one count per settled reel change
    SnapshotBuilder.kt / UiSnapshot.kt    bounded copy of the accessibility tree
```

The accessibility service keeps counting while the UI is closed. Native `ReelsUsageStore` is
the source of truth, and JS reads it through the module. JS gets live updates from the
`reelScrollDetected` (`{count, sessionCount, timestamp}`) and `trackingStatusChanged`
(full snapshot) events.

## Design

- **Mark:** a portrait reel frame holding a five-bar tally. The source is `assets/logo.svg`. The
  launcher icon is adaptive (`res/drawable/ic_launcher_foreground.xml`, with a monochrome layer
  for Android 13 themed icons). Legacy PNGs and `assets/ic_launcher-playstore.png` (512 px)
  are rendered from the same artwork.
- **Look:** a hand tally counter. Today's count is shown in odometer digit cells (amber
  `#FFB020` on ink navy `#141A33`) over a cool paper background (`#EEF1F6`). Changed digits roll
  in, unless reduce motion is on. Tokens are in `src/theme/index.ts`, and the badge on Instagram
  mirrors them.
- **Type:** Barlow for text and Barlow Condensed for numbers and titles
  (`android/app/src/main/assets/fonts`, SIL OFL). All text goes through `components/Txt.tsx`.

## How detection works

**Events used** (`res/xml/accessibility_service_config.xml`, filtered to `com.instagram.android`):
`TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_SCROLLED`. Events only
*schedule* an evaluation, throttled to one every 200 ms, so a burst of events costs one tree
read.

**Which screen is showing** (`InstagramReelsDetector`). The detector combines several signals,
so a single renamed view degrades accuracy instead of breaking it:

| Signal | Effect |
|---|---|
| Full-height Reels viewer container (`clips_viewer_*`) | strong evidence for Reels |
| 2+ per-reel widgets (`clips_*`) **and** a full-screen scrollable pager | evidence |
| Selected Reels tab (`clips_tab` or label "Reels") **and** full-screen pager | evidence |
| Stories viewer (`reel_viewer_*`, `story_viewer_*`) | veto → STORIES |
| Comments / share / bottom sheet ids | → REELS_OVERLAY (pause, keep position) |
| Feed / profile / search / DM ids without the viewer container | → OTHER |

Instagram's internal names are inverted: Stories are `reel_*` and Reels are `clips_*`.

**Which reel is showing.** The detector uses three sources, most reliable first:
1. the pager's item index from `TYPE_VIEW_SCROLLED` (`fromIndex == toIndex` when it is at rest)
2. an identity hash per view (author, caption prefix, audio label). Only views present
   both before and after are compared, so a caption that loads late is not counted as a new reel
3. accumulated scroll distance of at least half a page, for pagers that report neither

**Counting once per swipe** (`ReelTransitionTracker`). The states are
`UNKNOWN → ACTIVE ⇄ TRANSITIONING`, plus `PAUSED` while an overlay is open. The tracker counts
only when the pager has been still for 300 ms (`settleMs`), the new position has stayed the same
for 150 ms (`confirmMs`), and it differs from the last counted position. There is also a 350 ms
floor between counts. Returning to Reels from elsewhere sets a new baseline and does not count.

**Count badge.** While Instagram is in front, the service shows today's count in a small,
draggable tag (`ReelsCountOverlay.kt`). It is full opacity in Reels and dimmed elsewhere, and it
pulses on each count. It uses `TYPE_ACCESSIBILITY_OVERLAY`, so it needs no "Display over other
apps" permission. Turn it off in Settings.

**Sessions.** A session ends after 2 minutes outside Reels. The next Reels visit starts a new
session.

## When Instagram changes its UI

Every selector and threshold is in `InstagramDetectionConfig`. To retune selectors on a device
without rebuilding:

1. Settings → Diagnostics → enable **Detection log**, then open Instagram Reels, Stories,
   the feed and so on.
2. The log shows each screen decision, its signals, and the visible **view ids** (never text).
3. Settings → Advanced detection → paste a partial JSON override, for example
   `{"reelsViewerIds": ["new_clips_pager_id"], "pagerIds": ["new_clips_pager_id"]}`.
   Missing keys keep their defaults, and invalid JSON or values are rejected. Use
   **Show defaults** to see the full config.

Then update the defaults in code and add a test case in
`android/app/src/test/.../InstagramReelsDetectorTest.kt`.

## Privacy

- Events are delivered for Instagram only. While Instagram is in front, the service checks the
  active window's **package name** every second so it notices when you leave Instagram.
- Text in the tree is used only in memory: a 32-character prefix of the author, caption and
  audio labels is hashed to tell reels apart, and then discarded. It is never persisted, logged
  or transmitted.
- Only daily counts, session counters and timestamps are stored, in app-private
  SharedPreferences. Data older than 60 days is pruned.
- No private APIs, network interception, scraping, root, or reading Instagram's data.

**Google Play:** apps using AccessibilityService for non-accessibility purposes must show a
prominent disclosure and get consent (the onboarding screen does this), declare
`isAccessibilityTool="false"`, and complete the Accessibility declaration in Play Console.
Review the current policy before publishing.

## Known limitations

- The default selectors are based on Instagram's known `clips_*` / `reel_viewer_*` naming. They
  have **not** been checked against a live Instagram build in this repo; verify them with the
  detection log on a real device.
- Content descriptions such as "Reels" are localised. The label fallback works for English by
  default; add other labels through `reelsTabLabels`.
- Reels opened from the feed, Explore or DMs use the same viewer and are counted.
- Swiping back to a previous reel also counts as a transition.
- Some OEM battery optimisers kill accessibility services. If counts stop, re-enable the service.
