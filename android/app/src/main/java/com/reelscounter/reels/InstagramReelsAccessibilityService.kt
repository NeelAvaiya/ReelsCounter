package com.reelscounter.reels

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reelscounter.reels.detection.Bounds
import com.reelscounter.reels.detection.InstagramDetectionConfig
import com.reelscounter.reels.detection.InstagramReelsDetector
import com.reelscounter.reels.detection.InstagramScreen
import com.reelscounter.reels.detection.ReelState
import com.reelscounter.reels.detection.ReelTransitionTracker
import com.reelscounter.reels.detection.SnapshotBuilder
import com.reelscounter.reels.detection.entryName

/**
 * Observes Instagram's accessibility events and counts swipes between Reels.
 *
 * Event flow: raw events only *schedule* an evaluation (throttled). An evaluation reads a bounded
 * snapshot of the window, asks [InstagramReelsDetector] which screen is showing, and feeds that to
 * [ReelTransitionTracker], which decides whether a reel change has settled. Pager scroll events
 * are fed to the tracker directly so it knows when the swipe animation is still running.
 *
 * Events are filtered to Instagram's package (see accessibility_service_config.xml and
 * [applyPackageFilter]); other apps' events are never delivered. To notice that the user has
 * left Instagram, the service only checks the *package name* of the active window while it
 * believes Instagram is in front.
 *
 * While Instagram is in front, [ReelsCountOverlay] shows today's count as a small badge.
 */
class InstagramReelsAccessibilityService : AccessibilityService() {

  private val handler = Handler(Looper.getMainLooper())
  private lateinit var store: ReelsUsageStore
  private lateinit var config: InstagramDetectionConfig
  private lateinit var detector: InstagramReelsDetector
  private lateinit var tracker: ReelTransitionTracker
  private lateinit var overlay: ReelsCountOverlay

  private var lastScreen: Bounds = Bounds.EMPTY
  private var lastAnalysisScreen: InstagramScreen? = null
  private var lastEvaluationAt = 0L
  private var evaluationScheduledAt = NOT_SCHEDULED

  private val evaluateRunnable = Runnable {
    evaluationScheduledAt = NOT_SCHEDULED
    evaluate()
  }

  private val presenceCheckRunnable = Runnable { checkStillInInstagram() }

  private val storeListener =
    object : ReelsUsageStore.Listener {
      override fun onConfigChanged(config: InstagramDetectionConfig) {
        handler.post { applyConfig(config) }
      }

      override fun onReelTransition(snapshot: UsageSnapshot, timestamp: Long) {
        handler.post { overlay.render(snapshot, pulse = true) }
      }

      override fun onStatusChanged(snapshot: UsageSnapshot) {
        handler.post {
          overlay.render(snapshot)
          if (!snapshot.trackingEnabled) stopObserving()
        }
      }
    }

  override fun onServiceConnected() {
    super.onServiceConnected()
    store = ReelsUsageStore.get(this)
    overlay = ReelsCountOverlay(this, store)
    applyConfig(store.loadDetectionConfig())
    store.addListener(storeListener)
    store.setServiceConnected(true)
    store.logDiagnostic("service connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || !::tracker.isInitialized) return
    if (!store.isTrackingEnabled) return
    if (!detector.isInstagramPackage(event.packageName)) return

    when (event.eventType) {
      AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event)
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scheduleEvaluation(0)
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleEvaluation(config.minEvaluationIntervalMs)
    }
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    if (::overlay.isInitialized) overlay.hide()
    if (::store.isInitialized) {
      store.removeListener(storeListener)
      store.setServiceConnected(false)
    }
    super.onDestroy()
  }

  private fun onScrolled(event: AccessibilityEvent) {
    // Not in Reels yet (or paused under an overlay): the scroll may mean the screen changed.
    if (tracker.state == ReelState.UNKNOWN || tracker.state == ReelState.PAUSED) {
      scheduleEvaluation(config.minEvaluationIntervalMs)
      return
    }

    val source = event.source
    val sourceId: String?
    val sourceBounds: Bounds?
    try {
      sourceId = entryName(source?.viewIdResourceName)
      sourceBounds = source?.let(SnapshotBuilder::boundsOf)
    } finally {
      SnapshotBuilder.recycle(source)
    }

    if (!detector.isReelPagerScroll(sourceId, sourceBounds, lastScreen)) {
      // Caption, carousel or similar; may still change content, so re-check the screen.
      scheduleEvaluation(config.minEvaluationIntervalMs)
      return
    }

    // Item indices are only trusted from the known pager id; a heuristically matched
    // full-screen list (e.g. a tall comments sheet) contributes scroll distance only.
    val trustedPager = detector.isKnownPager(sourceId)
    val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
    tracker.onPagerScroll(
      fromIndex = if (trustedPager) event.fromIndex else -1,
      toIndex = if (trustedPager) event.toIndex else -1,
      deltaY = deltaY,
      pagerHeight = sourceBounds?.height ?: 0,
      now = SystemClock.uptimeMillis(),
    )
    scheduleEvaluation(config.settleMs + 10)
  }

