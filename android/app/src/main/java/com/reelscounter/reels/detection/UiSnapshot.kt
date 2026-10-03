package com.reelscounter.reels.detection

/**
 * A lightweight, framework-free copy of the parts of the accessibility tree the detector needs.
 *
 * Keeping the detector on plain data (instead of AccessibilityNodeInfo) means the
 * Instagram-specific logic can be unit tested on the JVM and that node objects are released as
 * soon as the snapshot is built. Snapshots live only in memory for a single evaluation.
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  val width: Int
    get() = (right - left).coerceAtLeast(0)

  val height: Int
    get() = (bottom - top).coerceAtLeast(0)

  val area: Long
    get() = width.toLong() * height.toLong()

  fun intersectionArea(other: Bounds): Long {
    val w = minOf(right, other.right) - maxOf(left, other.left)
    val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
    return if (w <= 0 || h <= 0) 0 else w.toLong() * h.toLong()
  }

  companion object {
    val EMPTY = Bounds(0, 0, 0, 0)
  }
}

data class UiNode(
  /** Resource entry name only, e.g. "clips_viewer_view_pager" (package prefix stripped). */
  val viewId: String?,
  val className: String?,
  val text: String?,
  val contentDescription: String?,
  val bounds: Bounds,
  val visible: Boolean,
  val scrollable: Boolean,
  val selected: Boolean,
)

data class UiSnapshot(
  val packageName: String?,
  /** Bounds of the active window's root; used as the reference "screen" for size ratios. */
  val screen: Bounds,
  val nodes: List<UiNode>,
)

/** Strips "com.instagram.android:id/" style prefixes so selectors survive package renames. */
fun entryName(viewIdResourceName: String?): String? {
  if (viewIdResourceName.isNullOrEmpty()) return null
  val slash = viewIdResourceName.lastIndexOf('/')
  return if (slash >= 0) viewIdResourceName.substring(slash + 1) else viewIdResourceName
}
