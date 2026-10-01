import { Stack, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, FlatList, Text, View } from 'react-native';

import { errorMessage } from '../../api/messages';
import type { BestTimeFinding, BestTimeGroup, StoreBestTime } from '../../api/types';
import { formatPercent } from '../../format';
import { findingText, type FindingKind } from '../../insights/bestTimeText';
import { parsePeriod } from '../../insights/period';
import { useBestTime } from '../../insights/queries';
import { ErrorBanner } from '../../ui/components';
import { makeStyles, spacing, useColors } from '../../ui/theme';

/** Best time to shop at each store, and how it is decided. */
export default function BestTimeScreen() {
  const colors = useColors();
  const styles = useStyles();
  const { months } = useLocalSearchParams<{ months?: string }>();
  const { data, error, isPending } = useBestTime(parsePeriod(months));

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Melhor época' }} />
      {isPending ? (
        <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
      ) : !data ? (
        <View style={styles.list}>
          <ErrorBanner message={errorMessage(error)} />
        </View>
      ) : (
        <FlatList
          data={data.stores}
          keyExtractor={store => String(store.storeId)}
          contentContainerStyle={styles.list}
          ListHeaderComponent={<Explanation hasStores={data.stores.length > 0} />}
          ListEmptyComponent={
            <Text style={styles.muted}>
              Ainda não há o que comparar. Compre os mesmos produtos no mesmo mercado algumas vezes ao longo do mês.
            </Text>
          }
          renderItem={({ item }) => <StoreCard store={item} />}
        />
      )}
    </View>
  );
}

function Explanation({ hasStores }: { hasStores: boolean }) {
  const styles = useStyles();
  return (
    <View style={styles.header}>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Como funciona</Text>
        <Text style={styles.text}>
          Comparamos cada compra com o preço que você costuma pagar pelo mesmo produto no mesmo mercado. Assim vemos se
          algum período do mês ou dia da semana sai mais barato em cada mercado.
        </Text>
        <Text style={[styles.text, styles.paragraph]}>
          Só mostramos um padrão quando a diferença é de pelo menos 3% e aparece em várias compras. Diferenças menores
          podem ser sorte ou uma promoção isolada; nesse caso dizemos &quot;sem padrão&quot;, e você pode comprar
          quando precisar.
        </Text>
      </View>
      {hasStores && <Text style={styles.sectionTitle}>Por mercado</Text>}
    </View>
  );
}

function StoreCard({ store }: { store: StoreBestTime }) {
  const styles = useStyles();
  return (
    <View style={styles.card}>
      <Text style={styles.storeName}>{store.storeName}</Text>
      <Text style={styles.muted}>
        {store.comparablePurchases} {store.comparablePurchases === 1 ? 'compra comparada' : 'compras comparadas'}
      </Text>
      <FindingSection title="Época do mês" finding={store.periodOfMonth} kind="period" />
      <FindingSection title="Dia da semana" finding={store.weekday} kind="weekday" />
    </View>
  );
}

function FindingSection({ title, finding, kind }: { title: string; finding: BestTimeFinding; kind: FindingKind }) {
  const styles = useStyles();
  return (
    <View style={styles.section}>
      <Text style={styles.sectionLabel}>{title}</Text>
      <Text style={[styles.finding, finding.status === 'PATTERN' && styles.findingPattern]}>
        {findingText(finding, kind)}
      </Text>
      {finding.groups.map(group => (
        <GroupRow key={group.key} group={group} highlighted={finding.best?.key === group.key} />
      ))}
    </View>
  );
}

function GroupRow({ group, highlighted }: { group: BestTimeGroup; highlighted: boolean }) {
  const styles = useStyles();
  const change = group.percentVsStoreAverage;
  const versus = change < 0 ? 'abaixo do normal' : change > 0 ? 'acima do normal' : 'no normal';
  return (
    <View style={styles.groupRow}>
      <Text style={[styles.groupLabel, highlighted && styles.groupHighlighted]}>{group.label}</Text>
      <Text style={[styles.muted, highlighted && styles.groupHighlighted]}>
        {change === 0 ? versus : `${formatPercent(change)} ${versus}`} · {group.samples}{' '}
        {group.samples === 1 ? 'compra' : 'compras'}
      </Text>
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { flex: 1, backgroundColor: colors.surface },
  loading: { marginTop: spacing.xl },
  list: { padding: spacing.md, gap: spacing.md },
  header: { gap: spacing.md },
  card: { backgroundColor: colors.background, borderRadius: 12, padding: spacing.md },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: colors.text, marginTop: spacing.sm },
  text: { fontSize: 15, color: colors.text, lineHeight: 21 },
  paragraph: { marginTop: spacing.sm },
  muted: { fontSize: 13, color: colors.textMuted, lineHeight: 18 },
  storeName: { fontSize: 16, fontWeight: '700', color: colors.text },
  section: { marginTop: spacing.md, paddingTop: spacing.sm, borderTopWidth: 1, borderTopColor: colors.border },
  sectionLabel: { fontSize: 13, fontWeight: '600', color: colors.textMuted, marginBottom: 2 },
  finding: { fontSize: 15, color: colors.text, marginBottom: spacing.xs },
  findingPattern: { fontWeight: '700', color: colors.primary },
  groupRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md, paddingVertical: 2 },
  groupLabel: { fontSize: 13, color: colors.text },
  groupHighlighted: { fontWeight: '700', color: colors.primary },
}));
