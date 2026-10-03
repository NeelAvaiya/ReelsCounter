import React from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme';
import { Txt } from './Txt';

type Props = {
  title: string;
  /** Rendered to the left of the title, e.g. the app mark. */
  leading?: React.ReactNode;
  action?: { label: string; onPress: () => void };
  children: React.ReactNode;
};

/** Scrollable page with a condensed title and an optional header action. */
export function Screen({ title, leading, action, children }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={[
        styles.content,
        { paddingTop: insets.top + 12, paddingBottom: insets.bottom + 32 },
      ]}
      keyboardShouldPersistTaps="handled">
      <View style={styles.header}>
        {leading}
        <Txt variant="title" style={styles.title} accessibilityRole="header">
          {title}
        </Txt>
        {action ? (
          <Pressable
            accessibilityRole="button"
            onPress={action.onPress}
            hitSlop={12}
            style={({ pressed }) => [
              styles.action,
              { borderColor: theme.line, opacity: pressed ? 0.7 : 1 },
            ]}>
            <Txt variant="bodyStrong">{action.label}</Txt>
          </Pressable>
        ) : null}
      </View>
      {children}
    </ScrollView>
  );
}

/** Heading for a group of settings. Sentence case, no decoration. */
export function SectionTitle({ children }: { children: string }) {
  return (
    <Txt variant="heading" style={styles.section}>
      {children}
    </Txt>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 16,
    gap: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 4,
    minHeight: 48,
  },
  title: {
    flex: 1,
  },
  action: {
    borderWidth: 1.5,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  section: {
    marginTop: 16,
    marginLeft: 4,
  },
});
