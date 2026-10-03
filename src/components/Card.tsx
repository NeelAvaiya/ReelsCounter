import React from 'react';
import { StyleSheet, View, type ViewProps } from 'react-native';
import { useTheme } from '../theme';
import { Txt } from './Txt';

/** A grouped surface for related rows (settings, history). */
export function Card({ style, ...props }: ViewProps) {
  const theme = useTheme();
  return (
    <View
      style={[styles.card, { backgroundColor: theme.surface }, style]}
      {...props}
    />
  );
}

/** Label on the left, value or control on the right, hairline between rows. */
export function Row({
  label,
  detail,
  value,
  right,
  first,
}: {
  label: string;
  detail?: string;
  value?: string;
  right?: React.ReactNode;
  first?: boolean;
}) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.row,
        !first && {
          borderTopColor: theme.line,
          borderTopWidth: StyleSheet.hairlineWidth,
        },
      ]}>
      <View style={styles.rowText}>
        <Txt variant="bodyStrong">{label}</Txt>
        {detail ? (
          <Txt variant="caption" muted>
            {detail}
          </Txt>
        ) : null}
      </View>
      {value !== undefined ? <Txt variant="bodyStrong">{value}</Txt> : null}
      {right}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 18,
    paddingHorizontal: 16,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 14,
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
});
