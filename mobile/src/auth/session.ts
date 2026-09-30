import * as SecureStore from 'expo-secure-store';

import type { TokenResponse } from '../api/types';

const SESSION_KEY = 'auth.session';

/** Access tokens are treated as expired this long before they really are, so requests never race the expiry. */
export const ACCESS_TOKEN_MARGIN_MS = 60_000;

export type Session = {
  accessToken: string;
  /** Epoch milliseconds */
  expiresAt: number;
  refreshToken: string;
  /** Epoch milliseconds */
  refreshExpiresAt: number;
};

export function sessionFrom(token: TokenResponse, now = Date.now()): Session {
  return {
    accessToken: token.accessToken,
    expiresAt: now + token.expiresIn * 1000,
    refreshToken: token.refreshToken,
    refreshExpiresAt: now + token.refreshExpiresIn * 1000,
  };
}

/** The access token can be used right now. */
export function isAccessTokenValid(session: Session | null, now = Date.now()): session is Session {
  return session !== null && session.expiresAt - ACCESS_TOKEN_MARGIN_MS > now;
}

/** A new access token can still be obtained without logging in again. */
export function canRefresh(session: Session | null, now = Date.now()): session is Session {
  return session !== null && Boolean(session.refreshToken) && session.refreshExpiresAt > now;
}

/** When to refresh proactively: shortly before the access token expires (never negative). */
export function refreshDelay(session: Session, now = Date.now()): number {
  return Math.max(session.expiresAt - ACCESS_TOKEN_MARGIN_MS - now, 0);
}

export async function loadSession(): Promise<Session | null> {
  const stored = await SecureStore.getItemAsync(SESSION_KEY);
  if (!stored) {
    return null;
  }
  try {
    const session = JSON.parse(stored) as Partial<Session>;
    // Sessions saved before refresh tokens existed cannot be renewed: log in once more
    if (!session.accessToken || !session.refreshToken) {
      await clearSession();
      return null;
    }
    return session as Session;
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
