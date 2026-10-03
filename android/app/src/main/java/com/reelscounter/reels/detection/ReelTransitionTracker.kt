package com.reelscounter.reels.detection

import kotlin.math.abs

enum class ReelState {
  /** Not in Reels (or nothing known yet). */
  UNKNOWN,
  /** In Reels, settled on a known reel. */
  ACTIVE,
  /** In Reels, the pager is moving; nothing is counted until it settles. */
  TRANSITIONING,
  /** Comments/share sheet over Reels. Position is kept so closing it does not count. */
  PAUSED,
}

/** Where the viewer is: pager index (from scroll events) and/or content identity (from the tree). */
data class ReelPosition(val index: Int?, val identity: Map<String, String>)

/**
 * Turns a noisy stream of accessibility observations into "exactly one count per reel change".
 *
 * A single swipe produces many scroll and content-change events. Nothing here counts events;
 * instead the tracker waits until the pager has been still for [InstagramDetectionConfig.settleMs],
 * requires the new position to stay unchanged for [InstagramDetectionConfig.confirmMs], and only
 * then compares it with the last counted position. Pure Kotlin and clock-injected for testing.
 */
class ReelTransitionTracker(private val config: InstagramDetectionConfig) {

  var state: ReelState = ReelState.UNKNOWN
    private set

  private var baseline: ReelPosition? = null
  private var settledIndex: Int? = null

  private var lastScrollAt = Long.MIN_VALUE / 2
  private var scrollSeenSinceBaseline = false
  private var scrollDeltaY = 0
  private var pageHeight = 0

  private var candidate: ReelPosition? = null
  private var candidateSince = 0L
  private var lastCountAt = Long.MIN_VALUE / 2

  /**
   * Feed a TYPE_VIEW_SCROLLED from the reel pager. [fromIndex]/[toIndex] are the event's
   * visible-item range (-1 if unknown); equal values mean the pager rests on that item.
   */
  fun onPagerScroll(fromIndex: Int, toIndex: Int, deltaY: Int, pagerHeight: Int, now: Long) {
    if (state == ReelState.UNKNOWN || state == ReelState.PAUSED) return
    state = ReelState.TRANSITIONING
    lastScrollAt = now
    scrollSeenSinceBaseline = true
    candidate = null
    if (fromIndex >= 0 && fromIndex == toIndex) settledIndex = fromIndex
    if (pagerHeight > 0) pageHeight = pagerHeight
    scrollDeltaY += deltaY
  }

  /**
   * Feed the result of analysing the current screen. Returns how many reel transitions to add
   * to the counter (almost always 0 or 1).
   */
  fun onScreen(analysis: ScreenAnalysis, now: Long): Int {
    when (analysis.screen) {
      InstagramScreen.REELS -> Unit
      InstagramScreen.REELS_OVERLAY -> {
        if (baseline != null) state = ReelState.PAUSED
        candidate = null
        return 0
      }
      else -> {
        reset()
        return 0
      }
    }

    val position = ReelPosition(settledIndex, analysis.reelIdentity)
    val base = baseline
    if (base == null || state == ReelState.UNKNOWN) {
      rebase(position)
      return 0
    }
    if (state == ReelState.PAUSED) state = ReelState.ACTIVE

    if (now - lastScrollAt < config.settleMs) {
      state = ReelState.TRANSITIONING
      return 0
    }

    val steps = stepsBetween(base, position)
    if (steps == 0) {
      // Bounced back to the same reel, or nothing happened.
      candidate = null
      if (state == ReelState.TRANSITIONING) clearScrollEvidence()
      state = ReelState.ACTIVE
      baseline = mergeKnown(base, position)
      return 0
    }

    if (candidate != position) {
      candidate = position
      candidateSince = now
      state = ReelState.TRANSITIONING
      return 0
    }
    if (now - candidateSince < config.confirmMs) return 0

    val counted =
      when {
        config.requireScrollEvidence && !scrollSeenSinceBaseline -> 0
        now - lastCountAt < config.minCountIntervalMs -> 0
        else -> steps
      }
    if (counted > 0) lastCountAt = now
    rebase(position)
    return counted
  }

  /**
   * When the caller should evaluate the screen again even if no new events arrive, so pending
   * settles and confirmations are not left hanging. Null when nothing is pending.
   */
  fun nextEvaluationDelay(now: Long): Long? =
    when {
      state == ReelState.TRANSITIONING && candidate != null ->
        (candidateSince + config.confirmMs - now).coerceAtLeast(0) + 10
      state == ReelState.TRANSITIONING ->
        (lastScrollAt + config.settleMs - now).coerceAtLeast(0) + 10
      else -> null
    }

  fun reset() {
    state = ReelState.UNKNOWN
    baseline = null
    settledIndex = null
    candidate = null
    clearScrollEvidence()
  }

  private fun rebase(position: ReelPosition) {
    baseline = position
    candidate = null
    state = ReelState.ACTIVE
    clearScrollEvidence()
  }

  private fun clearScrollEvidence() {
    scrollSeenSinceBaseline = false
    scrollDeltaY = 0
  }

  /** Fills in identity parts that were unknown when the baseline was taken. */
  private fun mergeKnown(base: ReelPosition, now: ReelPosition) =
    ReelPosition(base.index ?: now.index, base.identity + now.identity)

  /**
   * Most reliable signal available on both sides wins: pager index, then content identity, then
   * accumulated scroll distance (for pagers that report neither).
   */
  private fun stepsBetween(base: ReelPosition, now: ReelPosition): Int {
    val max = config.maxTransitionsPerSettle
    if (base.index != null && now.index != null) {
      return abs(now.index - base.index).coerceAtMost(max)
    }
    // Only views present on both sides are compared, so a caption that finishes loading after
    // the username does not look like a new reel.
    val shared = base.identity.keys intersect now.identity.keys
    if (shared.isNotEmpty()) {
      return if (shared.any { base.identity[it] != now.identity[it] }) 1 else 0
    }
    if (pageHeight > 0 && abs(scrollDeltaY) >= pageHeight / 2) {
      return ((abs(scrollDeltaY) + pageHeight / 2) / pageHeight).coerceIn(1, max)
    }
    return 0
  }
}
