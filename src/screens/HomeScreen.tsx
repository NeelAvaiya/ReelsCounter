import React, { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button } from '../components/Button';
import { Card, Row } from '../components/Card';
import { CounterCard } from '../components/CounterCard';
import { Logo } from '../components/Logo';
import { Screen } from '../components/Screen';
import { TrackingStatus } from '../components/TrackingStatus';
import { Txt } from '../components/Txt';
import { useNow } from '../hooks/useTracker';
import { startTracking, stopTracking } from '../services/tracker';
import { getHistory } from '../storage/usageStorage';
import { useTheme } from '../theme';
import type { DailyCount, TrackerSnapshot } from '../types/tracker';
import { formatClockTime, formatRelativeTime } from '../utils/time';

type Props = {
  snapshot: TrackerSnapshot;
  onOpenSettings: () => void;
};

export function HomeScreen({ snapshot, onOpenSettings }: Props) {
  const theme = useTheme();
  const now = useNow();
  const week = useWeek(snapshot.todayCount);
  const last = snapshot.lastTransitionAt;

  return (
    <Screen
      title="Reels Counter"
      leading={<Logo size={40} />}
      action={{ label: 'Settings', onPress: onOpenSettings }}>
      <TrackingStatus snapshot={snapshot} />

      <CounterCard count={snapshot.todayCount} week={week} />

      <View style={[styles.stats, { borderColor: theme.line }]}>
        <Stat
          label="This session"
          value={String(snapshot.sessionCount)}
          detail={snapshot.inReels ? 'In progress' : 'Ends 2 min after Reels'}
        />
        <View style={[styles.statDivider, { backgroundColor: theme.line }]} />
        <Stat
          label="Last swipe"
          value={formatRelativeTime(last, now)}
          detail={last ? `at ${formatClockTime(last)}` : 'Swipe in Reels to start'}
        />
      </View>

      <Card>
        <Row
          first
          label="In Instagram Reels"
          value={snapshot.inReels && snapshot.serviceConnected ? 'Yes' : 'No'}
        />
        <Row
          label="Accessibility service"
          value={snapshot.serviceEnabled ? 'On' : 'Off'}
        />
        <Row
          label="Count badge on Instagram"
          value={snapshot.overlayEnabled ? 'On' : 'Off'}
        />
      </Card>

      <Button
        title={snapshot.trackingEnabled ? 'Pause counting' : 'Resume counting'}
        variant={snapshot.trackingEnabled ? 'outline' : 'primary'}
        onPress={() =>
          snapshot.trackingEnabled ? stopTracking() : startTracking()
        }
      />

      <Txt variant="caption" muted style={styles.footnote}>
        Only swipes between Reels count. Feed, Stories, profiles, search and
        comments are ignored. Your counts never leave this phone.
      </Txt>
    </Screen>
  );
}

function Stat({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <View style={styles.stat} accessible accessibilityLabel={`${label}: ${value}`}>
      <Txt variant="bodyStrong" muted>
        {label}
      </Txt>
      <Txt variant="stat" numberOfLines={1} adjustsFontSizeToFit>
        {value}
      </Txt>
      <Txt variant="caption" muted numberOfLines={1}>
        {detail}
      </Txt>
    </View>
  );
}

/** Last 7 days for the strip under the counter; re-read whenever today's count moves. */
function useWeek(todayCount: number) {
  const [week, setWeek] = useState<DailyCount[]>([]);
  useEffect(() => {
    let alive = true;
    getHistory(7)
      .then(days => alive && setWeek(days))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [todayCount]);
  return week;
}

const styles = StyleSheet.create({
  stats: {
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderBottomWidth: StyleSheet.hairlineWidth,
    paddingVertical: 16,
    marginVertical: 4,
  },
  stat: {
    flex: 1,
    gap: 2,
    paddingHorizontal: 4,
  },
  statDivider: {
    width: StyleSheet.hairlineWidth,
    marginHorizontal: 12,
  },
  footnote: {
    marginTop: 4,
    paddingHorizontal: 4,
  },
});
