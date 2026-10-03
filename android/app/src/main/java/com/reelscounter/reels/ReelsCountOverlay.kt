package com.reelscounter.reels

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Small draggable badge showing today's Reel count while Instagram is in front.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an enabled accessibility service may add without the
 * "Display over other apps" permission. The window is not focusable, so Instagram keeps input
 * focus and stays the active window the detector reads. Must be called on the main thread.
 */
class ReelsCountOverlay(
  private val service: AccessibilityService,
  private val store: ReelsUsageStore,
) {
  private val windowManager = service.getSystemService(WindowManager::class.java)
  private val density = service.resources.displayMetrics.density
  private var badge: TextView? = null
  private var params: WindowManager.LayoutParams? = null

  fun render(snapshot: UsageSnapshot, pulse: Boolean = false) {
    val visible =
      snapshot.overlayEnabled &&
        snapshot.trackingEnabled &&
        snapshot.serviceConnected &&
        snapshot.inInstagram
    if (!visible) return hide()

    val view = badge ?: show() ?: return
    val count = snapshot.todayCount
    view.text = label(count)
    view.contentDescription = "$count Reels watched today"
    // Dimmed outside Reels (feed, profile...) where nothing is being counted.
    view.alpha = if (snapshot.inReels) 1f else 0.55f
    if (pulse) pulse(view)
  }

  fun hide() {
    val view = badge ?: return
    try {
      windowManager.removeView(view)
    } catch (_: Exception) {
      // Already removed with the service window token.
    }
    badge = null
    params = null
  }

  private fun show(): TextView? {
    val (savedX, savedY) = store.overlayPosition()
    val layout =
      WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.TOP or Gravity.END
          // Default: right edge, below Instagram's top bar and above the like/comment column.
          x = if (savedX >= 0) savedX else dp(12)
          y = if (savedY >= 0) savedY else dp(140)
        }

    val view =
      TextView(service).apply {
        setTextColor(PAPER)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = badgeTypeface()
        includeFontPadding = false
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background =
          GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(INK)
            setStroke(dp(1), SIGNAL_EDGE)
          }
        elevation = dp(4).toFloat()
        setOnTouchListener(DragListener(layout))
      }

    return try {
      windowManager.addView(view, layout)
      badge = view
      params = layout
      view
    } catch (e: Exception) {
      store.logDiagnostic("overlay failed: ${e.javaClass.simpleName}")
      null
    }
  }

  /** Count in the app's amber, "reels" in paper: the same counter look as the app. */
  private fun label(count: Int): CharSequence {
    val number = count.toString()
    return SpannableStringBuilder(number)
      .append(if (count == 1) " reel" else " reels")
      .apply {
        setSpan(ForegroundColorSpan(SIGNAL), 0, number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(RelativeSizeSpan(1.2f), 0, number.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
  }

  private fun badgeTypeface(): Typeface =
    try {
      Typeface.createFromAsset(service.assets, "fonts/BarlowCondensed-SemiBold.ttf")
    } catch (_: Exception) {
      Typeface.DEFAULT_BOLD
    }

  private fun pulse(view: View) {
    view.animate().cancel()
    view
      .animate()
      .scaleX(1.25f)
      .scaleY(1.25f)
      .setDuration(110)
      .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(170).start() }
      .start()
  }

  /** Lets the user move the badge off anything it covers; the position is remembered. */
  private inner class DragListener(private val layout: WindowManager.LayoutParams) :
    View.OnTouchListener {
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0

    override fun onTouch(v: View, e: MotionEvent): Boolean {
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = e.rawX
          downY = e.rawY
          startX = layout.x
          startY = layout.y
        }
        MotionEvent.ACTION_MOVE -> {
          // Gravity END: x grows leftwards.
          layout.x = (startX - (e.rawX - downX)).toInt().coerceAtLeast(0)
          layout.y = (startY + (e.rawY - downY)).toInt().coerceAtLeast(0)
          if (v.isAttachedToWindow) windowManager.updateViewLayout(v, layout)
        }
        MotionEvent.ACTION_UP -> {
          store.saveOverlayPosition(layout.x, layout.y)
          v.performClick()
        }
        MotionEvent.ACTION_CANCEL -> store.saveOverlayPosition(layout.x, layout.y)
      }
      return true
    }
  }

  private fun dp(value: Int) = (value * density).toInt()

  private companion object {
    // Brand tokens, mirrored from src/theme/index.ts.
    const val INK = 0xF2141A33.toInt()
    const val PAPER = 0xFFEEF1F6.toInt()
    const val SIGNAL = 0xFFFFB020.toInt()
    const val SIGNAL_EDGE = 0x66FFB020
  }
}
