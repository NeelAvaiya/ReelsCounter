package com.reelscounter.reels.detection

enum class InstagramScreen {
  /** Foreground window is not Instagram. */
  NOT_INSTAGRAM,
  /** Full-screen Reels viewer, nothing on top. The only state in which swipes count. */
  REELS,
  /** Reels viewer with comments / share sheet / menu on top. Counting pauses, position is kept. */
  REELS_OVERLAY,
  STORIES,
  /** Feed, profile, search, DMs, explore grid, settings... */
  OTHER,
}

data class ScreenAnalysis(
  val screen: InstagramScreen,
  /**
   * Identity of the reel on screen: identity view id -> hash of its text prefix. Empty when it
   * could not be determined. Per-view hashes (rather than one combined hash) let the tracker
   * ignore views that simply finished loading.
   */
  val reelIdentity: Map<String, String> = emptyMap(),
  /** Which signals drove the decision. View ids only, never user content. For diagnostics. */
  val signals: List<String> = emptyList(),
)

/**
 * Decides which Instagram surface is on screen and which reel is showing, from an accessibility
 * snapshot. Uses several independent signals (viewer containers, per-reel widgets, the selected
 * Reels tab, a full-screen vertical pager) and explicit vetoes (Stories, overlays, other
 * surfaces), so one renamed view id degrades accuracy rather than breaking detection.
 */
class InstagramReelsDetector(private val config: InstagramDetectionConfig) {

  private val viewer = IdMatcher(config.reelsViewerIds)
  private val items = IdMatcher(config.reelsItemIds)
  private val tab = IdMatcher(config.reelsTabIds)
  private val stories = IdMatcher(config.storiesIds)
  private val overlay = IdMatcher(config.overlayIds)
  private val other = IdMatcher(config.otherScreenIds)
  private val identity = IdMatcher(config.identityIds)
  private val pager = IdMatcher(config.pagerIds)
  private val tabLabels = config.reelsTabLabels.map { it.lowercase() }.toSet()

  fun isInstagramPackage(packageName: CharSequence?): Boolean =
    packageName != null && config.instagramPackages.contains(packageName.toString())

  fun isInstagramReels(snapshot: UiSnapshot?): Boolean = analyze(snapshot).screen == InstagramScreen.REELS

  fun analyze(snapshot: UiSnapshot?): ScreenAnalysis {
    if (snapshot == null || !isInstagramPackage(snapshot.packageName)) {
      return ScreenAnalysis(InstagramScreen.NOT_INSTAGRAM)
    }
    val screen = snapshot.screen
    val visible = snapshot.nodes.filter { it.visible }
    val signals = ArrayList<String>()

    val storiesHit = visible.firstNotNullOfOrNull { stories.match(it.viewId) }
    if (storiesHit != null) {
      return ScreenAnalysis(InstagramScreen.STORIES, signals = listOf("stories:$storiesHit"))
    }

    val viewerHit =
      visible.firstOrNull {
        viewer.matches(it.viewId) && it.bounds.height >= screen.height * config.minViewerHeightRatio
      }
    viewerHit?.let { signals += "viewer:${it.viewId}" }

    val itemHits = visible.mapNotNull { items.match(it.viewId) }.toSet()
    if (itemHits.isNotEmpty()) signals += "items:${itemHits.size}"

    val tabSelected =
      visible.any {
        it.selected &&
          (tab.matches(it.viewId) ||
            it.contentDescription?.trim()?.lowercase() in tabLabels ||
            it.text?.trim()?.lowercase() in tabLabels)
      }
    if (tabSelected) signals += "tab:selected"

    val fullScreenPager = findFullScreenPager(visible, screen)
    fullScreenPager?.let { signals += "pager:${it.viewId ?: it.className}" }

    val reelsEvidence =
      viewerHit != null ||
        (itemHits.size >= 2 && fullScreenPager != null) ||
        (tabSelected && fullScreenPager != null)

    if (!reelsEvidence) {
      visible.firstNotNullOfOrNull { other.match(it.viewId) }?.let { signals += "other:$it" }
      return ScreenAnalysis(InstagramScreen.OTHER, signals = signals)
    }

    val overlayHit = visible.firstNotNullOfOrNull { overlay.match(it.viewId) }
    if (overlayHit != null) {
      signals += "overlay:$overlayHit"
      return ScreenAnalysis(InstagramScreen.REELS_OVERLAY, signals = signals)
    }

    // Without the dedicated viewer container, a visible feed/profile/search view means the
    // weaker signals came from something like an inline reel in the feed.
    if (viewerHit == null) {
      val otherHit = visible.firstNotNullOfOrNull { other.match(it.viewId) }
      if (otherHit != null) {
        signals += "other:$otherHit"
        return ScreenAnalysis(InstagramScreen.OTHER, signals = signals)
      }
    }

    return ScreenAnalysis(InstagramScreen.REELS, reelIdentity(visible, screen), signals)
  }

  /**
   * Whether a TYPE_VIEW_SCROLLED event came from the reel pager (as opposed to a caption,
   * a horizontal carousel, or the comments list).
   */
  fun isReelPagerScroll(sourceViewId: String?, sourceBounds: Bounds?, screen: Bounds): Boolean {
    if (isKnownPager(sourceViewId)) return true
    if (sourceBounds == null || screen.height == 0) return false
    return sourceBounds.height >= screen.height * config.minPagerHeightRatio &&
      sourceBounds.width >= screen.width * config.minPagerWidthRatio
  }

  /** True only for the configured pager ids, not for size-based guesses. */
  fun isKnownPager(viewId: String?): Boolean = pager.matches(viewId)

  private fun findFullScreenPager(visible: List<UiNode>, screen: Bounds): UiNode? =
    visible
      .filter { it.scrollable || pager.matches(it.viewId) }
      .filter {
        it.bounds.height >= screen.height * config.minPagerHeightRatio &&
          it.bounds.width >= screen.width * config.minPagerWidthRatio
      }
      .maxByOrNull { it.bounds.area }

  /**
   * For each identity view id, a hash of the most visible node's text prefix. The raw text
   * never leaves this function.
   */
  private fun reelIdentity(visible: List<UiNode>, screen: Bounds): Map<String, String> =
    visible
      .filter { identity.matches(it.viewId) && it.bounds.intersectionArea(screen) > 0 }
      .groupBy { it.viewId!! }
      .mapNotNull { (id, nodes) ->
        val best = nodes.maxByOrNull { it.bounds.intersectionArea(screen) } ?: return@mapNotNull null
        val value = (best.text ?: best.contentDescription)?.trim()?.take(config.identityTextPrefix)
        if (value.isNullOrEmpty()) null else id to fnv1a64(value)
      }
      .toMap()

  private fun fnv1a64(value: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (c in value) {
      hash = hash xor c.code.toLong()
      hash *= 0x100000001b3L
    }
    return java.lang.Long.toHexString(hash)
  }
}
