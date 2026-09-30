import { exportFileName } from './exportFile';

jest.mock('expo-file-system', () => ({}));
jest.mock('expo-sharing', () => ({}));

describe('exportFileName', () => {
  it('uses the local date', () => {
    expect(exportFileName(new Date(2026, 8, 30, 23, 59))).toBe('meus-dados-2026-09-30.json');
  });
});
