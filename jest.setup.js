/* global jest */
jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);

jest.mock('./src/specs/NativeReelsTracker', () => {
  const snapshot = {
    todayCount: 0,
    sessionCount: 0,
    sessionId: 1,
    lastTransitionAt: 0,
    trackingEnabled: true,
    serviceEnabled: true,
    serviceConnected: true,
    inInstagram: false,
    inReels: false,
    debugMode: false,
    overlayEnabled: true,
  };
  return {
    __esModule: true,
    __snapshot: snapshot,
    default: {
      startTracking: jest.fn(() => Promise.resolve()),
      stopTracking: jest.fn(() => Promise.resolve()),
      isAccessibilityServiceEnabled: jest.fn(() => Promise.resolve(true)),
      getTodayCount: jest.fn(() => Promise.resolve(snapshot.todayCount)),
      getSessionCount: jest.fn(() => Promise.resolve(snapshot.sessionCount)),
      getTrackingStatus: jest.fn(() => Promise.resolve(true)),
      getSnapshot: jest.fn(() => Promise.resolve({ ...snapshot })),
      getHistory: jest.fn(() => Promise.resolve([])),
      resetTodayCount: jest.fn(() => Promise.resolve()),
      openAccessibilitySettings: jest.fn(() => Promise.resolve()),
      openAppSettings: jest.fn(() => Promise.resolve()),
      getDetectionConfig: jest.fn(() => Promise.resolve('')),
      getDefaultDetectionConfig: jest.fn(() => Promise.resolve('{}')),
      setDetectionConfig: jest.fn(() => Promise.resolve()),
      setDebugMode: jest.fn(() => Promise.resolve()),
      setOverlayEnabled: jest.fn(() => Promise.resolve()),
      getDiagnostics: jest.fn(() => Promise.resolve([])),
      addListener: jest.fn(),
      removeListeners: jest.fn(),
    },
  };
});
