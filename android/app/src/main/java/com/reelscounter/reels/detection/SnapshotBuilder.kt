package com.reelscounter.reels.detection

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Copies a bounded, visible-only slice of the accessibility tree into a [UiSnapshot]. */
object SnapshotBuilder {

  fun build(root: AccessibilityNodeInfo?, config: InstagramDetectionConfig): UiSnapshot? {
    if (root == null) return null
    val rect = Rect()
    root.getBoundsInScreen(rect)
    val screen = rect.toBounds()
    val nodes = ArrayList<UiNode>(128)

    // Breadth-first so that, if the node budget runs out, the large structural containers near
    // the top of the tree (viewer, pager, tabs) are already captured.
    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    queue.addLast(root to 0)
    while (queue.isNotEmpty()) {
      val (node, depth) = queue.removeFirst()
      try {
        if (nodes.size >= config.maxNodes || !node.isVisibleToUser) continue
        node.getBoundsInScreen(rect)
        nodes +=
          UiNode(
            viewId = entryName(node.viewIdResourceName),
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            bounds = rect.toBounds(),
            visible = true,
            scrollable = node.isScrollable,
            selected = node.isSelected,
          )
        if (depth < config.maxDepth) {
          for (i in 0 until node.childCount) {
            node.getChild(i)?.let { queue.addLast(it to depth + 1) }
          }
        }
      } finally {
        if (node !== root) recycle(node)
      }
    }
    // Drain anything left after hitting the budget.
    while (queue.isNotEmpty()) recycle(queue.removeFirst().first)

    return UiSnapshot(root.packageName?.toString(), screen, nodes)
  }

  fun boundsOf(node: AccessibilityNodeInfo): Bounds {
    val rect = Rect()
    node.getBoundsInScreen(rect)
    return rect.toBounds()
  }

  /** Node pooling was removed in API 33 (recycle() is a no-op there); older versions need it. */
  @Suppress("DEPRECATION")
  fun recycle(node: AccessibilityNodeInfo?) {
    if (node != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
  }

  private fun Rect.toBounds() = Bounds(left, top, right, bottom)
}
