import { Link } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useState } from 'react';
import { KeyboardAvoidingView, ScrollView, Text } from 'react-native';

import { ApiError } from '../api/client';
import { errorMessage } from '../api/messages';
import { Button, ErrorBanner, TextField } from '../ui/components';
import { makeStyles, spacing } from '../ui/theme';
import { useAuth } from './AuthProvider';
import { MIN_PASSWORD_LENGTH, validateCredentials } from './validation';

type Mode = 'login' | 'register';

export function AuthForm({ mode }: { mode: Mode }) {
  const styles = useStyles();
  // Apps are edge-to-edge, so Android no longer resizes the window for the keyboard:
  // pad on every platform, measured from below the header
  const headerHeight = useHeaderHeight();
  const { signIn, signUp } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    const validationError = validateCredentials(email, password, mode);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const credentials = { email: email.trim(), password };
      await (mode === 'login' ? signIn(credentials) : signUp(credentials));
    } catch (e) {
      setError(authErrorMessage(e, mode));
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior="padding" keyboardVerticalOffset={headerHeight}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>{mode === 'login' ? 'Bem-vindo de volta' : 'Crie sua conta'}</Text>
        <Text style={styles.subtitle}>
          Escaneie suas notas fiscais e descubra onde e quando comprar mais barato.
        </Text>

        <ErrorBanner message={error} />

        <TextField
          label="E-mail"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          textContentType="emailAddress"
          placeholder="voce@exemplo.com"
        />
        <TextField
          label="Senha"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          textContentType={mode === 'login' ? 'password' : 'newPassword'}
          placeholder={mode === 'register' ? `Mínimo de ${MIN_PASSWORD_LENGTH} caracteres` : undefined}
          onSubmitEditing={submit}
        />

        <Button title={mode === 'login' ? 'Entrar' : 'Criar conta'} onPress={submit} loading={submitting} />

        <Link href={mode === 'login' ? '/register' : '/login'} replace style={styles.link}>
          {mode === 'login' ? 'Ainda não tem conta? Cadastre-se' : 'Já tem conta? Entre'}
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function authErrorMessage(error: unknown, mode: Mode): string {
  if (error instanceof ApiError) {
    if (mode === 'login' && error.status === 401) {
      return 'E-mail ou senha incorretos.';
    }
    if (error.status === 400) {
      return `Verifique o e-mail e use uma senha com pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`;
    }
  }
  return errorMessage(error);
}

const useStyles = makeStyles(colors => ({
  flex: { flex: 1, backgroundColor: colors.background },
  container: { flexGrow: 1, justifyContent: 'center', padding: spacing.lg },
  title: { fontSize: 26, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  subtitle: { fontSize: 16, color: colors.textMuted, marginBottom: spacing.lg },
  link: { marginTop: spacing.lg, textAlign: 'center', color: colors.primary, fontSize: 15 },
}));
