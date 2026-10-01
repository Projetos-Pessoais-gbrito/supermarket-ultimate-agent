import { StyleSheet, useColorScheme, type ColorSchemeName } from 'react-native';

export type Colors = {
  primary: string;
  primaryPressed: string;
  /** Text and icons on top of `primary` (buttons) */
  onPrimary: string;
  /** Cards, headers, inputs */
  background: string;
  /** Page behind the cards */
  surface: string;
  text: string;
  textMuted: string;
  border: string;
  danger: string;
  /** Text on top of `danger` (destructive buttons) */
  onDanger: string;
  dangerBackground: string;
  /** Translucent layer over content while something loads */
  overlay: string;
  // Charts: accent series is `primary`; context series recede to this gray
  chartMuted: string;
  chartTrack: string;
};

export const lightColors: Colors = {
  primary: '#1B7F4B',
  primaryPressed: '#15653B',
  onPrimary: '#FFFFFF',
  background: '#FFFFFF',
  surface: '#F4F6F5',
  text: '#1A1A1A',
  textMuted: '#5F6B66',
  border: '#D5DBD8',
  danger: '#B3261E',
  onDanger: '#FFFFFF',
  dangerBackground: '#FDECEA',
  overlay: 'rgba(255,255,255,0.8)',
  chartMuted: '#C9D1CD',
  chartTrack: '#EEF1EF',
};

/** Own steps from the same hues (not an automatic inversion), validated against the dark card surface. */
export const darkColors: Colors = {
  primary: '#35AD73',
  primaryPressed: '#2E9A66',
  onPrimary: '#0B1A12',
  background: '#1C2220',
  surface: '#121715',
  text: '#ECEFEE',
  textMuted: '#A3ADA9',
  border: '#34403B',
  danger: '#F2B8B5',
  onDanger: '#3B0A08',
  dangerBackground: '#3B1B1A',
  overlay: 'rgba(0,0,0,0.6)',
  chartMuted: '#4A5652',
  chartTrack: '#2A3330',
};

/** @deprecated Static light palette; use {@link useColors} so screens follow dark mode. */
export const colors = lightColors;

export function paletteFor(scheme: ColorSchemeName | null | undefined): Colors {
  return scheme === 'dark' ? darkColors : lightColors;
}

/** Palette for the phone's current light/dark setting. */
export function useColors(): Colors {
  return paletteFor(useColorScheme());
}

/**
 * Themed StyleSheet: `const useStyles = makeStyles(c => ({ card: { backgroundColor: c.background } }))`
 * at module level, then `const styles = useStyles()` inside the component. Styles are built once per
 * palette.
 */
export function makeStyles<T extends StyleSheet.NamedStyles<T>>(factory: (colors: Colors) => T): () => T {
  const cache = new Map<Colors, T>();
  return function useStyles() {
    const palette = useColors();
    let styles = cache.get(palette);
    if (!styles) {
      styles = StyleSheet.create(factory(palette));
      cache.set(palette, styles);
    }
    return styles;
  };
}

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 };
