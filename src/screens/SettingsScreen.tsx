import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  View,
} from 'react-native';
import { Button } from '../components/Button';
import { Card, Row } from '../components/Card';
import { Screen, SectionTitle } from '../components/Screen';
import { Txt } from '../components/Txt';
import {
  getDiagnostics,
  openAccessibilitySettings,
  setDebugMode,
  setOverlayEnabled,
  startTracking,
  stopTracking,
} from '../services/tracker';
import {
  getDefaultDetectionConfig,
  getDetectionOverride,
  getHistory,
  resetTodayCount,
  saveDetectionOverride,
} from '../storage/usageStorage';
import { useTheme } from '../theme';
import type { DailyCount, TrackerSnapshot } from '../types/tracker';

type Props = {
  snapshot: TrackerSnapshot;
  onBack: () => void;
};

export function SettingsScreen({ snapshot, onBack }: Props) {
  const [history, setHistory] = useState<DailyCount[]>([]);

  useEffect(() => {
    getHistory(7).then(setHistory).catch(() => setHistory([]));
  }, [snapshot.todayCount]);

  const confirmReset = () =>
    Alert.alert(
      "Reset today's count?",
      'Today and the current session go back to 0. Earlier days are kept.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Reset', style: 'destructive', onPress: resetTodayCount },
      ],
    );

  return (
    <Screen title="Settings" action={{ label: 'Done', onPress: onBack }}>
      <SectionTitle>Counting</SectionTitle>
      <Card>
        <Row
          first
          label="Count Reel swipes"
          detail="Pause without turning the service off."
          right={
            <Toggle
              value={snapshot.trackingEnabled}
              onChange={on => (on ? startTracking() : stopTracking())}
              label="Count Reel swipes"
            />
          }
        />
        <Row
          label="Count badge on Instagram"
          detail="A small tag with today's count. Drag it to move it."
          right={
            <Toggle
              value={snapshot.overlayEnabled}
              onChange={setOverlayEnabled}
              label="Count badge on Instagram"
            />
          }
        />
        <Row
          label="Accessibility service"
          detail={
            snapshot.serviceEnabled && !snapshot.serviceConnected
              ? 'On, starting'
              : undefined
          }
          value={snapshot.serviceEnabled ? 'On' : 'Off'}
        />
      </Card>
      <Button
        title="Open Accessibility settings"
        variant="outline"
        onPress={openAccessibilitySettings}
      />

      <SectionTitle>Last 7 days</SectionTitle>
      <HistoryCard history={history} />
      <Button title="Reset today's count" variant="danger" onPress={confirmReset} />

      <SectionTitle>Troubleshooting</SectionTitle>
      <DiagnosticsCard enabled={snapshot.debugMode} />

      <SectionTitle>Detection rules</SectionTitle>
      <DetectionConfigCard />
    </Screen>
  );
}

function HistoryCard({ history }: { history: DailyCount[] }) {
  const theme = useTheme();
  const max = Math.max(1, ...history.map(d => d.count));
  return (
    <Card>
      {history.map((day, i) => (
        <View
          key={day.date}
          style={[
            styles.historyRow,
            i > 0 && {
              borderTopColor: theme.line,
              borderTopWidth: StyleSheet.hairlineWidth,
            },
          ]}>
          <Txt variant="body" muted={i > 0} style={styles.historyDay}>
            {i === 0 ? 'Today' : formatDay(day.date)}
          </Txt>
          <View style={styles.historyTrack}>
            <View
              style={[
                styles.historyBar,
                {
                  width: `${(day.count / max) * 100}%`,
                  backgroundColor: i === 0 ? theme.signal : theme.line,
                },
              ]}
            />
          </View>
          <Txt variant="bodyStrong" style={styles.historyCount}>
            {day.count}
          </Txt>
        </View>
      ))}
    </Card>
  );
}

