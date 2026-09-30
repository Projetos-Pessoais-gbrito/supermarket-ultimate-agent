import { LOCK_GRACE_MS, shouldLockAfterBackground } from './biometrics';

jest.mock('expo-local-authentication', () => ({}));
jest.mock('expo-secure-store', () => ({}));

describe('shouldLockAfterBackground', () => {
  const now = 1_700_000_000_000;

  it('does not ask again after a quick switch to another app', () => {
    expect(shouldLockAfterBackground(now - 10_000, now)).toBe(false);
  });

  it('asks again after more than a minute away', () => {
    expect(shouldLockAfterBackground(now - LOCK_GRACE_MS - 1, now)).toBe(true);
  });

  it('does not lock when the app never went to the background', () => {
    expect(shouldLockAfterBackground(null, now)).toBe(false);
  });
});
