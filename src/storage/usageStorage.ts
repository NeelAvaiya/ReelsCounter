import NativeReelsTracker from '../specs/NativeReelsTracker';
import type { DailyCount } from '../types/tracker';

/**
 * Persisted usage data. The accessibility service keeps counting while the UI is closed, so the
 * source of truth is native SharedPreferences (ReelsUsageStore.kt); this module is the JS view
 * of it. Only daily counts and timestamps are stored, on device.
 */

export const getHistory = (days: number): Promise<DailyCount[]> =>
  NativeReelsTracker.getHistory(days);

export const resetTodayCount = () => NativeReelsTracker.resetTodayCount();

/** User's detection override JSON, or '' when the built-in selectors are used. */
export const getDetectionOverride = () => NativeReelsTracker.getDetectionConfig();

export const getDefaultDetectionConfig = () =>
  NativeReelsTracker.getDefaultDetectionConfig();

/** Validated natively; rejects with a readable message if the JSON or a value is invalid. */
export const saveDetectionOverride = (json: string) =>
  NativeReelsTracker.setDetectionConfig(json);
