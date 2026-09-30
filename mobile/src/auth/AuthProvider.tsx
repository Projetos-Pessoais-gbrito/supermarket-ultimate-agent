import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { authApi } from '../api/endpoints';
import { clearSession, isSessionValid, loadSession, saveSession, sessionFrom, type Session } from './session';

type Credentials = { email: string; password: string };

type AuthState = {
  status: 'loading' | 'signedOut' | 'signedIn';
  token: string | null;
  signIn: (credentials: Credentials) => Promise<void>;
  signUp: (credentials: Credentials) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSession()
      .then(stored => setSession(isSessionValid(stored) ? stored : null))
      .catch(() => setSession(null))
      .finally(() => setLoading(false));
  }, []);

  const start = useCallback(async (next: Session) => {
    await saveSession(next);
    setSession(next);
  }, []);

  const signIn = useCallback(
    async (credentials: Credentials) => start(sessionFrom(await authApi.login(credentials))),
    [start],
  );

  const signUp = useCallback(
    async (credentials: Credentials) => start(sessionFrom(await authApi.register(credentials))),
    [start],
  );

  const signOut = useCallback(async () => {
    await clearSession();
    setSession(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      status: loading ? 'loading' : session ? 'signedIn' : 'signedOut',
      token: session?.accessToken ?? null,
      signIn,
      signUp,
      signOut,
    }),
    [loading, session, signIn, signUp, signOut],
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
