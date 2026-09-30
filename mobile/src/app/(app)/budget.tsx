import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../api/messages';
import type { BudgetCategoryOption, BudgetSettings } from '../../api/types';
import { formatMoneyInput, parseMoneyInput } from '../../budget/budgetMath';
import { useBudgetCategories, useBudgetSettings, useSaveBudget } from '../../budget/queries';
import { Button, ErrorBanner, TextField } from '../../ui/components';
import { colors, spacing } from '../../ui/theme';

export default function BudgetScreen() {
  const settings = useBudgetSettings();
  const categories = useBudgetCategories();
  const error = settings.error ?? categories.error;

  return (
    <>
      <Stack.Screen options={{ title: 'Orçamento mensal' }} />
      {settings.data && categories.data ? (
        <BudgetForm settings={settings.data} categories={categories.data} />
      ) : (
        <View style={styles.content}>
          {error ? (
            <ErrorBanner message={errorMessage(error)} />
          ) : (
            <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
          )}
        </View>
      )}
    </>
  );
}

function BudgetForm({ settings, categories }: { settings: BudgetSettings; categories: BudgetCategoryOption[] }) {
  const router = useRouter();
  const save = useSaveBudget();
  const [overall, setOverall] = useState(formatMoneyInput(settings.overall));
  const [limits, setLimits] = useState<Record<string, string>>(() =>
    Object.fromEntries(settings.categories.map(limit => [limit.category, formatMoneyInput(limit.limit)])),
  );
  const [invalid, setInvalid] = useState<string | null>(null);

  const submit = () => {
    const overallValue = parseMoneyInput(overall);
    if (overallValue === undefined) {
      setInvalid('Confira o limite total: use um valor como 800 ou 1.250,50.');
      return;
    }
    const categoryLimits: { category: string; limit: number }[] = [];
    for (const option of categories) {
      const value = parseMoneyInput(limits[option.category] ?? '');
      if (value === undefined) {
        setInvalid(`Confira o limite de ${option.label}: use um valor como 150 ou 89,90.`);
        return;
      }
      if (value !== null) {
        categoryLimits.push({ category: option.category, limit: value });
      }
    }
    setInvalid(null);
    save.mutate({ overall: overallValue, categories: categoryLimits }, { onSuccess: () => router.back() });
  };

  return (
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={styles.card}>
        <Text style={styles.title}>Limite do mês</Text>
        <Text style={styles.text}>
          Quanto você quer gastar no mercado por mês, somando todas as notas. Deixe em branco para não ter limite.
        </Text>
        <TextField
          label="Limite total (R$)"
          value={overall}
          onChangeText={setOverall}
          keyboardType="decimal-pad"
          placeholder="Ex.: 1.200,00"
        />
      </View>

      <View style={styles.card}>
        <Text style={styles.title}>Limites por categoria</Text>
        <Text style={styles.text}>Opcional. Você recebe um aviso no painel ao chegar a 80% de cada limite.</Text>
        {categories.map(option => (
          <TextField
            key={option.category}
            label={`${option.label} (R$)`}
            value={limits[option.category] ?? ''}
            onChangeText={text => setLimits(current => ({ ...current, [option.category]: text }))}
            keyboardType="decimal-pad"
            placeholder="Sem limite"
          />
        ))}
      </View>

      <ErrorBanner message={invalid ?? (save.error ? errorMessage(save.error) : null)} />
      <Button title="Salvar orçamento" onPress={submit} loading={save.isPending} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.md, gap: spacing.md, backgroundColor: colors.surface, flexGrow: 1 },
  loading: { marginTop: spacing.xl },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, gap: spacing.sm },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  text: { fontSize: 14, color: colors.textMuted, lineHeight: 20, marginBottom: spacing.sm },
});