  private fun evaluate() {
    if (!store.isTrackingEnabled) return stopObserving()
    val now = SystemClock.uptimeMillis()
    lastEvaluationAt = now

    val root = rootInActiveWindow
    val snapshot =
      try {
        SnapshotBuilder.build(root, config)
      } finally {
        SnapshotBuilder.recycle(root)
      }
    snapshot?.let { lastScreen = it.screen }
    val analysis = detector.analyze(snapshot)
    val wallClock = System.currentTimeMillis()

    if (analysis.screen == InstagramScreen.REELS) store.touchReels(wallClock, config.sessionTimeoutMs)
    val transitions = tracker.onScreen(analysis, now)
    if (transitions > 0) {
      store.recordTransitions(transitions, wallClock)
      store.logDiagnostic("+$transitions reel transition(s)")
    }

    val inInstagram = analysis.screen != InstagramScreen.NOT_INSTAGRAM
    store.updatePresence(
      inInstagram = inInstagram,
      inReels = analysis.screen == InstagramScreen.REELS || analysis.screen == InstagramScreen.REELS_OVERLAY,
    )

    if (analysis.screen != lastAnalysisScreen) {
      lastAnalysisScreen = analysis.screen
      store.logDiagnostic("screen=${analysis.screen} signals=${analysis.signals}")
      if (store.isDebugMode && snapshot != null && inInstagram) {
        // View ids only: what selectors in InstagramDetectionConfig would need updating.
        val ids = snapshot.nodes.mapNotNull { it.viewId }.distinct().take(80)
        store.logDiagnostic("visible ids: ${ids.joinToString(", ")}")
      }
    }

    tracker.nextEvaluationDelay(now)?.let { scheduleEvaluation(it) }
    handler.removeCallbacks(presenceCheckRunnable)
    if (inInstagram) handler.postDelayed(presenceCheckRunnable, PRESENCE_CHECK_MS)
  }

  /**
   * Instagram events stop arriving once another app is in front, so poll the active window's
   * package (and nothing else) while we think Instagram is showing.
   */
  private fun checkStillInInstagram() {
    val root = rootInActiveWindow
    val packageName =
      try {
        root?.packageName
      } finally {
        SnapshotBuilder.recycle(root)
      }
    if (detector.isInstagramPackage(packageName)) {
      handler.postDelayed(presenceCheckRunnable, PRESENCE_CHECK_MS)
    } else {
      scheduleEvaluation(0)
    }
  }

  /** Coalesces bursts of events into at most one evaluation per minEvaluationIntervalMs. */
  private fun scheduleEvaluation(delayMs: Long) {
    val now = SystemClock.uptimeMillis()
    val target = maxOf(now + delayMs, lastEvaluationAt + config.minEvaluationIntervalMs)
    if (evaluationScheduledAt != NOT_SCHEDULED && evaluationScheduledAt <= target) return
    handler.removeCallbacks(evaluateRunnable)
    evaluationScheduledAt = target
    handler.postAtTime(evaluateRunnable, target)
  }

  private fun stopObserving() {
    handler.removeCallbacks(evaluateRunnable)
    handler.removeCallbacks(presenceCheckRunnable)
    evaluationScheduledAt = NOT_SCHEDULED
    lastAnalysisScreen = null
    if (::tracker.isInitialized) tracker.reset()
    store.updatePresence(inInstagram = false, inReels = false)
  }

  private fun applyConfig(newConfig: InstagramDetectionConfig) {
    config = newConfig
    detector = InstagramReelsDetector(newConfig)
    tracker = ReelTransitionTracker(newConfig)
    applyPackageFilter(newConfig)
    if (::store.isInitialized) store.logDiagnostic("detection config applied")
  }

  /** Restricts delivered events to the configured Instagram package(s). */
  private fun applyPackageFilter(config: InstagramDetectionConfig) {
    val info = serviceInfo ?: return
    info.packageNames = config.instagramPackages.toTypedArray()
    serviceInfo = info
  }

  private companion object {
    const val NOT_SCHEDULED = -1L
    // Also bounds how long the badge can linger after switching away from Instagram.
    const val PRESENCE_CHECK_MS = 1_000L
  }
}
