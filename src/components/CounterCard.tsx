import React, { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, StyleSheet, View } from 'react-native';
import { fonts, useTheme } from '../theme';
import type { DailyCount } from '../types/tracker';
import { Txt } from './Txt';

type Props = {
  count: number;
  /** Most recent first, today included. */
  week: DailyCount[];
};

const MIN_DIGITS = 3;

/**
 * Today's count as a hand tally counter: one cell per digit, unused leading digits dimmed.
 * Changed digits roll in, which is the app's one deliberate motion.
 */
export function CounterCard({ count, week }: Props) {
  const theme = useTheme();
  const digits = String(Math.max(0, count)).padStart(MIN_DIGITS, '0').split('');
  const leading = digits.length - String(Math.max(0, count)).length;

  return (
    <View
      style={[styles.panel, { backgroundColor: theme.panel }]}
      accessible
      accessibilityLabel={`${count} Reels swiped today`}>
      <Txt variant="bodyStrong" color={theme.panelMuted}>
        Today
      </Txt>
      <View style={styles.digits}>
        {digits.map((d, i) => (
          <DigitCell
            // Keyed from the right so the ones digit keeps its cell as the number grows.
            key={digits.length - i}
            digit={d}
            dim={i < leading}
          />
        ))}
      </View>
      <Txt variant="body" color={theme.panelText}>
        {count === 1 ? 'Reel swiped' : 'Reels swiped'}
      </Txt>
      {week.length > 1 ? <WeekStrip week={week} /> : null}
    </View>
  );
}

function DigitCell({ digit, dim }: { digit: string; dim: boolean }) {
  const theme = useTheme();
  const roll = useRef(new Animated.Value(1)).current;
  const previous = useRef(digit);
  const reduceMotion = useReduceMotion();

  useEffect(() => {
    if (previous.current === digit) {
      return;
    }
    previous.current = digit;
    if (reduceMotion) {
      return;
    }
    roll.setValue(0);
    Animated.timing(roll, {
      toValue: 1,
      duration: 220,
      useNativeDriver: true,
    }).start();
  }, [digit, reduceMotion, roll]);

  return (
    <View style={[styles.cell, { backgroundColor: theme.panelCell }]}>
      <Animated.Text
        style={[
          styles.digit,
          dim && styles.dim,
          {
            color: dim ? theme.panelMuted : theme.signal,
            opacity: dim ? undefined : roll,
            transform: [
              {
                translateY: roll.interpolate({
                  inputRange: [0, 1],
                  outputRange: [-18, 0],
                }),
              },
            ],
          },
        ]}>
        {digit}
      </Animated.Text>
      <View style={[styles.seam, { backgroundColor: theme.panel }]} />
    </View>
  );
}

function WeekStrip({ week }: { week: DailyCount[] }) {
  const theme = useTheme();
  const days = [...week].slice(0, 7).reverse(); // oldest -> today
  const max = Math.max(1, ...days.map(d => d.count));
  return (
    <View style={styles.week}>
      {days.map((d, i) => {
        const isToday = i === days.length - 1;
        return (
          <View key={d.date} style={styles.weekDay}>
            <View style={styles.weekTrack}>
              <View
                style={[
                  styles.weekBar,
                  !isToday && styles.dim,
                  {
                    height: `${Math.max(6, (d.count / max) * 100)}%`,
                    backgroundColor: isToday ? theme.signal : theme.panelMuted,
                  },
                ]}
              />
            </View>
            <Txt
              variant="caption"
              color={isToday ? theme.panelText : theme.panelMuted}>
              {dayInitial(d.date)}
            </Txt>
          </View>
        );
      })}
    </View>
  );
}

function dayInitial(isoDate: string) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return 'SMTWTFS'[new Date(y, m - 1, d).getDay()];
}

function useReduceMotion() {
  const [reduce, setReduce] = useState(false);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled()
      .then(setReduce)
      .catch(() => {});
    const sub = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setReduce,
    );
    return () => sub.remove();
  }, []);
  return reduce;
}

const styles = StyleSheet.create({
  panel: {
    borderRadius: 24,
    padding: 20,
    paddingBottom: 18,
  },
  digits: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
    marginBottom: 10,
  },
  cell: {
    flex: 1,
    maxWidth: 68,
    aspectRatio: 0.72,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  digit: {
    fontFamily: fonts.condensedBold,
    fontSize: 64,
    lineHeight: 72,
    includeFontPadding: false,
  },
  dim: {
    opacity: 0.4,
  },
  // The horizontal split of a mechanical counter wheel.
  seam: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: '50%',
    height: 2,
    opacity: 0.6,
  },
  week: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 18,
    gap: 6,
  },
  weekDay: {
    flex: 1,
    alignItems: 'center',
    gap: 4,
  },
  weekTrack: {
    height: 36,
    width: '100%',
    maxWidth: 18,
    justifyContent: 'flex-end',
  },
  weekBar: {
    width: '100%',
    borderRadius: 4,
  },
});
