import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { accountApi, authApi } from '../../../api/endpoints';
import { errorMessage } from '../../../api/messages';
import { shareExport } from '../../../account/exportFile';
import { useAuth, useToken } from '../../../auth/AuthProvider';
import { Button, ErrorBanner, TextField } from '../../../ui/components';
import { colors, spacing } from '../../../ui/theme';

export default function AccountScreen() {
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

      <View style={styles.card}>
        <Text style={styles.title}>Seus dados</Text>
        <Text style={styles.text}>
          Baixe tudo o que o app guarda sobre você: sua conta e todas as notas fiscais com itens e pagamentos,
          em um arquivo JSON.
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

function exportErrorMessage(error: unknown): string {
  return error instanceof Error && error.message.startsWith('Compartilhamento')
    ? error.message
    : errorMessage(error);
}

const styles = StyleSheet.create({
  content: { padding: spacing.md, gap: spacing.md, backgroundColor: colors.surface, flexGrow: 1 },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, gap: spacing.md },
  dangerCard: { borderWidth: 1, borderColor: colors.dangerBackground },
  label: { fontSize: 14, color: colors.textMuted },
  email: { fontSize: 18, fontWeight: '600', color: colors.text },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  text: { fontSize: 14, color: colors.textMuted, lineHeight: 20 },
  actions: { gap: spacing.sm },
});
