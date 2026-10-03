import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

/**
 * Codegen spec for the native ReelsTracker TurboModule (android/.../reels/ReelsTrackerModule.kt).
 * App code should use src/services/tracker.ts rather than importing this directly.
 */

export type NativeTrackerSnapshot = {
  todayCount: number;
  sessionCount: number;
  sessionId: number;
  /** Epoch millis of the last counted transition, 0 if none yet. */
  lastTransitionAt: number;
  /** User-controlled pause/resume switch. */
  trackingEnabled: boolean;
  /** Enabled in Android Accessibility settings. */
  serviceEnabled: boolean;
  /** Service process is bound and receiving events. */
  serviceConnected: boolean;
  inInstagram: boolean;
  inReels: boolean;
  debugMode: boolean;
  /** Floating count badge shown over Instagram. */
  overlayEnabled: boolean;
};

export type NativeDailyCount = {
  date: string;
  count: number;
};

export interface Spec extends TurboModule {
  startTracking(): Promise<void>;
  stopTracking(): Promise<void>;
  isAccessibilityServiceEnabled(): Promise<boolean>;
  getTodayCount(): Promise<number>;
  getSessionCount(): Promise<number>;
  getTrackingStatus(): Promise<boolean>;
  getSnapshot(): Promise<NativeTrackerSnapshot>;
  getHistory(days: number): Promise<NativeDailyCount[]>;
  resetTodayCount(): Promise<void>;

  openAccessibilitySettings(): Promise<void>;
  /** App info screen; needed for "Allow restricted settings" on Android 13+ sideloads. */
  openAppSettings(): Promise<void>;

  getDetectionConfig(): Promise<string>;
  getDefaultDetectionConfig(): Promise<string>;
  /** Partial JSON override; empty string restores defaults. Rejects on invalid input. */
  setDetectionConfig(json: string): Promise<void>;
  setDebugMode(enabled: boolean): Promise<void>;
  setOverlayEnabled(enabled: boolean): Promise<void>;
  getDiagnostics(): Promise<string[]>;

  // Required by NativeEventEmitter.
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('ReelsTracker');
