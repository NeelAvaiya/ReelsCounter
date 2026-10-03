package com.reelscounter.reels.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal val SCREEN = Bounds(0, 0, 1080, 2340)

internal fun node(
  id: String? = null,
  bounds: Bounds = Bounds(0, 0, 100, 100),
  text: String? = null,
  desc: String? = null,
  scrollable: Boolean = false,
  selected: Boolean = false,
  visible: Boolean = true,
  className: String? = "android.view.View",
) = UiNode(id, className, text, desc, bounds, visible, scrollable, selected)

internal fun snapshot(vararg nodes: UiNode, pkg: String = "com.instagram.android") =
  UiSnapshot(pkg, SCREEN, nodes.toList())

internal fun reelsScreen(author: String, caption: String, vararg extra: UiNode) =
  snapshot(
    node("clips_viewer_view_pager", SCREEN, scrollable = true),
    node("clips_video_container", SCREEN),
    node("clips_ufi_component", Bounds(950, 1200, 1080, 2000)),
    node("clips_author_username", Bounds(40, 1900, 400, 1950), text = author),
    node("clips_caption_component", Bounds(40, 1960, 900, 2050), text = caption),
    *extra,
  )

class InstagramReelsDetectorTest {

  private val detector = InstagramReelsDetector(InstagramDetectionConfig.DEFAULT)

  @Test
  fun `other packages are never Instagram`() {
    val result = detector.analyze(snapshot(node("clips_viewer_view_pager", SCREEN), pkg = "com.example"))
    assertEquals(InstagramScreen.NOT_INSTAGRAM, result.screen)
  }

  @Test
  fun `null root is not Instagram`() {
    assertEquals(InstagramScreen.NOT_INSTAGRAM, detector.analyze(null).screen)
  }

  @Test
  fun `full screen clips viewer is Reels`() {
    val result = detector.analyze(reelsScreen("alice", "hello world"))
    assertEquals(InstagramScreen.REELS, result.screen)
    assertTrue(result.reelIdentity.isNotEmpty())
  }

  @Test
  fun `stories viewer is vetoed even with a full screen pager`() {
    val result =
      detector.analyze(
        snapshot(
          node("reel_viewer_root", SCREEN),
          node(null, SCREEN, scrollable = true),
          node("clips_tab", selected = true),
        )
      )
    assertEquals(InstagramScreen.STORIES, result.screen)
  }

  @Test
  fun `comments sheet over Reels is an overlay`() {
    val result = detector.analyze(reelsScreen("alice", "hi", node("layout_comment_thread_edittext")))
    assertEquals(InstagramScreen.REELS_OVERLAY, result.screen)
  }

  @Test
  fun `home feed with an inline reel is not Reels`() {
    val result =
      detector.analyze(
        snapshot(
          node("row_feed_photo_profile_name", text = "bob"),
          node(null, Bounds(0, 200, 1080, 2200), scrollable = true),
          node("clips_video_container", Bounds(0, 400, 1080, 1400)),
          node("clips_author_username", text = "bob"),
        )
      )
    assertEquals(InstagramScreen.OTHER, result.screen)
  }

  @Test
  fun `profile grid is not Reels`() {
    val result =
      detector.analyze(
        snapshot(node("row_profile_header_imageview"), node(null, Bounds(0, 600, 1080, 2200), scrollable = true))
      )
    assertEquals(InstagramScreen.OTHER, result.screen)
  }

  @Test
  fun `selected Reels tab plus full screen pager works without known ids`() {
    val result =
      detector.analyze(
        snapshot(
          node("some_renamed_pager", SCREEN, scrollable = true),
          node("tab_x", Bounds(400, 2200, 600, 2340), desc = "Reels", selected = true),
        )
      )
    assertEquals(InstagramScreen.REELS, result.screen)
  }

  @Test
  fun `unselected Reels tab is not evidence`() {
    val result =
      detector.analyze(
        snapshot(
          node(null, SCREEN, scrollable = true),
          node("clips_tab", Bounds(400, 2200, 600, 2340), desc = "Reels", selected = false),
        )
      )
    assertEquals(InstagramScreen.OTHER, result.screen)
  }

  @Test
  fun `small clips container does not count as the viewer`() {
    val result = detector.analyze(snapshot(node("clips_viewer_container", Bounds(0, 0, 1080, 500))))
    assertEquals(InstagramScreen.OTHER, result.screen)
  }

  @Test
  fun `invisible nodes are ignored`() {
    val result = detector.analyze(snapshot(node("clips_viewer_view_pager", SCREEN, visible = false)))
    assertEquals(InstagramScreen.OTHER, result.screen)
  }

  @Test
  fun `identity differs between reels and ignores caption expansion`() {
    val a = detector.analyze(reelsScreen("alice", "A very long caption that keeps going and going")).reelIdentity
    val aExpanded =
      detector.analyze(reelsScreen("alice", "A very long caption that keeps going and going and more text")).reelIdentity
    val b = detector.analyze(reelsScreen("bob", "Another reel")).reelIdentity
    assertEquals(a, aExpanded)
    assertNotEquals(a, b)
  }

  @Test
  fun `pager scroll recognised by id or by full screen size only`() {
    assertTrue(detector.isReelPagerScroll("clips_viewer_view_pager", null, SCREEN))
    assertTrue(detector.isReelPagerScroll(null, Bounds(0, 0, 1080, 2300), SCREEN))
    assertFalse(detector.isReelPagerScroll("caption_scroll", Bounds(0, 1800, 1080, 2000), SCREEN))
  }

  @Test
  fun `config override replaces only given keys`() {
    val config = InstagramDetectionConfig.fromJson("""{"reelsViewerIds":["new_viewer"],"settleMs":500}""")
    assertEquals(listOf("new_viewer"), config.reelsViewerIds)
    assertEquals(500L, config.settleMs)
    assertEquals(InstagramDetectionConfig.DEFAULT.storiesIds, config.storiesIds)

    val renamed = InstagramReelsDetector(config).analyze(snapshot(node("new_viewer", SCREEN)))
    assertEquals(InstagramScreen.REELS, renamed.screen)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `config override rejects invalid ratios`() {
    InstagramDetectionConfig.fromJson("""{"minPagerHeightRatio": 3}""")
  }

  @Test
  fun `id matcher globs`() {
    val m = IdMatcher(listOf("exact", "pre*", "*suf", "*mid*"))
    assertTrue(m.matches("exact"))
    assertTrue(m.matches("prefix"))
    assertTrue(m.matches("a_suf"))
    assertTrue(m.matches("x_mid_y"))
    assertFalse(m.matches("exactly"))
    assertFalse(m.matches(null))
  }
}
