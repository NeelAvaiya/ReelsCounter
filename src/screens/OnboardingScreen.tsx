import React from 'react';
import { Platform, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Logo } from '../components/Logo';
import { SectionTitle } from '../components/Screen';
import { Txt } from '../components/Txt';
import {
  openAccessibilitySettings,
  openAppSettings,
} from '../services/tracker';
import { useTheme } from '../theme';

/**
 * Prominent disclosure shown before the user enables the accessibility service (and whenever
 * it is disabled). Android cannot enable the service programmatically.
 */
export function OnboardingScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const restrictedSettings =
    Platform.OS === 'android' && Number(Platform.Version) >= 33;

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={[
        styles.content,
        { paddingTop: insets.top + 32, paddingBottom: insets.bottom + 32 },
      ]}>
      <Logo size={72} />
      <Txt variant="title" accessibilityRole="header" style={styles.title}>
        Enable Reel tracking
      </Txt>
      <Txt variant="body" style={styles.lead}>
        To count your Instagram Reel swipes, Reels Counter needs Android
        Accessibility access. Here is exactly what that access is used for.
      </Txt>

      <Card>
        <Point mark="✓" color={theme.positive} first>
          Recognises when the Instagram Reels viewer is on screen.
        </Point>
        <Point mark="✓" color={theme.positive}>
          Adds 1 when you swipe to a different Reel.
        </Point>
        <Point mark="✓" color={theme.positive}>
          Tells Reels apart with a short-lived fingerprint of the visible
          author and caption, kept in memory only.
        </Point>
        <Point mark="✓" color={theme.positive}>
          Shows a small count badge while Instagram is open. You can turn it
          off.
        </Point>
      </Card>

      <Card>
        <Point mark="✕" color={theme.danger} first>
          Never saves or sends Instagram content, messages or account data.
        </Point>
        <Point mark="✕" color={theme.danger}>
          Never looks at apps other than Instagram.
        </Point>
        <Point mark="✕" color={theme.danger}>
          Never taps or types for you, and never uses the internet.
        </Point>
      </Card>

      <SectionTitle>How to turn it on</SectionTitle>
      <Card>
        <Step n={1} first>
          Tap Open Accessibility settings.
        </Step>
        <Step n={2}>
          Find Reels Counter, often under Installed or Downloaded apps.
        </Step>
        <Step n={3}>Turn it on, confirm, then come back here.</Step>
      </Card>

      <Button
        title="Open Accessibility settings"
        onPress={openAccessibilitySettings}
      />

      {restrictedSettings ? (
        <Card style={styles.help}>
          <Txt variant="heading">Switch greyed out?</Txt>
          <Txt variant="body" muted>
            Android 13 and later blocks this for apps installed outside the Play
            Store. Open App info, tap the menu in the top corner, choose Allow
            restricted settings, then try again.
          </Txt>
          <Button
            title="Open App info"
            variant="outline"
            onPress={openAppSettings}
          />
        </Card>
      ) : null}

      <Txt variant="caption" muted style={styles.note}>
        Turning the service on means you agree to this use. You can turn it off
        any time in Android Settings, under Accessibility.
      </Txt>
    </ScrollView>
  );
}

function Point({
  mark,
  color,
  first,
  children,
}: {
  mark: string;
  color: string;
  first?: boolean;
  children: React.ReactNode;
}) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.line,
        !first && {
          borderTopColor: theme.line,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
      ]}>
      <Txt variant="bodyStrong" color={color} style={styles.mark}>
        {mark}
      </Txt>
      <Txt variant="body" style={styles.flex}>
        {children}
      </Txt>
    </View>
  );
}

function Step({
  n,
  first,
  children,
}: {
  n: number;
  first?: boolean;
  children: React.ReactNode;
}) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.line,
        !first && {
          borderTopColor: theme.line,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
      ]}>
      <View style={[styles.stepBadge, { backgroundColor: theme.panel }]}>
        <Txt variant="caption" color={theme.signal}>
          {n}
        </Txt>
      </View>
      <Txt variant="body" style={styles.flex}>
        {children}
      </Txt>
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 16,
    gap: 12,
  },
  title: {
    marginTop: 12,
  },
  lead: {
    fontSize: 17,
    lineHeight: 25,
    marginBottom: 8,
  },
  line: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    paddingVertical: 12,
  },
  mark: {
    width: 16,
    textAlign: 'center',
  },
  flex: {
    flex: 1,
  },
  stepBadge: {
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: -1,
  },
  help: {
    paddingVertical: 16,
    gap: 8,
  },
  note: {
    textAlign: 'center',
    paddingHorizontal: 8,
  },
});
