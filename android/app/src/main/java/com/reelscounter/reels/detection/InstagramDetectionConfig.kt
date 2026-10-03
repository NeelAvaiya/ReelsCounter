package com.reelscounter.reels.detection

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every Instagram-specific selector and threshold lives here, and nowhere else.
 *
 * Instagram renames views often, so nothing below is treated as permanent:
 * - Selectors are matched against resource *entry names* with simple globs
 *   ("reel_viewer_*", "*_composer", "*comment*").
 * - Any field can be overridden at runtime with a partial JSON document (see [fromJson]), which
 *   the app exposes in Settings → Advanced, so selectors can be fixed without shipping a build.
 *
 * Naming note: Instagram internally calls *Stories* "reel" (reel_viewer_*) and *Reels* "clips"
 * (clips_*). The defaults reflect that, so do not "fix" them to match the product names.
 */
data class InstagramDetectionConfig(
  val instagramPackages: List<String> = listOf("com.instagram.android"),

  /** Large containers that only exist in the full-screen Reels viewer. Strongest positive signal. */
  val reelsViewerIds: List<String> =
    listOf(
      "clips_viewer_view_pager",
      "clips_viewer_container",
      "clips_viewer_fragment_container",
      "clips_swipe_refresh_container",
    ),

  /** Per-reel widgets. Supporting evidence only; two or more plus a full-screen pager means Reels. */
  val reelsItemIds: List<String> =
    listOf(
      "clips_video_container",
      "clips_media_component",
      "clips_ufi_component",
      "clips_ufi_more_button",
      "clips_author_username",
      "clips_author_info_component",
      "clips_caption_component",
      "clips_audio_attribution*",
      "clips_item_overlay_component",
    ),

  /** Bottom-navigation Reels tab. Only counts as evidence when it is selected. */
  val reelsTabIds: List<String> = listOf("clips_tab"),
  /** Localised labels for the Reels tab (text or content description), used as a fallback. */
  val reelsTabLabels: List<String> = listOf("Reels"),

  /** Stories viewer. Full-screen and vertically paged like Reels, so it is an explicit veto. */
  val storiesIds: List<String> =
    listOf("reel_viewer_*", "story_viewer_*", "stories_viewer_*", "reel_main_*"),

  /** Overlays shown on top of Reels: comments, share sheet, "more" menu. Pause, don't reset. */
  val overlayIds: List<String> =
    listOf(
      "layout_comment_thread_*",
      "comment_composer*",
      "row_comment_*",
      "comments_*",
      "bottom_sheet_container",
      "bottom_sheet_drag_handle*",
      "direct_private_share_*",
      "action_sheet_*",
    ),

  /** Other Instagram surfaces. Seeing these without the Reels viewer means "not Reels". */
  val otherScreenIds: List<String> =
    listOf(
      "row_feed_*",
      "main_feed_*",
      "row_profile_header*",
      "profile_header_*",
      "profile_tab*",
      "action_bar_search_edit_text",
      "row_search_*",
      "direct_thread_*",
      "direct_inbox_*",
    ),

  /**
   * Views whose text/description identify the reel currently on screen. Only a short prefix of
   * each value is used, and only as an in-memory hash; nothing is stored or transmitted.
   * Avoid views whose text changes while a reel plays (like counts, timers).
   */
  val identityIds: List<String> =
    listOf("clips_author_username", "clips_caption_component", "clips_audio_attribution*"),
  val identityTextPrefix: Int = 32,

  /** Ids of the vertical pager whose TYPE_VIEW_SCROLLED events represent a reel swipe. */
  val pagerIds: List<String> = listOf("clips_viewer_view_pager"),

  /** Minimum share of the window height a Reels viewer container must cover. */
  val minViewerHeightRatio: Float = 0.6f,
  /** Minimum share of the window a scrollable node must cover to be treated as the reel pager. */
  val minPagerHeightRatio: Float = 0.7f,
  val minPagerWidthRatio: Float = 0.9f,

  /** No pager scroll for this long = the swipe animation has finished. */
  val settleMs: Long = 300,
  /** A new reel identity must stay unchanged this long before it is counted. */
  val confirmMs: Long = 150,
  /** Hard floor between two counts; protects against event storms. */
  val minCountIntervalMs: Long = 350,
  /** Upper bound on transitions credited for one settled swipe (e.g. index jumped by 2). */
  val maxTransitionsPerSettle: Int = 3,
  /** If true, a change of reel identity only counts when a pager scroll was observed. */
  val requireScrollEvidence: Boolean = false,

  /** Outside Reels for longer than this ends the current session. */
  val sessionTimeoutMs: Long = 120_000,
  /** Throttle for re-reading the tree on TYPE_WINDOW_CONTENT_CHANGED storms. */
  val minEvaluationIntervalMs: Long = 200,

  /** Traversal limits that keep each evaluation cheap on the main thread. */
  val maxNodes: Int = 600,
  val maxDepth: Int = 40,
) {

  fun toJson(): JSONObject =
    JSONObject().apply {
      put("instagramPackages", JSONArray(instagramPackages))
      put("reelsViewerIds", JSONArray(reelsViewerIds))
      put("reelsItemIds", JSONArray(reelsItemIds))
      put("reelsTabIds", JSONArray(reelsTabIds))
      put("reelsTabLabels", JSONArray(reelsTabLabels))
      put("storiesIds", JSONArray(storiesIds))
      put("overlayIds", JSONArray(overlayIds))
      put("otherScreenIds", JSONArray(otherScreenIds))
      put("identityIds", JSONArray(identityIds))
      put("identityTextPrefix", identityTextPrefix)
      put("pagerIds", JSONArray(pagerIds))
      put("minViewerHeightRatio", minViewerHeightRatio.toDouble())
      put("minPagerHeightRatio", minPagerHeightRatio.toDouble())
      put("minPagerWidthRatio", minPagerWidthRatio.toDouble())
      put("settleMs", settleMs)
      put("confirmMs", confirmMs)
      put("minCountIntervalMs", minCountIntervalMs)
      put("maxTransitionsPerSettle", maxTransitionsPerSettle)
      put("requireScrollEvidence", requireScrollEvidence)
      put("sessionTimeoutMs", sessionTimeoutMs)
      put("minEvaluationIntervalMs", minEvaluationIntervalMs)
      put("maxNodes", maxNodes)
      put("maxDepth", maxDepth)
    }

  companion object {
    val DEFAULT = InstagramDetectionConfig()

    /**
     * Builds a config from a *partial* JSON override: keys that are missing keep their defaults.
     * Throws [org.json.JSONException] or [IllegalArgumentException] on invalid input so callers
     * can reject a bad override instead of silently tracking with broken selectors.
     */
    fun fromJson(json: String?): InstagramDetectionConfig {
      if (json.isNullOrBlank()) return DEFAULT
      val o = JSONObject(json)
      val d = DEFAULT

      fun list(key: String, fallback: List<String>): List<String> {
        if (!o.has(key)) return fallback
        val arr = o.getJSONArray(key)
        return List(arr.length()) { arr.getString(it).trim() }.filter { it.isNotEmpty() }
      }
      fun ratio(key: String, fallback: Float): Float {
        val v = o.optDouble(key, fallback.toDouble()).toFloat()
        require(v in 0f..1f) { "$key must be between 0 and 1" }
        return v
      }
      fun millis(key: String, fallback: Long): Long {
        val v = o.optLong(key, fallback)
        require(v >= 0) { "$key must not be negative" }
        return v
      }
      fun positive(key: String, fallback: Int): Int {
        val v = o.optInt(key, fallback)
        require(v > 0) { "$key must be positive" }
        return v
      }

      val config =
        InstagramDetectionConfig(
          instagramPackages = list("instagramPackages", d.instagramPackages),
          reelsViewerIds = list("reelsViewerIds", d.reelsViewerIds),
          reelsItemIds = list("reelsItemIds", d.reelsItemIds),
          reelsTabIds = list("reelsTabIds", d.reelsTabIds),
          reelsTabLabels = list("reelsTabLabels", d.reelsTabLabels),
          storiesIds = list("storiesIds", d.storiesIds),
          overlayIds = list("overlayIds", d.overlayIds),
          otherScreenIds = list("otherScreenIds", d.otherScreenIds),
          identityIds = list("identityIds", d.identityIds),
          identityTextPrefix = positive("identityTextPrefix", d.identityTextPrefix),
          pagerIds = list("pagerIds", d.pagerIds),
          minViewerHeightRatio = ratio("minViewerHeightRatio", d.minViewerHeightRatio),
          minPagerHeightRatio = ratio("minPagerHeightRatio", d.minPagerHeightRatio),
          minPagerWidthRatio = ratio("minPagerWidthRatio", d.minPagerWidthRatio),
          settleMs = millis("settleMs", d.settleMs),
          confirmMs = millis("confirmMs", d.confirmMs),
          minCountIntervalMs = millis("minCountIntervalMs", d.minCountIntervalMs),
          maxTransitionsPerSettle = positive("maxTransitionsPerSettle", d.maxTransitionsPerSettle),
          requireScrollEvidence = o.optBoolean("requireScrollEvidence", d.requireScrollEvidence),
          sessionTimeoutMs = millis("sessionTimeoutMs", d.sessionTimeoutMs),
          minEvaluationIntervalMs = millis("minEvaluationIntervalMs", d.minEvaluationIntervalMs),
          maxNodes = positive("maxNodes", d.maxNodes),
          maxDepth = positive("maxDepth", d.maxDepth),
        )
      require(config.instagramPackages.isNotEmpty()) { "instagramPackages must not be empty" }
      return config
    }
  }
}

/** Glob matcher for resource entry names: "abc" exact, "abc*" prefix, "*abc" suffix, "*abc*" contains. */
class IdMatcher(patterns: List<String>) {
  private val exact = HashSet<String>()
  private val prefixes = ArrayList<String>()
  private val suffixes = ArrayList<String>()
  private val contains = ArrayList<String>()

  init {
    for (p in patterns) {
      val starts = p.startsWith('*')
      val ends = p.endsWith('*')
      val core = p.trim('*')
      if (core.isEmpty()) continue
      when {
        starts && ends -> contains += core
        ends -> prefixes += core
        starts -> suffixes += core
        else -> exact += core
      }
    }
  }

  /** Returns the matched pattern core (useful for diagnostics) or null. */
  fun match(id: String?): String? {
    if (id == null) return null
    if (id in exact) return id
    prefixes.firstOrNull { id.startsWith(it) }?.let { return id }
    suffixes.firstOrNull { id.endsWith(it) }?.let { return id }
    contains.firstOrNull { id.contains(it) }?.let { return id }
    return null
  }

  fun matches(id: String?): Boolean = match(id) != null
}
