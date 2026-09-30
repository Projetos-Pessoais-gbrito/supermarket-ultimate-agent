import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { AppState, StyleSheet, Text, View } from 'react-native';

import { Button } from '../ui/components';
import { makeStyles, spacing } from '../ui/theme';
import { useAuth } from './AuthProvider';
import {
  authenticate,
  isBiometricLockEnabled,
  setBiometricLockEnabled,
  shouldLockAfterBackground,
} from './biometrics';

type AppLockState = {
  enabled: boolean;
  /** Turning it on asks for the fingerprint first, so it is never enabled by mistake. */
  setEnabled: (enabled: boolean) => Promise<boolean>;
};

const AppLockContext = createContext<AppLockState | null>(null);

const UNLOCK_PROMPT = 'Desbloqueie o Supermarket Agent';

/**
 * Covers the signed-in app until the owner unlocks it with fingerprint/face: when the app opens
 * and when it returns after more than a minute in the background.
 */
export function AppLock({ children }: { children: ReactNode }) {
  const { status, signOut } = useAuth();
  const [enabled, setEnabledState] = useState<boolean | null>(null);
  const [locked, setLocked] = useState(true);
  const backgroundedAt = useRef<number | null>(null);

  useEffect(() => {
    isBiometricLockEnabled()
      .then(on => {
        setEnabledState(on);
        setLocked(on);
      })
      .catch(() => {
        setEnabledState(false);
        setLocked(false);
      });
  }, []);

  // The preference belongs to the person who turned it on: forget it on sign-out
  useEffect(() => {
    if (status === 'signedOut' && enabled) {
      setBiometricLockEnabled(false)
        .catch(() => undefined)
        .then(() => {
          setEnabledState(false);
          setLocked(false);
        });
    }
  }, [status, enabled]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'background') {
        backgroundedAt.current = Date.now();
      } else if (state === 'active') {
        if (enabled && shouldLockAfterBackground(backgroundedAt.current)) {
          setLocked(true);
        }
        backgroundedAt.current = null;
      }
    });
    return () => subscription.remove();
  }, [enabled]);

  const setEnabled = useCallback(async (on: boolean) => {
    if (on && !(await authenticate('Confirme sua digital para ativar'))) {
      return false;
    }
    await setBiometricLockEnabled(on);
    setEnabledState(on);
    setLocked(false);
    return true;
  }, []);

  const showLock = status === 'signedIn' && (enabled === null || (enabled && locked));

  return (
    <AppLockContext.Provider value={{ enabled: enabled === true, setEnabled }}>
      {children}
      {showLock && (
        <LockScreen
          ready={enabled !== null}
          onUnlocked={() => setLocked(false)}
          onUsePassword={() => void signOut()}
        />
      )}
    </AppLockContext.Provider>
  );
}

export function useAppLock(): AppLockState {
  const lock = useContext(AppLockContext);
  if (!lock) {
    throw new Error('useAppLock must be used inside AppLock');
  }
  return lock;
}

function LockScreen({
  ready,
  onUnlocked,
  onUsePassword,
}: {
  ready: boolean;
  onUnlocked: () => void;
  onUsePassword: () => void;
}) {
  const styles = useStyles();
  const [failed, setFailed] = useState(false);

  const unlock = useCallback(
    () =>
      authenticate(UNLOCK_PROMPT).then(
        ok => (ok ? onUnlocked() : setFailed(true)),
        () => setFailed(true),
      ),
    [onUnlocked],
  );

  // Ask right away, like banking apps do
  useEffect(() => {
    if (ready) {
      authenticate(UNLOCK_PROMPT).then(
        ok => (ok ? onUnlocked() : setFailed(true)),
        () => setFailed(true),
      );
    }
  }, [ready, onUnlocked]);

  return (
    <View style={styles.overlay} accessibilityViewIsModal>
      {ready && (
        <View style={styles.content}>
          <Text style={styles.title}>App bloqueado</Text>
          <Text style={styles.text}>Use sua digital para ver suas notas e seus gastos.</Text>
          {failed && <Text style={styles.text}>Não foi possível confirmar. Tente de novo.</Text>}
          <Button title="Desbloquear com digital" onPress={() => void unlock()} />
          <Button title="Entrar com senha" variant="secondary" onPress={onUsePassword} />
        </View>
      )}
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: colors.background,
    justifyContent: 'center',
    padding: spacing.lg,
  },
  content: { gap: spacing.md },
  title: { fontSize: 24, fontWeight: '700', color: colors.text, textAlign: 'center' },
  text: { fontSize: 15, color: colors.textMuted, textAlign: 'center', marginBottom: spacing.sm },
}));
