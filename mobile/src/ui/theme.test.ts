import { darkColors, lightColors, paletteFor } from './theme';

describe('paletteFor', () => {
  it('uses the dark palette only when the phone is in dark mode', () => {
    expect(paletteFor('dark')).toBe(darkColors);
    expect(paletteFor('light')).toBe(lightColors);
    expect(paletteFor(null)).toBe(lightColors);
    expect(paletteFor(undefined)).toBe(lightColors);
  });

  it('keeps the same tokens in both palettes', () => {
    expect(Object.keys(darkColors).sort()).toEqual(Object.keys(lightColors).sort());
  });
});
