import * as SecureStore from 'expo-secure-store';

import type { TokenResponse } from '../api/types';

const SESSION_KEY = 'auth.session';

export type Session = {
  accessToken: string;
  /** Epoch milliseconds */
  expiresAt: number;
};

export function sessionFrom(token: TokenResponse, now = Date.now()): Session {
  return { accessToken: token.accessToken, expiresAt: now + token.expiresIn * 1000 };
}

/** Treats sessions about to expire as expired so requests don't fail mid-flight. */
export function isSessionValid(session: Session | null, now = Date.now()): session is Session {
  const safetyMarginMs = 30_000;
  return session !== null && session.expiresAt - safetyMarginMs > now;
}

export async function loadSession(): Promise<Session | null> {
  const stored = await SecureStore.getItemAsync(SESSION_KEY);
  if (!stored) {
    return null;
  }
  try {
    return JSON.parse(stored) as Session;
  } catch {
    await clearSession();
    return null;
  }
}

export function saveSession(session: Session): Promise<void> {
  return SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): Promise<void> {
  return SecureStore.deleteItemAsync(SESSION_KEY);
}
