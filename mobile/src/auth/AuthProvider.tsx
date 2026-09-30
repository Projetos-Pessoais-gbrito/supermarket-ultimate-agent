import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';

import { ApiError } from '../api/client';
import { authApi } from '../api/endpoints';
import {
  canRefresh,
  clearSession,
  isAccessTokenValid,
  loadSession,
  refreshDelay,
  saveSession,
  sessionFrom,
  type Session,
} from './session';

type Credentials = { email: string; password: string };

type AuthState = {
  status: 'loading' | 'signedOut' | 'signedIn';
  token: string | null;
  signIn: (credentials: Credentials) => Promise<void>;
  signUp: (credentials: Credentials) => Promise<void>;
  signOut: () => Promise<void>;
  /**
   * Gets a new access token with the refresh token. Resolves true on success; signs the user out
   * when the server rejects the session; keeps the session (false) when offline.
   */
  refresh: () => Promise<boolean>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  // Latest session for callbacks, and the refresh in flight so parallel 401s share one request
  const sessionRef = useRef<Session | null>(null);
  const refreshInFlight = useRef<Promise<boolean> | null>(null);

  const apply = useCallback(async (next: Session | null) => {
    sessionRef.current = next;
    setSession(next);
    await (next ? saveSession(next) : clearSession());
  }, []);

  const refresh = useCallback((): Promise<boolean> => {
    if (refreshInFlight.current) {
      return refreshInFlight.current;
    }
    const current = sessionRef.current;
    if (!canRefresh(current)) {
      return apply(null).then(() => false);
    }
    refreshInFlight.current = authApi
      .refresh(current.refreshToken)
      .then(async token => {
        await apply(sessionFrom(token));
        return true;
      })
      .catch(async error => {
        // Offline or server hiccup: keep the session and try again later
        if (error instanceof ApiError && error.status === 401) {
          await apply(null);
        }
        return false;
      })
      .finally(() => {
        refreshInFlight.current = null;
      });
    return refreshInFlight.current;
  }, [apply]);

  // Restore the session on start, renewing it if only the access token expired
  useEffect(() => {
    loadSession()
      .then(async stored => {
        sessionRef.current = stored;
        if (isAccessTokenValid(stored)) {
          setSession(stored);
        } else if (canRefresh(stored)) {
          await refresh();
        } else if (stored) {
          await apply(null);
        }
      })
      .catch(() => apply(null))
      .finally(() => setLoading(false));
  }, [apply, refresh]);

  // Renew shortly before the access token expires while the app is open
  useEffect(() => {
    if (!session) {
      return;
    }
    const timer = setTimeout(() => void refresh(), refreshDelay(session));
    return () => clearTimeout(timer);
  }, [session, refresh]);

  // Timers do not run in the background: check again when the app comes back
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active' && sessionRef.current && !isAccessTokenValid(sessionRef.current)) {
        void refresh();
      }
    });
    return () => subscription.remove();
  }, [refresh]);

  const signIn = useCallback(
    async (credentials: Credentials) => apply(sessionFrom(await authApi.login(credentials))),
    [apply],
  );

  const signUp = useCallback(
    async (credentials: Credentials) => apply(sessionFrom(await authApi.register(credentials))),
    [apply],
  );

  const signOut = useCallback(async () => {
    const current = sessionRef.current;
    await apply(null);
    if (current?.refreshToken) {
      // Best effort: revoke the session on the server too
      authApi.logout(current.refreshToken).catch(() => undefined);
    }
  }, [apply]);

  const value = useMemo<AuthState>(
    () => ({
      status: loading ? 'loading' : session ? 'signedIn' : 'signedOut',
      token: session?.accessToken ?? null,
      signIn,
      signUp,
      signOut,
      refresh,
    }),
    [loading, session, signIn, signUp, signOut, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return auth;
}

/** Token for screens that are only reachable when signed in. */
export function useToken(): string {
  const { token } = useAuth();
  if (!token) {
    throw new Error('useToken called while signed out');
  }
  return token;
}
