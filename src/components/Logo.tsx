import React from 'react';
import { Image, StyleSheet } from 'react-native';

/** App mark (assets/logo.svg rendered to PNG; same artwork as the launcher icon). */
export function Logo({ size = 40 }: { size?: number }) {
  return (
    <Image
      source={require('../../assets/logo.png')}
      style={[styles.logo, { width: size, height: size }]}
      accessibilityIgnoresInvertColors
      accessible={false}
    />
  );
}

const styles = StyleSheet.create({
  logo: {
    resizeMode: 'contain',
  },
});
