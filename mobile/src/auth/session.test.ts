import { canRefresh, isAccessTokenValid, refreshDelay, sessionFrom } from './session';
import { validateCredentials } from './validation';

jest.mock('expo-secure-store', () => ({}));

describe('session', () => {
  const now = 1_700_000_000_000;
  const token = { accessToken: 'abc', tokenType: 'Bearer' as const, expiresIn: 3600, refreshToken: 'r', refreshExpiresIn: 2_592_000 };

  it('computes both expiries from the token lifetimes', () => {
    expect(sessionFrom(token, now)).toEqual({
      accessToken: 'abc',
      expiresAt: now + 3_600_000,
      refreshToken: 'r',
      refreshExpiresAt: now + 2_592_000_000,
    });
  });

  it('treats the access token as expired a minute early', () => {
    const session = sessionFrom(token, now);

    expect(isAccessTokenValid(session, now)).toBe(true);
    expect(isAccessTokenValid(session, now + 3_550_000)).toBe(false);
    expect(isAccessTokenValid(null, now)).toBe(false);
  });

  it('can refresh until the refresh token expires', () => {
    const session = sessionFrom(token, now);

    expect(canRefresh(session, now + 3_600_000)).toBe(true);
    expect(canRefresh(session, now + 2_592_000_000)).toBe(false);
    expect(canRefresh({ ...session, refreshToken: '' }, now)).toBe(false);
  });

  it('schedules the refresh a minute before the access token expires', () => {
    const session = sessionFrom(token, now);

    expect(refreshDelay(session, now)).toBe(3_540_000);
    expect(refreshDelay(session, now + 4_000_000)).toBe(0);
  });
});

describe('validateCredentials', () => {
  it('accepts valid input', () => {
    expect(validateCredentials(' ana@example.com ', 'correct-horse', 'register')).toBeNull();
  });

  it('rejects an invalid e-mail', () => {
    expect(validateCredentials('ana', 'correct-horse', 'login')).toBe('Digite um e-mail válido.');
  });

  it('requires 8 characters only when registering', () => {
    expect(validateCredentials('ana@example.com', 'short', 'register')).toContain('8 caracteres');
    expect(validateCredentials('ana@example.com', 'short', 'login')).toBeNull();
  });
});
