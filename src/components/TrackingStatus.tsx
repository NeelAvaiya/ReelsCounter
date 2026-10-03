import React from 'react';
import { StyleSheet, View } from 'react-native';
import { useTheme } from '../theme';
import { isTrackingActive, type TrackerSnapshot } from '../types/tracker';
import { Txt } from './Txt';

/** One line under the title: a status dot, Active/Inactive, and what that means right now. */
export function TrackingStatus({ snapshot }: { snapshot: TrackerSnapshot }) {
  const theme = useTheme();
  const active = isTrackingActive(snapshot);
  const counting = active && snapshot.inReels;

  const detail = !snapshot.serviceEnabled
    ? 'Accessibility service is off'
    : !snapshot.trackingEnabled
    ? 'Paused'
    : !snapshot.serviceConnected
    ? 'Starting the service'
    : counting
    ? 'Counting your swipes in Reels'
    : 'Open Reels in Instagram to start';

  const dot = counting ? theme.positive : active ? theme.signal : theme.danger;

  return (
    <View
      style={styles.row}
      accessible
      accessibilityLabel={`Tracking ${active ? 'active' : 'inactive'}. ${detail}`}>
      <View style={[styles.dot, { backgroundColor: dot }]} />
      <Txt variant="bodyStrong">{active ? 'Active' : 'Inactive'}</Txt>
      <Txt variant="body" muted style={styles.detail} numberOfLines={1}>
        {detail}
      </Txt>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: -4,
    marginBottom: 4,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  detail: {
    flex: 1,
  },
});
