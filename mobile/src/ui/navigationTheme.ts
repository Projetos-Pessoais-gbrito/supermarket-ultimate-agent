import { DarkTheme, DefaultTheme } from 'expo-router';

import type { Colors } from './theme';

export type NavigationTheme = typeof DefaultTheme;

/** Headers, tab bar and screen backgrounds follow the same palette as the screens. */
export function navigationTheme(colors: Colors, dark: boolean): NavigationTheme {
  const base = dark ? DarkTheme : DefaultTheme;
  return {
    ...base,
    colors: {
      ...base.colors,
      primary: colors.primary,
      background: colors.surface,
      card: colors.background,
      text: colors.text,
      border: colors.border,
      notification: colors.danger,
    },
  };
}
