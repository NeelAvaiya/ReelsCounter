package com.reelscounter.reels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.reelscounter.reels.detection.InstagramDetectionConfig

/**
 * TurboModule bridging [ReelsUsageStore] and the accessibility service to JavaScript.
 * Spec: src/specs/NativeReelsTracker.ts (NativeReelsTrackerSpec is generated from it).
 */
class ReelsTrackerModule(reactContext: ReactApplicationContext) :
  NativeReelsTrackerSpec(reactContext) {

  private val store = ReelsUsageStore.get(reactContext)

  private val storeListener =
    object : ReelsUsageStore.Listener {
      override fun onReelTransition(snapshot: UsageSnapshot, timestamp: Long) {
        emit(
          EVENT_REEL_SCROLL,
          Arguments.createMap().apply {
            putInt("count", snapshot.todayCount)
            putInt("sessionCount", snapshot.sessionCount)
            putDouble("timestamp", timestamp.toDouble())
          },
        )
      }

      override fun onStatusChanged(snapshot: UsageSnapshot) {
        emit(EVENT_STATUS_CHANGED, snapshot.toMap())
      }
    }

  override fun getName() = NAME

  override fun initialize() {
    super.initialize()
    store.addListener(storeListener)
  }

  override fun invalidate() {
    store.removeListener(storeListener)
    super.invalidate()
  }

  override fun startTracking(promise: Promise) {
    store.setTrackingEnabled(true)
    promise.resolve(null)
  }

  override fun stopTracking(promise: Promise) {
    store.setTrackingEnabled(false)
    promise.resolve(null)
  }

  override fun isAccessibilityServiceEnabled(promise: Promise) {
    promise.resolve(isServiceEnabled())
  }

  override fun getTodayCount(promise: Promise) {
    promise.resolve(store.todayCount())
  }

  override fun getSessionCount(promise: Promise) {
    promise.resolve(store.sessionCount())
  }

  /** "Active" = user has tracking on, the service is enabled in settings and bound. */
  override fun getTrackingStatus(promise: Promise) {
    promise.resolve(store.isTrackingEnabled && store.serviceConnected && isServiceEnabled())
  }

  override fun getSnapshot(promise: Promise) {
    promise.resolve(store.snapshot().toMap())
  }

  override fun getHistory(days: Double, promise: Promise) {
    val array = Arguments.createArray()
    store.history(days.toInt()).forEach { (date, count) ->
      array.pushMap(
        Arguments.createMap().apply {
          putString("date", date)
          putInt("count", count)
        }
      )
    }
    promise.resolve(array)
  }

  override fun resetTodayCount(promise: Promise) {
    store.resetToday()
    promise.resolve(null)
  }

  override fun openAccessibilitySettings(promise: Promise) {
    val component = serviceComponent().flattenToString()
    // The ":settings:" extras ask Settings to scroll to/highlight our entry; ignored where unsupported.
    val intent =
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(":settings:fragment_args_key", component)
        putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", component) })
      }
    startActivity(intent, promise)
  }

  override fun openAppSettings(promise: Promise) {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", reactApplicationContext.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent, promise)
  }

  override fun getDetectionConfig(promise: Promise) {
    promise.resolve(store.detectionOverride)
  }

  override fun getDefaultDetectionConfig(promise: Promise) {
    promise.resolve(InstagramDetectionConfig.DEFAULT.toJson().toString(2))
  }

  override fun setDetectionConfig(json: String, promise: Promise) {
    try {
      store.saveDetectionOverride(json)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("E_INVALID_CONFIG", e.message ?: "Invalid detection config", e)
    }
  }

  override fun setDebugMode(enabled: Boolean, promise: Promise) {
    store.setDebugMode(enabled)
    promise.resolve(null)
  }

  override fun setOverlayEnabled(enabled: Boolean, promise: Promise) {
    store.setOverlayEnabled(enabled)
    promise.resolve(null)
  }

  override fun getDiagnostics(promise: Promise) {
    promise.resolve(Arguments.fromList(store.diagnostics()))
  }

  override fun addListener(eventName: String) = Unit

  override fun removeListeners(count: Double) = Unit

  private fun startActivity(intent: Intent, promise: Promise) {
    try {
      reactApplicationContext.startActivity(intent)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("E_OPEN_SETTINGS", e.message ?: "Could not open settings", e)
    }
  }

  private fun emit(event: String, payload: WritableMap) {
    if (reactApplicationContext.hasActiveReactInstance()) {
      reactApplicationContext.emitDeviceEvent(event, payload)
    }
  }

  private fun serviceComponent() =
    ComponentName(reactApplicationContext, InstagramReelsAccessibilityService::class.java)

  private fun isServiceEnabled(): Boolean = isServiceEnabled(reactApplicationContext, serviceComponent())

  private fun UsageSnapshot.toMap(): WritableMap =
    Arguments.createMap().apply {
      putInt("todayCount", todayCount)
      putInt("sessionCount", sessionCount)
      putInt("sessionId", sessionId)
      putDouble("lastTransitionAt", lastTransitionAt.toDouble())
      putBoolean("trackingEnabled", trackingEnabled)
      putBoolean("serviceEnabled", isServiceEnabled())
      putBoolean("serviceConnected", serviceConnected)
      putBoolean("inInstagram", inInstagram)
      putBoolean("inReels", inReels)
      putBoolean("debugMode", debugMode)
      putBoolean("overlayEnabled", overlayEnabled)
    }

  companion object {
    const val NAME = "ReelsTracker"
    const val EVENT_REEL_SCROLL = "reelScrollDetected"
    const val EVENT_STATUS_CHANGED = "trackingStatusChanged"

    fun isServiceEnabled(context: Context, component: ComponentName): Boolean {
      val enabled =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
          ?: return false
      val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
      return splitter.any { ComponentName.unflattenFromString(it) == component }
    }
  }
}
