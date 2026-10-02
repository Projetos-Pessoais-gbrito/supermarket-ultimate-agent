import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { ApiError } from '../api/client';
import { errorMessage } from '../api/messages';
import type { SavedShoppingList } from '../api/shopping';
import { Button, ErrorBanner, TextField } from '../ui/components';
import { makeStyles, spacing, useColors } from '../ui/theme';
import { useApplySavedList, useRemoveSavedList, useSaveList, useSavedLists } from './queries';
import { addedItemsMessage, savedListSummary } from './text';

const DEFAULT_NAME = 'Compra do mês';

function savedListErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return 'Dê um nome à lista (até 80 letras) e tenha pelo menos um item nela.';
  }
  if (error instanceof ApiError && error.status === 404) {
    return 'Essa lista salva não existe mais. Puxe para atualizar.';
  }
  return errorMessage(error);
}

/** Named copies of the list ("Compra do mês") to reuse on the next shopping trip. */
export function SavedLists({ canSave }: { canSave: boolean }) {
  const styles = useStyles();
  const colors = useColors();
  const saved = useSavedLists();
  const save = useSaveList();
  const apply = useApplySavedList();
  const remove = useRemoveSavedList();
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState(DEFAULT_NAME);
  const [message, setMessage] = useState<string | null>(null);
  const mutationError = save.error ?? apply.error ?? remove.error;

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    save.mutate(trimmed, {
      onSuccess: list => {
        setNaming(false);
        setMessage(`Lista "${list.name}" salva com ${list.itemCount === 1 ? '1 item' : `${list.itemCount} itens`}.`);
      },
    });
  };

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Listas salvas</Text>
      <Text style={styles.muted}>
        Salve a lista para usar de novo no próximo mês. Salvar com o mesmo nome atualiza a lista.
      </Text>
      <ErrorBanner
        message={saved.error ? errorMessage(saved.error) : mutationError ? savedListErrorMessage(mutationError) : null}
      />
      {message && <Text style={styles.highlight}>{message}</Text>}
      {naming ? (
        <View>
          <TextField
            label="Nome da lista"
            value={name}
            onChangeText={setName}
            onSubmitEditing={submit}
            returnKeyType="done"
            maxLength={80}
            autoFocus
            selectTextOnFocus
          />
          <View style={styles.buttons}>
            <View style={styles.flex}>
              <Button title="Cancelar" variant="secondary" onPress={() => setNaming(false)} />
            </View>
            <View style={styles.flex}>
              <Button title="Salvar" onPress={submit} loading={save.isPending} disabled={!name.trim()} />
            </View>
          </View>
        </View>
      ) : (
        <Button
          title="Salvar lista atual"
          variant="secondary"
          disabled={!canSave}
          onPress={() => {
            setMessage(null);
            setNaming(true);
          }}
        />
      )}
      {saved.isPending ? (
        <ActivityIndicator color={colors.primary} />
      ) : (
        saved.data &&
        saved.data.length > 0 && (
          <View style={styles.card}>
            {saved.data.map(list => (
              <SavedListRow
                key={list.id}
                list={list}
                applying={apply.isPending && apply.variables === list.id}
                onApply={() =>
                  apply.mutate(list.id, { onSuccess: result => setMessage(addedItemsMessage(result)) })
                }
                onRemove={() => remove.mutate(list.id, { onSuccess: () => setMessage(null) })}
              />
            ))}
          </View>
        )
      )}
    </View>
  );
}

function SavedListRow({
  list,
  applying,
  onApply,
  onRemove,
}: {
  list: SavedShoppingList;
  applying: boolean;
  onApply: () => void;
  onRemove: () => void;
}) {
  const styles = useStyles();
  const colors = useColors();
  const [confirming, setConfirming] = useState(false);
  return (
    <View style={styles.row}>
      <View style={styles.flex}>
        <Text style={styles.name} numberOfLines={2}>
          {list.name}
        </Text>
        <Text style={styles.muted}>{savedListSummary(list.itemCount, list.updatedAt)}</Text>
      </View>
      {confirming ? (
        <>
          <Pressable accessibilityRole="button" hitSlop={8} onPress={() => setConfirming(false)}>
            <Text style={styles.action}>Manter</Text>
          </Pressable>
          <Pressable accessibilityRole="button" hitSlop={8} onPress={onRemove}>
            <Text style={[styles.action, { color: colors.danger }]}>Excluir</Text>
          </Pressable>
        </>
      ) : (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Usar a lista ${list.name}`}
            hitSlop={8}
            disabled={applying}
            onPress={onApply}>
            {applying ? <ActivityIndicator color={colors.primary} /> : <Text style={styles.action}>Usar</Text>}
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Excluir a lista ${list.name}`}
            hitSlop={8}
            onPress={() => setConfirming(true)}>
            <Ionicons name="trash-outline" size={20} color={colors.textMuted} />
          </Pressable>
        </>
      )}
    </View>
  );
}

const useStyles = makeStyles(colors => ({
  section: { gap: spacing.sm },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: colors.text },
  muted: { fontSize: 13, color: colors.textMuted },
  highlight: { fontSize: 14, fontWeight: '600', color: colors.primary },
  buttons: { flexDirection: 'row', gap: spacing.sm },
  flex: { flex: 1 },
  card: {
    backgroundColor: colors.background,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  name: { fontSize: 16, fontWeight: '600', color: colors.text },
  action: { fontSize: 15, fontWeight: '600', color: colors.primary },
}));
