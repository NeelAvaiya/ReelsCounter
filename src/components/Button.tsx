import React from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { useTheme } from '../theme';
import { Txt } from './Txt';

type Props = {
  title: string;
  onPress: () => void;
  /** primary: amber, the one main action on a screen. outline: everything else. */
  variant?: 'primary' | 'outline' | 'danger';
  disabled?: boolean;
};

export function Button({ title, onPress, variant = 'primary', disabled }: Props) {
  const theme = useTheme();
  const primary = variant === 'primary';
  const fg = primary
    ? theme.onSignal
    : variant === 'danger'
    ? theme.danger
    : theme.text;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      onPress={onPress}
      disabled={disabled}
      android_ripple={{ color: theme.line }}
      style={({ pressed }) => [
        styles.button,
        primary
          ? { backgroundColor: theme.signal }
          : { borderColor: theme.line, borderWidth: 1.5 },
        { opacity: disabled ? 0.45 : pressed ? 0.85 : 1 },
      ]}>
      <Txt variant="button" color={fg}>
        {title}
      </Txt>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 52,
    borderRadius: 14,
    paddingHorizontal: 18,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
});
