import { useColorScheme } from 'react-native';

/**
 * Visual identity: a hand tally counter. Ink-navy panel, amber "lit display" digits, cool paper
 * background. Barlow for text, Barlow Condensed for numbers and titles
 * (android/app/src/main/assets/fonts, OFL).
 */
export const fonts = {
  regular: 'Barlow-Regular',
  medium: 'Barlow-Medium',
  semibold: 'Barlow-SemiBold',
  condensed: 'BarlowCondensed-SemiBold',
  condensedBold: 'BarlowCondensed-Bold',
} as const;

const light = {
  background: '#EEF1F6',
  surface: '#FFFFFF',
  text: '#141A33',
  muted: '#6A7391',
  line: '#D9DEE8',
  signal: '#FFB020',
  onSignal: '#141A33',
  panel: '#141A33',
  panelText: '#EEF1F6',
  panelMuted: '#8F98B8',
  panelCell: '#1F2648',
  positive: '#1FA88A',
  danger: '#D64545',
  input: '#F5F7FA',
};

export type Theme = typeof light;

const dark: Theme = {
  background: '#0C1022',
  surface: '#151A31',
  text: '#E8ECF7',
  muted: '#8E97B5',
  line: '#262C47',
  signal: '#FFB020',
  onSignal: '#141A33',
  panel: '#1A2045',
  panelText: '#EEF1F6',
  panelMuted: '#8F98B8',
  panelCell: '#252D59',
  positive: '#2BC29F',
  danger: '#F07070',
  input: '#0F1428',
};

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}