function DiagnosticsCard({ enabled }: { enabled: boolean }) {
  const theme = useTheme();
  const [lines, setLines] = useState<string[]>([]);

  const load = useCallback(() => {
    getDiagnostics().then(setLines).catch(() => setLines([]));
  }, []);

  useEffect(() => {
    if (enabled) {
      load();
    } else {
      setLines([]);
    }
  }, [enabled, load]);

  return (
    <Card style={enabled && styles.cardBottom}>
      <Row
        first
        label="Detection log"
        detail="Records what the counter decided and which Instagram view ids it saw. Never any text."
        right={
          <Toggle value={enabled} onChange={setDebugMode} label="Detection log" />
        }
      />
      {enabled ? (
        <>
          <ScrollView
            nestedScrollEnabled
            style={[styles.log, { backgroundColor: theme.input }]}>
            <Txt variant="caption" selectable style={styles.mono}>
              {lines.length
                ? lines.slice(-60).join('\n')
                : 'Nothing logged yet. Open Reels in Instagram, then refresh.'}
            </Txt>
          </ScrollView>
          <Button title="Refresh log" variant="outline" onPress={load} />
        </>
      ) : null}
    </Card>
  );
}

function DetectionConfigCard() {
  const theme = useTheme();
  const [text, setText] = useState('');
  const [message, setMessage] = useState<{ error: boolean; text: string } | null>(
    null,
  );

  useEffect(() => {
    getDetectionOverride().then(setText).catch(() => setText(''));
  }, []);

  const save = async (json: string) => {
    try {
      await saveDetectionOverride(json);
      setText(json);
      setMessage({
        error: false,
        text: json.trim() ? 'Rules saved and applied.' : 'Using the built-in rules.',
      });
    } catch (e) {
      setMessage({
        error: true,
        text: e instanceof Error ? e.message : String(e),
      });
    }
  };

  return (
    <Card style={styles.cardPadded}>
      <Txt variant="body" muted>
        If Instagram renames its screens and counting stops, paste updated view
        ids here as JSON. Keys you leave out keep their built-in values.
      </Txt>
      <TextInput
        value={text}
        onChangeText={setText}
        multiline
        autoCapitalize="none"
        autoCorrect={false}
        placeholder='{"reelsViewerIds": ["clips_viewer_view_pager"]}'
        placeholderTextColor={theme.muted}
        accessibilityLabel="Detection rules JSON"
        style={[
          styles.input,
          styles.mono,
          { color: theme.text, borderColor: theme.line, backgroundColor: theme.input },
        ]}
      />
      {message ? (
        <Txt variant="caption" color={message.error ? theme.danger : theme.positive}>
          {message.text}
        </Txt>
      ) : null}
      <View style={styles.buttons}>
        <View style={styles.flex}>
          <Button title="Save rules" onPress={() => save(text)} />
        </View>
        <View style={styles.flex}>
          <Button
            title="Show built-in"
            variant="outline"
            onPress={() =>
              getDefaultDetectionConfig().then(json => {
                setText(json);
                setMessage(null);
              })
            }
          />
        </View>
      </View>
      <Button title="Use built-in rules" variant="danger" onPress={() => save('')} />
    </Card>
  );
}

function Toggle(props: {
  value: boolean;
  onChange: (value: boolean) => void;
  label: string;
}) {
  const theme = useTheme();
  return (
    <Switch
      accessibilityLabel={props.label}
      value={props.value}
      onValueChange={props.onChange}
      trackColor={{ true: theme.signal, false: theme.line }}
      thumbColor={props.value ? theme.panel : '#FFFFFF'}
    />
  );
}

function formatDay(isoDate: string) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
  });
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  cardBottom: {
    paddingBottom: 16,
  },
  cardPadded: {
    paddingVertical: 16,
    gap: 12,
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
  },
  historyDay: {
    width: 72,
  },
  historyTrack: {
    flex: 1,
    height: 8,
    justifyContent: 'center',
  },
  historyBar: {
    height: 8,
    borderRadius: 4,
    minWidth: 4,
  },
  historyCount: {
    minWidth: 36,
    textAlign: 'right',
    fontVariant: ['tabular-nums'],
  },
  log: {
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
    maxHeight: 320,
  },
  mono: {
    fontFamily: 'monospace',
    fontSize: 12,
    lineHeight: 17,
  },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    minHeight: 140,
    maxHeight: 360,
    textAlignVertical: 'top',
  },
  buttons: {
    flexDirection: 'row',
    gap: 12,
  },
});
