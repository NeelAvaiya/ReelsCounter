package com.reelscounter.reels.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelTransitionTrackerTest {

  private val config = InstagramDetectionConfig.DEFAULT
  private val detector = InstagramReelsDetector(config)
  private val tracker = ReelTransitionTracker(config)
  private var now = 10_000L
  private var counted = 0

  private fun reels(author: String, caption: String = "caption of $author") =
    detector.analyze(reelsScreen(author, caption))

  private fun screen(analysis: ScreenAnalysis, advance: Long = 0) {
    now += advance
    counted += tracker.onScreen(analysis, now)
  }

  /** Simulates a swipe: a burst of scroll + content events, then the follow-up evaluations. */
  private fun swipe(to: ScreenAnalysis, index: Int = -1, events: Int = 8) {
    repeat(events) {
      now += 16
      tracker.onPagerScroll(if (it == events - 1) index else -1, index, deltaY = 290, pagerHeight = 2340, now = now)
      counted += tracker.onScreen(to, now) // content-change evaluations mid-swipe
    }
    settle(to)
  }

  /** Runs the evaluations the service would schedule until the tracker has nothing pending. */
  private fun settle(analysis: ScreenAnalysis) {
    var guard = 0
    while (guard++ < 10) {
      val delay = tracker.nextEvaluationDelay(now) ?: return
      screen(analysis, delay)
    }
  }

  @Test
  fun `entering Reels does not count`() {
    screen(reels("alice"))
    settle(reels("alice"))
    assertEquals(0, counted)
    assertEquals(ReelState.ACTIVE, tracker.state)
  }

  @Test
  fun `each swipe counts exactly once despite many events`() {
    screen(reels("a"))
    swipe(reels("b"))
    assertEquals(1, counted)
    swipe(reels("c"))
    swipe(reels("d"))
    assertEquals(3, counted)
    // Many redundant evaluations of the same reel afterwards change nothing.
    repeat(20) { screen(reels("d"), 50) }
    assertEquals(3, counted)
  }

  @Test
  fun `bounce back to the same reel does not count`() {
    screen(reels("a"))
    swipe(reels("a"))
    assertEquals(0, counted)
  }

  @Test
  fun `content loading progressively does not count`() {
    val partial = ScreenAnalysis(InstagramScreen.REELS, mapOf("clips_author_username" to "h1"))
    val full = ScreenAnalysis(InstagramScreen.REELS, mapOf("clips_author_username" to "h1", "clips_caption_component" to "h2"))
    screen(partial)
    repeat(5) { screen(full, 200) }
    assertEquals(0, counted)
  }

  @Test
  fun `pager index is preferred and capped`() {
    screen(reels("a"))
    swipe(reels("b"), index = 1)
    assertEquals(1, counted)
    swipe(reels("b"), index = 9) // jump; identity unchanged but index moved 8 -> capped
    assertEquals(1 + config.maxTransitionsPerSettle, counted)
  }

  @Test
  fun `scroll distance is used when nothing identifies reels`() {
    val anonymous = ScreenAnalysis(InstagramScreen.REELS, emptyMap())
    screen(anonymous)
    swipe(anonymous) // 8 * 290 = 2320 px, about one page
    assertEquals(1, counted)
  }

  @Test
  fun `comments overlay pauses and keeps position`() {
    screen(reels("a"))
    screen(ScreenAnalysis(InstagramScreen.REELS_OVERLAY, emptyMap()), 500)
    assertEquals(ReelState.PAUSED, tracker.state)
    // Scrolling the comments list while paused is ignored.
    tracker.onPagerScroll(0, 0, 2000, 2340, now)
    screen(reels("a"), 500)
    settle(reels("a"))
    assertEquals(0, counted)
    swipe(reels("b"))
    assertEquals(1, counted)
  }

  @Test
  fun `leaving Reels resets so returning to a different reel does not count`() {
    screen(reels("a"))
    screen(ScreenAnalysis(InstagramScreen.OTHER, emptyMap()), 500)
    assertEquals(ReelState.UNKNOWN, tracker.state)
    screen(reels("z"), 500)
    settle(reels("z"))
    assertEquals(0, counted)
  }

  @Test
  fun `stories and feed never count`() {
    screen(ScreenAnalysis(InstagramScreen.STORIES, emptyMap()))
    tracker.onPagerScroll(1, 1, 2340, 2340, now)
    screen(ScreenAnalysis(InstagramScreen.STORIES, emptyMap()), 1000)
    screen(ScreenAnalysis(InstagramScreen.OTHER, emptyMap()), 1000)
    tracker.onPagerScroll(2, 2, 2340, 2340, now)
    screen(ScreenAnalysis(InstagramScreen.OTHER, emptyMap()), 1000)
    assertEquals(0, counted)
  }

  @Test
  fun `require scroll evidence blocks content-only changes`() {
    val strict = ReelTransitionTracker(config.copy(requireScrollEvidence = true))
    var c = strict.onScreen(reels("a"), 0)
    c += strict.onScreen(reels("b"), 1000)
    c += strict.onScreen(reels("b"), 1200)
    c += strict.onScreen(reels("b"), 1400)
    assertEquals(0, c)
  }

  @Test
  fun `content-only change counts by default after confirmation`() {
    screen(reels("a"))
    screen(reels("b"), 1000)
    assertEquals(0, counted) // needs confirmation first
    screen(reels("b"), config.confirmMs)
    assertEquals(1, counted)
  }
}
