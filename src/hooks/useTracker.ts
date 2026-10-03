import { useCallback, useEffect, useState } from 'react';
import { AppState } from 'react-native';
import {
  getSnapshot,
  onReelScrollDetected,
  onTrackingStatusChanged,
} from '../services/tracker';
import type { TrackerSnapshot } from '../types/tracker';

/**
 * Live tracker state. Loads a snapshot, applies native events as they arrive, and re-reads on
 * returning to the foreground (the user may have just toggled the service in Settings, or the
 * day may have rolled over).
 */
export function useTracker() {
  const [snapshot, setSnapshot] = useState<TrackerSnapshot | null>(null);

  const refresh = useCallback(async () => {
    try {
      setSnapshot(await getSnapshot());
    } catch (e) {
      console.warn('ReelsTracker: failed to read snapshot', e);
    }
  }, []);

  useEffect(() => {
    refresh();
    const offScroll = onReelScrollDetected(e =>
      setSnapshot(prev =>
        prev
          ? {
              ...prev,
              todayCount: e.count,
              sessionCount: e.sessionCount,
              lastTransitionAt: e.timestamp,
            }
          : prev,
      ),
    );
    const offStatus = onTrackingStatusChanged(setSnapshot);
    const appState = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refresh();
      }
    });
    return () => {
      offScroll();
      offStatus();
      appState.remove();
    };
  }, [refresh]);

  return { snapshot, refresh };
}

/** Re-renders every `intervalMs` so relative times ("2 min ago") stay current. */
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
