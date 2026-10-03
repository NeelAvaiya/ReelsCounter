package com.reelscounter.reels

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.reelscounter.reels.detection.InstagramDetectionConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** Counts and status as exposed to React Native. */
data class UsageSnapshot(
  val todayCount: Int,
  val sessionCount: Int,
  val sessionId: Int,
  val lastTransitionAt: Long,
  val trackingEnabled: Boolean,
  val serviceConnected: Boolean,
  val inInstagram: Boolean,
  val inReels: Boolean,
  val debugMode: Boolean,
  val overlayEnabled: Boolean,
)

/**
 * Single source of truth for counts, settings and live tracking status.
 *
 * The accessibility service writes here whether or not the React Native UI is running; the
 * native module reads from here and relays changes to JS. Counts are persisted in
 * SharedPreferences, keyed by local calendar day, so they survive process death and reboots.
 * Only numbers and timestamps are stored, never Instagram content.
 */
class ReelsUsageStore private constructor(context: Context) {

  interface Listener {
    fun onReelTransition(snapshot: UsageSnapshot, timestamp: Long) {}
    fun onStatusChanged(snapshot: UsageSnapshot) {}
    fun onConfigChanged(config: InstagramDetectionConfig) {}
  }

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val listeners = CopyOnWriteArrayList<Listener>()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val diagnostics = ArrayDeque<String>()

  // Live state; only meaningful while the service process is alive.
  @Volatile var serviceConnected = false
    private set
  @Volatile var inInstagram = false
    private set
  @Volatile var inReels = false
    private set

  private var lastReelsSeenAt = prefs.getLong(KEY_LAST_REELS_SEEN, 0L)
  private var lastReelsSeenPersistedAt = 0L

  fun addListener(listener: Listener) = listeners.addIfAbsent(listener)

  fun removeListener(listener: Listener) = listeners.remove(listener)

  // region Settings

  val isTrackingEnabled: Boolean
    get() = prefs.getBoolean(KEY_TRACKING_ENABLED, true)

