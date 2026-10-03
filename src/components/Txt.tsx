import React from 'react';
import { StyleSheet, Text, type TextProps } from 'react-native';
import { fonts, useTheme } from '../theme';

type Variant =
  | 'title'
  | 'heading'
  | 'body'
  | 'bodyStrong'
  | 'caption'
  | 'stat'
  | 'button';

type Props = TextProps & {
  variant?: Variant;
  color?: string;
  muted?: boolean;
};

/**
 * All app text goes through here so the type scale stays consistent.
 * Custom fonts on Android pick weight by family name, so fontWeight is never set.
 */
export function Txt({ variant = 'body', color, muted, style, ...props }: Props) {
  const theme = useTheme();
  return (
    <Text
      style={[
        styles[variant],
        { color: color ?? (muted ? theme.muted : theme.text) },
        style,
      ]}
      {...props}
    />
  );
}

const styles = StyleSheet.create({
  title: { fontFamily: fonts.condensedBold, fontSize: 34, lineHeight: 38 },
  heading: { fontFamily: fonts.semibold, fontSize: 17, lineHeight: 22 },
  body: { fontFamily: fonts.regular, fontSize: 15, lineHeight: 21 },
  bodyStrong: { fontFamily: fonts.medium, fontSize: 15, lineHeight: 21 },
  caption: { fontFamily: fonts.regular, fontSize: 13, lineHeight: 18 },
  stat: {
    fontFamily: fonts.condensed,
    fontSize: 34,
    lineHeight: 38,
    fontVariant: ['tabular-nums'],
  },
  button: { fontFamily: fonts.semibold, fontSize: 16, lineHeight: 20 },
});
