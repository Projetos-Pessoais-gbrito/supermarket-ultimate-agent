import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing } from './theme';

export type ChartDatum = { key: string; label: string; value: number; accessibilityLabel?: string };

type ColumnChartProps = {
  data: ChartDatum[];
  /** Key drawn in the accent color (e.g. the current month); the rest recede to gray. */
  highlightKey: string;
  formatValue: (value: number) => string;
  height?: number;
};

/**
 * Single-series column chart: thin columns grown from one baseline, rounded data end,
 * emphasis on one column. Tap a column to read its value (the touch equivalent of a tooltip).
 */
export function ColumnChart({ data, highlightKey, formatValue, height = 140 }: ColumnChartProps) {
  const [selectedKey, setSelectedKey] = useState(highlightKey);
  const max = Math.max(...data.map(d => d.value), 0);
  const selected = data.find(d => d.key === selectedKey) ?? data[data.length - 1];

  return (
    <View>
      {selected && (
        <Text style={styles.readout}>
          {selected.accessibilityLabel ?? selected.label}: <Text style={styles.readoutValue}>{formatValue(selected.value)}</Text>
        </Text>
      )}
      <View style={[styles.plot, { height }]}>
        {data.map(datum => {
          const barHeight = max > 0 ? Math.max((datum.value / max) * height, datum.value > 0 ? 2 : 0) : 0;
          const highlighted = datum.key === highlightKey;
          return (
            <Pressable
              key={datum.key}
              accessibilityRole="button"
              accessibilityLabel={`${datum.accessibilityLabel ?? datum.label}: ${formatValue(datum.value)}`}
              onPress={() => setSelectedKey(datum.key)}
              style={styles.slot}
              hitSlop={4}>
              <View
                style={[
                  styles.column,
                  { height: barHeight, backgroundColor: highlighted ? colors.primary : colors.chartMuted },
                  datum.key === selected?.key && !highlighted && styles.columnSelected,
                ]}
              />
            </Pressable>
          );
        })}
      </View>
      <View style={styles.axis}>
        {data.map(datum => (
          <Text
            key={datum.key}
            style={[styles.axisLabel, datum.key === highlightKey && styles.axisLabelStrong]}
            numberOfLines={1}>
            {datum.label}
          </Text>
        ))}
      </View>
    </View>
  );
}

type BarListProps = {
  items: ChartDatum[];
  formatValue: (value: number) => string;
  /** Makes rows tappable (except the folded "Outros" row). */
  onPressItem?: (key: string) => void;
  /** Rows shown before folding the rest into "Outros". */
  maxRows?: number;
};

/** Ranked horizontal bars with the value written as text, one hue, largest first. */
export function BarList({ items, formatValue, onPressItem, maxRows = 5 }: BarListProps) {
  const sorted = [...items].sort((a, b) => b.value - a.value);
  const rows =
    sorted.length > maxRows
      ? [
          ...sorted.slice(0, maxRows - 1),
          {
            key: 'others',
            label: 'Outros',
            value: sorted.slice(maxRows - 1).reduce((sum, item) => sum + item.value, 0),
          },
        ]
      : sorted;
  const max = Math.max(...rows.map(row => row.value), 0);

  return (
    <View style={styles.list}>
      {rows.map(row => {
        const pressable = onPressItem !== undefined && row.key !== 'others';
        return (
          <Pressable
            key={row.key}
            disabled={!pressable}
            onPress={() => onPressItem?.(row.key)}
            accessibilityRole={pressable ? 'button' : undefined}
            accessibilityLabel={`${row.label}: ${formatValue(row.value)}`}
            accessibilityHint={pressable ? 'Mostra os produtos' : undefined}
            style={({ pressed }) => [styles.listRow, pressed && styles.listRowPressed]}>
            <View style={styles.listText}>
              <Text style={styles.listLabel} numberOfLines={1}>
                {row.label}
              </Text>
              <Text style={styles.listValue}>
                {formatValue(row.value)}
                {pressable ? '  ›' : ''}
              </Text>
            </View>
            <View style={styles.track}>
              <View style={[styles.bar, { width: `${max > 0 ? (row.value / max) * 100 : 0}%` }]} />
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  readout: { fontSize: 14, color: colors.textMuted, marginBottom: spacing.sm },
  readoutValue: { color: colors.text, fontWeight: '600' },
  plot: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  slot: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', height: '100%' },
  column: { width: '60%', maxWidth: 24, borderTopLeftRadius: 4, borderTopRightRadius: 4 },
  columnSelected: { backgroundColor: colors.textMuted },
  axis: { flexDirection: 'row', marginTop: spacing.xs },
  axisLabel: { flex: 1, textAlign: 'center', fontSize: 12, color: colors.textMuted },
  axisLabelStrong: { color: colors.text, fontWeight: '600' },
  list: { gap: spacing.md },
  listRow: { gap: spacing.xs },
  listRowPressed: { opacity: 0.6 },
  listText: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm },
  listLabel: { flex: 1, fontSize: 14, color: colors.text },
  listValue: { fontSize: 14, color: colors.text, fontWeight: '600' },
  track: { height: 8, borderRadius: 4, backgroundColor: colors.chartTrack, overflow: 'hidden' },
  bar: { height: 8, borderTopRightRadius: 4, borderBottomRightRadius: 4, backgroundColor: colors.primary },
});
