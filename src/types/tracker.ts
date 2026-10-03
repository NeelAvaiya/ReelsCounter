import type {
  NativeDailyCount,
  NativeTrackerSnapshot,
} from '../specs/NativeReelsTracker';

export type TrackerSnapshot = NativeTrackerSnapshot;
export type DailyCount = NativeDailyCount;

/** Payload of the native `reelScrollDetected` event. */
export type ReelScrollEvent = {
  /** Today's total after this transition. */
  count: number;
  sessionCount: number;
  /** Epoch millis. */
  timestamp: number;
};

/** Payload of the native `trackingStatusChanged` event (a full snapshot). */
export type TrackingStatusEvent = TrackerSnapshot;

export const TrackerEvents = {
  reelScrollDetected: 'reelScrollDetected',
  trackingStatusChanged: 'trackingStatusChanged',
} as const;

/** "Active" means the counter is actually able to count right now. */
export function isTrackingActive(s: TrackerSnapshot): boolean {
  return s.trackingEnabled && s.serviceEnabled && s.serviceConnected;
}
