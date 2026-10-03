import { NativeEventEmitter } from 'react-native';
import NativeReelsTracker from '../specs/NativeReelsTracker';
import {
  TrackerEvents,
  type ReelScrollEvent,
  type TrackingStatusEvent,
} from '../types/tracker';

/**
 * Control surface for the native Reels tracker. Everything Android-specific stays behind the
 * ReelsTracker TurboModule; screens only talk to this module and to storage/usageStorage.
 */

const emitter = new NativeEventEmitter(NativeReelsTracker);

export const startTracking = () => NativeReelsTracker.startTracking();
export const stopTracking = () => NativeReelsTracker.stopTracking();
export const isAccessibilityServiceEnabled = () =>
  NativeReelsTracker.isAccessibilityServiceEnabled();
export const getTodayCount = () => NativeReelsTracker.getTodayCount();
export const getSessionCount = () => NativeReelsTracker.getSessionCount();
export const getTrackingStatus = () => NativeReelsTracker.getTrackingStatus();
export const getSnapshot = () => NativeReelsTracker.getSnapshot();
export const openAccessibilitySettings = () =>
  NativeReelsTracker.openAccessibilitySettings();
export const openAppSettings = () => NativeReelsTracker.openAppSettings();
export const setDebugMode = (enabled: boolean) =>
  NativeReelsTracker.setDebugMode(enabled);
export const setOverlayEnabled = (enabled: boolean) =>
  NativeReelsTracker.setOverlayEnabled(enabled);
export const getDiagnostics = () => NativeReelsTracker.getDiagnostics();

export function onReelScrollDetected(listener: (e: ReelScrollEvent) => void) {
  const sub = emitter.addListener(TrackerEvents.reelScrollDetected, e =>
    listener(e as ReelScrollEvent),
  );
  return () => sub.remove();
}

export function onTrackingStatusChanged(
  listener: (e: TrackingStatusEvent) => void,
) {
  const sub = emitter.addListener(TrackerEvents.trackingStatusChanged, e =>
    listener(e as TrackingStatusEvent),
  );
  return () => sub.remove();
}
