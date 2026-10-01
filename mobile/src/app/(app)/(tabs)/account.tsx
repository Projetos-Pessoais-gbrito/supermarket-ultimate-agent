import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, Switch, Text, View } from 'react-native';

import { accountApi, authApi } from '../../../api/endpoints';
import { errorMessage } from '../../../api/messages';
import { shareExport } from '../../../account/exportFile';
import { useAppLock } from '../../../auth/AppLock';
import { useAuth, useToken } from '../../../auth/AuthProvider';
import { isBiometricAvailable } from '../../../auth/biometrics';
import { Button, ErrorBanner, TextField } from '../../../ui/components';
import { makeStyles, spacing, useColors } from '../../../ui/theme';

export default function AccountScreen() {
  const router = useRouter();
  const styles = useStyles();
  const token = useToken();
  const { signOut } = useAuth();
  const me = useQuery({ queryKey: ['me'], queryFn: () => authApi.me(token) });

  const exportData = useMutation({
    mutationFn: async () => shareExport(await accountApi.export(token)),
  });

  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [password, setPassword] = useState('');
  const deleteAccount = useMutation({
    mutationFn: () => accountApi.delete(token, password),
    onSuccess: () => signOut(),
  });

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={styles.card}>
        <Text style={styles.label}>Conectado como</Text>
        <Text style={styles.email}>{me.data?.email ?? '…'}</Text>
        <Button title="Sair" variant="secondary" onPress={() => void signOut()} />
      </View>

      <BiometricSetting />

      <Pressable
        accessibilityRole="button"
        onPress={() => router.push('/budget')}
        style={({ pressed }) => [styles.card, styles.linkRow, pressed && styles.pressed]}>
        <View style={styles.switchText}>
          <Text style={styles.title}>Orçamento mensal</Text>
          <Text style={styles.text}>Limite do mês e por categoria</Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </Pressable>

      <View style={styles.card}>
        <Text style={styles.title}>Seus dados</Text>
        <Text style={styles.text}>
          Baixe tudo o que o app guarda sobre você: sua conta, as notas fiscais com itens e pagamentos, sua
          lista de compras e seus orçamentos, em um arquivo JSON.
        </Text>
        {exportData.error && <ErrorBanner message={exportErrorMessage(exportData.error)} />}
        <Button title="Exportar meus dados" onPress={() => exportData.mutate()} loading={exportData.isPending} />
      </View>

      <View style={[styles.card, styles.dangerCard]}>
        <Text style={styles.title}>Excluir conta</Text>
        <Text style={styles.text}>
          Apaga para sempre sua conta, suas notas fiscais e todo o histórico de preços. Não é possível desfazer.
        </Text>
        {!confirmingDelete ? (
          <Button title="Excluir minha conta" variant="danger" onPress={() => setConfirmingDelete(true)} />
        ) : (
          <>
            {deleteAccount.error && <ErrorBanner message={errorMessage(deleteAccount.error)} />}
            <TextField
              label="Confirme sua senha"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoComplete="current-password"
              textContentType="password"
            />
            <View style={styles.actions}>
              <Button
                title="Excluir definitivamente"
                variant="danger"
                onPress={() => deleteAccount.mutate()}
                loading={deleteAccount.isPending}
                disabled={password.length === 0}
              />
              <Button
                title="Cancelar"
                variant="secondary"
                onPress={() => {
                  setConfirmingDelete(false);
                  setPassword('');
                  deleteAccount.reset();
                }}
              />
            </View>
          </>
        )}
      </View>
    </ScrollView>
  );
}

function BiometricSetting() {
  const colors = useColors();
  const styles = useStyles();
  const { enabled, setEnabled } = useAppLock();
  const [available, setAvailable] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    isBiometricAvailable().then(setAvailable, () => setAvailable(false));
  }, []);

  async function toggle(on: boolean) {
    setBusy(true);
    try {
      await setEnabled(on);
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <View style={styles.switchRow}>
        <View style={styles.switchText}>
          <Text style={styles.title}>Entrar com digital</Text>
          <Text style={styles.text}>
            {available === false
              ? 'Cadastre uma digital (ou rosto) nas configurações do celular para usar.'
              : 'Pede sua digital ao abrir o app e quando você volta depois de mais de 1 minuto fora.'}
          </Text>
        </View>
        <Switch
          value={enabled}
          onValueChange={on => void toggle(on)}
          disabled={!available || busy}
          trackColor={{ true: colors.primary }}
          accessibilityLabel="Entrar com digital"
        />
      </View>
    </View>
  );
}

function exportErrorMessage(error: unknown): string {
  return error instanceof Error && error.message.startsWith('Compartilhamento')
    ? error.message
    : errorMessage(error);
}

const useStyles = makeStyles(colors => ({
  content: { padding: spacing.md, gap: spacing.md, backgroundColor: colors.surface, flexGrow: 1 },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, gap: spacing.md },
  dangerCard: { borderWidth: 1, borderColor: colors.dangerBackground },
  label: { fontSize: 14, color: colors.textMuted },
  email: { fontSize: 18, fontWeight: '600', color: colors.text },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  text: { fontSize: 14, color: colors.textMuted, lineHeight: 20 },
  actions: { gap: spacing.sm },
  switchRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  switchText: { flex: 1, gap: spacing.xs },
  linkRow: { flexDirection: 'row', alignItems: 'center' },
  pressed: { opacity: 0.7 },
  chevron: { fontSize: 24, color: colors.textMuted },
}));
