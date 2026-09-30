import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState, type ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ApiError } from '../api/client';
import { AppLock } from '../auth/AppLock';
import { AuthProvider, useAuth } from '../auth/AuthProvider';
import { colors } from '../ui/theme';

export default function RootLayout() {
  return (
    <AuthProvider>
      <QueryProvider>
        <AppLock>
          <RootNavigator />
        </AppLock>
        <StatusBar style="auto" />
      </QueryProvider>
    </AuthProvider>
  );
}

/**
 * A 401 means the access token expired: renew it once and retry the failed queries. If the
 * session itself is over, refresh() signs the user out.
 */
function QueryProvider({ children }: { children: ReactNode }) {
  const { refresh, status } = useAuth();
  const [queryClient] = useState(() => {
    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({ onError: error => void renewAfter401(error) }),
      mutationCache: new MutationCache({ onError: error => void renewAfter401(error) }),
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
        },
      },
    });
    async function renewAfter401(error: unknown) {
      if (error instanceof ApiError && error.status === 401 && (await refresh())) {
        await client.invalidateQueries();
      }
    }
    return client;
  });

  // Never show one user's cached receipts or insights to the next person who logs in on this phone
  useEffect(() => {
    if (status === 'signedOut') {
      queryClient.clear();
    }
  }, [status, queryClient]);

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function RootNavigator() {
  const { status } = useAuth();

  if (status === 'loading') {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  const signedIn = status === 'signedIn';
  return (
    <Stack screenOptions={{ headerTitleAlign: 'center' }}>
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="(app)" options={{ headerShown: false }} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="login" options={{ title: 'Entrar' }} />
        <Stack.Screen name="register" options={{ title: 'Criar conta' }} />
      </Stack.Protected>
    </Stack>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
});