  fun setTrackingEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
    if (!enabled) updatePresence(inInstagram = false, inReels = false, notify = false)
    notifyStatus()
  }

  val isOverlayEnabled: Boolean
    get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)

  fun setOverlayEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    notifyStatus()
  }

  /** Badge offset from the top-right corner in px, or (-1, -1) for the default spot. */
  fun overlayPosition(): Pair<Int, Int> =
    prefs.getInt(KEY_OVERLAY_X, -1) to prefs.getInt(KEY_OVERLAY_Y, -1)

  fun saveOverlayPosition(x: Int, y: Int) {
    prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
  }

  val isDebugMode: Boolean
    get() = prefs.getBoolean(KEY_DEBUG, false)

  fun setDebugMode(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_DEBUG, enabled).apply()
    if (!enabled) synchronized(diagnostics) { diagnostics.clear() }
    notifyStatus()
  }

  /** Raw JSON override as entered by the user, or "" when using defaults. */
  val detectionOverride: String
    get() = prefs.getString(KEY_DETECTION_OVERRIDE, "") ?: ""

  fun loadDetectionConfig(): InstagramDetectionConfig =
    try {
      InstagramDetectionConfig.fromJson(detectionOverride)
    } catch (e: Exception) {
      // A previously valid override can only fail if defaults changed shape; fall back safely.
      InstagramDetectionConfig.DEFAULT
    }

  /** Validates and saves an override. Blank restores defaults. Throws on invalid JSON/values. */
  fun saveDetectionOverride(json: String) {
    val config = InstagramDetectionConfig.fromJson(json)
    prefs.edit().putString(KEY_DETECTION_OVERRIDE, json.trim()).apply()
    listeners.forEach { it.onConfigChanged(config) }
  }

  // endregion

  // region Counts

  fun todayCount(now: Long = System.currentTimeMillis()): Int = prefs.getInt(dayKey(now), 0)

  fun sessionCount(now: Long = System.currentTimeMillis()): Int =
    if (isSessionExpired(now)) 0 else prefs.getInt(KEY_SESSION_COUNT, 0)

  val sessionId: Int
    get() = prefs.getInt(KEY_SESSION_ID, 0)

  val lastTransitionAt: Long
    get() = prefs.getLong(KEY_LAST_TRANSITION, 0L)

  fun history(days: Int, now: Long = System.currentTimeMillis()): List<Pair<String, Int>> {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    return List(days.coerceIn(1, HISTORY_DAYS)) {
      val date = DAY_FORMAT.get()!!.format(cal.time)
      cal.add(Calendar.DAY_OF_YEAR, -1)
      date to prefs.getInt(DAY_PREFIX + date, 0)
    }
  }

  /** Called whenever the Reels viewer is on screen; starts a new session after a long absence. */
  fun touchReels(now: Long, sessionTimeoutMs: Long) {
    if (now - lastReelsSeenAt > sessionTimeoutMs) {
      prefs
        .edit()
        .putInt(KEY_SESSION_ID, sessionId + 1)
        .putInt(KEY_SESSION_COUNT, 0)
        .putLong(KEY_SESSION_TIMEOUT, sessionTimeoutMs)
        .apply()
    }
    lastReelsSeenAt = now
    // Evaluations happen several times a second; persisting every one is wasteful.
    if (now - lastReelsSeenPersistedAt > 10_000) {
      prefs.edit().putLong(KEY_LAST_REELS_SEEN, now).apply()
      lastReelsSeenPersistedAt = now
    }
  }

  fun recordTransitions(count: Int, now: Long) {
    if (count <= 0) return
    val key = dayKey(now)
    val today = prefs.getInt(key, 0) + count
    val session = prefs.getInt(KEY_SESSION_COUNT, 0) + count
    lastReelsSeenAt = now
    lastReelsSeenPersistedAt = now
    prefs
      .edit()
      .putInt(key, today)
      .putInt(KEY_SESSION_COUNT, session)
      .putLong(KEY_LAST_TRANSITION, now)
      .putLong(KEY_LAST_REELS_SEEN, now)
      .apply()
    pruneOldDays(now)
    val snapshot = snapshot(now)
    listeners.forEach { it.onReelTransition(snapshot, now) }
  }

  fun resetToday(now: Long = System.currentTimeMillis()) {
    prefs.edit().remove(dayKey(now)).putInt(KEY_SESSION_COUNT, 0).apply()
    notifyStatus()
  }

  // endregion

  // region Live status

  fun setServiceConnected(connected: Boolean) {
    serviceConnected = connected
    if (!connected) updatePresence(inInstagram = false, inReels = false, notify = false)
    notifyStatus()
  }

  fun updatePresence(inInstagram: Boolean, inReels: Boolean, notify: Boolean = true) {
    if (this.inInstagram == inInstagram && this.inReels == inReels) return
    this.inInstagram = inInstagram
    this.inReels = inReels
    if (notify) notifyStatus()
  }

  fun snapshot(now: Long = System.currentTimeMillis()) =
    UsageSnapshot(
      todayCount = todayCount(now),
      sessionCount = sessionCount(now),
      sessionId = sessionId,
      lastTransitionAt = lastTransitionAt,
      trackingEnabled = isTrackingEnabled,
      serviceConnected = serviceConnected,
      inInstagram = inInstagram,
      inReels = inReels,
      debugMode = isDebugMode,
      overlayEnabled = isOverlayEnabled,
    )

  // endregion

  // region Diagnostics (in memory only, view ids and decisions, never content)

  fun logDiagnostic(line: String) {
    if (!isDebugMode) return
    val stamped = "${TIME_FORMAT.get()!!.format(Date())} $line"
    synchronized(diagnostics) {
      diagnostics.addLast(stamped)
      while (diagnostics.size > MAX_DIAGNOSTICS) diagnostics.removeFirst()
    }
  }

  fun diagnostics(): List<String> = synchronized(diagnostics) { diagnostics.toList() }

  // endregion

  private fun isSessionExpired(now: Long): Boolean {
    val timeout = prefs.getLong(KEY_SESSION_TIMEOUT, InstagramDetectionConfig.DEFAULT.sessionTimeoutMs)
    return !inReels && lastReelsSeenAt > 0 && now - lastReelsSeenAt > timeout
  }

  private fun notifyStatus() {
    val run = Runnable {
      val snapshot = snapshot()
      listeners.forEach { it.onStatusChanged(snapshot) }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
  }

  private fun pruneOldDays(now: Long) {
    val cutoff = Calendar.getInstance().apply {
      timeInMillis = now
      add(Calendar.DAY_OF_YEAR, -HISTORY_DAYS)
    }
    val cutoffKey = dayKey(cutoff.timeInMillis)
    val stale = prefs.all.keys.filter { it.startsWith(DAY_PREFIX) && it < cutoffKey }
    if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
  }

  companion object {
    private const val PREFS = "reels_tracker"
    private const val DAY_PREFIX = "day_"
    private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    private const val KEY_DEBUG = "debug_mode"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_OVERLAY_X = "overlay_x"
    private const val KEY_OVERLAY_Y = "overlay_y"
    private const val KEY_DETECTION_OVERRIDE = "detection_override"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_SESSION_TIMEOUT = "session_timeout"
    private const val KEY_LAST_TRANSITION = "last_transition_at"
    private const val KEY_LAST_REELS_SEEN = "last_reels_seen_at"
    private const val HISTORY_DAYS = 60
    private const val MAX_DIAGNOSTICS = 200

    // "yyyy-MM-dd" keys sort chronologically as strings, which pruneOldDays relies on.
    // ThreadLocal.withInitial needs API 26; minSdk is 24.
    private val DAY_FORMAT = formatter("yyyy-MM-dd")
    private val TIME_FORMAT = formatter("HH:mm:ss.SSS")

    private fun formatter(pattern: String) =
      object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat(pattern, Locale.US)
      }

    private fun dayKey(time: Long) = DAY_PREFIX + DAY_FORMAT.get()!!.format(Date(time))

    @Volatile private var instance: ReelsUsageStore? = null

    fun get(context: Context): ReelsUsageStore =
      instance ?: synchronized(this) { instance ?: ReelsUsageStore(context).also { instance = it } }
  }
}
