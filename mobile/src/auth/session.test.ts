import { isSessionValid, sessionFrom } from './session';
import { validateCredentials } from './validation';

jest.mock('expo-secure-store', () => ({}));

describe('session', () => {
  const now = 1_700_000_000_000;

  it('computes expiry from the token lifetime', () => {
    const session = sessionFrom({ accessToken: 'abc', tokenType: 'Bearer', expiresIn: 3600 }, now);

    expect(session).toEqual({ accessToken: 'abc', expiresAt: now + 3_600_000 });
  });

  it('is valid until shortly before it expires', () => {
    const session = { accessToken: 'abc', expiresAt: now + 3_600_000 };

    expect(isSessionValid(session, now)).toBe(true);
    expect(isSessionValid(session, now + 3_590_000)).toBe(false);
    expect(isSessionValid(null, now)).toBe(false);
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
