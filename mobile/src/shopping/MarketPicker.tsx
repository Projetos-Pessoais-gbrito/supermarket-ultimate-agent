import { Pressable, ScrollView, Text, View } from 'react-native';

import type { Market } from '../api/shopping';
import { makeStyles, spacing } from '../ui/theme';

/**
 * Where the user plans to shop: the markets they have receipts from. Tapping the chosen one again
 * clears it, so the list shows no prices.
 */
export function MarketPicker({
  markets,
  value,
  onChange,
}: {
  markets: Market[];
  value: string | null;
  onChange: (market: string | null) => void;
}) {
  const styles = useStyles();
  if (markets.length === 0) {
    return <Text style={styles.muted}>Importe uma nota para ver os preços dos itens aqui.</Text>;
  }
  return (
    <View style={styles.container}>
      <Text style={styles.label}>Onde você vai comprar?</Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.chips}
        keyboardShouldPersistTaps="handled">
        {markets.map(market => {
          const active = market.name === value;
          return (
            <Pressable
              key={market.name}
              accessibilityRole="button"
              accessibilityState={{ selected: active }}
              accessibilityLabel={`Mercado ${market.name}${active ? ', toque para não usar preços' : ''}`}
              onPress={() => onChange(active ? null : market.name)}
              style={[styles.chip, active && styles.chipActive]}>
              <Text style={[styles.chipText, active && styles.chipTextActive]}>
                {active ? '✓ ' : ''}
                {market.name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  container: { gap: spacing.xs },
  label: { fontSize: 14, fontWeight: '600', color: colors.text },
  muted: { fontSize: 14, color: colors.textMuted },
  chips: { gap: spacing.sm, alignItems: 'center' },
  chip: {
    minHeight: 36,
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.background,
  },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 14, color: colors.text },
  chipTextActive: { color: colors.background, fontWeight: '600' },
}));
