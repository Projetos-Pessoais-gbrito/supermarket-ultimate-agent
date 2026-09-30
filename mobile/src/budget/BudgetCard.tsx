import { useRouter } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import type { BudgetLine } from '../api/types';
import { formatCurrency, formatMonthLong, formatPercent } from '../format';
import { Button } from '../ui/components';
import { colors, spacing } from '../ui/theme';
import { barPercent, budgetStateIcon, budgetStateText } from './budgetMath';
import { useBudgetStatus } from './queries';

// Amber for "near the limit"; the state is also given by an icon and text, never by color alone
const WARNING_COLOR = '#B26A00';

/** Dashboard card: this month's spending against the user's limits. Hidden while loading or on error. */
export function BudgetCard() {
  const router = useRouter();
  const { data } = useBudgetStatus();
  if (!data) {
    return null;
  }
  const lines = [...(data.overall ? [data.overall] : []), ...data.categories];
  const openSettings = () => router.push('/budget');

  if (lines.length === 0) {
    return (
      <View style={styles.card}>
        <Text style={styles.title}>Orçamento mensal</Text>
        <Text style={styles.muted}>
          Defina um limite para o mês (no total ou por categoria) e acompanhe quanto já gastou.
        </Text>
        <Button title="Definir orçamento" variant="secondary" onPress={openSettings} />
      </View>
    );
  }

  return (
    <View style={styles.card}>
      <View>
        <Text style={styles.title}>Orçamento de {formatMonthLong(data.month)}</Text>
        <Text style={styles.muted}>
          Dia {data.daysElapsed} de {data.daysInMonth}
        </Text>
      </View>
      {lines.map(line => (
        <BudgetRow key={line.category ?? 'overall'} line={line} />
      ))}
      <Button title="Editar orçamento" variant="secondary" onPress={openSettings} />
    </View>
  );
}

function BudgetRow({ line }: { line: BudgetLine }) {
  const fill = line.state === 'OVER' ? colors.danger : line.state === 'WARNING' ? WARNING_COLOR : colors.primary;
  const stateColor = line.state === 'OVER' ? colors.danger : line.state === 'WARNING' ? WARNING_COLOR : colors.textMuted;
  return (
    <View style={styles.row}>
      <View style={styles.rowHeader}>
        <Text style={[styles.label, line.category === null && styles.overallLabel]} numberOfLines={1}>
          {line.label}
        </Text>
        <Text style={styles.amounts}>
          {formatCurrency(line.spent)} de {formatCurrency(line.limit)}
        </Text>
      </View>
      <View
        style={styles.track}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${line.label}: ${formatPercent(line.percentUsed)} do limite usado, ${budgetStateText(line)}`}
        accessibilityValue={{ min: 0, max: 100, now: Math.round(barPercent(line)) }}>
        <View style={[styles.fill, { width: `${barPercent(line)}%`, backgroundColor: fill }]} />
      </View>
      <Text style={[styles.state, { color: stateColor }]}>
        {budgetStateIcon(line)} {budgetStateText(line)} · {formatPercent(line.percentUsed)}
      </Text>
      {line.state !== 'OVER' && (
        <Text style={styles.muted}>
          No ritmo atual: {formatCurrency(line.projected)} até o fim do mês
          {line.projectedOver ? ' (passaria do limite)' : ''}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md, gap: spacing.md },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  muted: { fontSize: 13, color: colors.textMuted },
  row: { gap: spacing.xs },
  rowHeader: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm },
  label: { flexShrink: 1, fontSize: 14, color: colors.text },
  overallLabel: { fontWeight: '700' },
  amounts: { fontSize: 14, color: colors.text, fontVariant: ['tabular-nums'] },
  track: { height: 10, borderRadius: 5, backgroundColor: colors.chartTrack, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 5 },
  state: { fontSize: 13, fontWeight: '600' },
});
