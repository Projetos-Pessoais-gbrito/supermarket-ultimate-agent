import { CameraView, useCameraPermissions, type BarcodeScanningResult } from 'expo-camera';
import { Stack, useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Linking, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../api/messages';
import { isReceiptQrCode } from '../../receipts/qrCode';
import { useImportReceipt } from '../../receipts/queries';
import { Button, ErrorBanner, TextField } from '../../ui/components';
import { colors, spacing } from '../../ui/theme';

const NOT_A_RECEIPT = 'Esse QR code não é de uma nota fiscal (NFC-e). Procure o QR code no fim do cupom.';

export default function ScanScreen() {
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const importReceipt = useImportReceipt();
  const [error, setError] = useState<string | null>(null);
  const [pastedUrl, setPastedUrl] = useState('');
  const [scanPaused, setScanPaused] = useState(false);
  // Camera callbacks fire many times per second, before state updates land; only the first read counts
  const handled = useRef(false);

  function submit(qrCodeUrl: string) {
    if (!isReceiptQrCode(qrCodeUrl)) {
      setError(NOT_A_RECEIPT);
      return;
    }
    setError(null);
    importReceipt.mutate(qrCodeUrl, {
      onSuccess: receipt => router.replace(`/receipts/${receipt.id}`),
      onError: e => setError(errorMessage(e)),
    });
  }

  function onBarcodeScanned({ data }: BarcodeScanningResult) {
    if (handled.current) {
      return;
    }
    handled.current = true;
    setScanPaused(true);
    submit(data);
  }

  function scanAgain() {
    handled.current = false;
    setScanPaused(false);
    importReceipt.reset();
    setError(null);
  }

  const busy = importReceipt.isPending;
  const waitingForRetry = scanPaused && !busy && error !== null;

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <Stack.Screen options={{ title: 'Escanear nota' }} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.cameraBox}>
          {!permission ? (
            <ActivityIndicator color={colors.primary} />
          ) : !permission.granted ? (
            <PermissionRequest canAskAgain={permission.canAskAgain} onRequest={requestPermission} />
          ) : (
            <CameraView
              style={StyleSheet.absoluteFill}
              facing="back"
              barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
              onBarcodeScanned={busy || scanPaused ? undefined : onBarcodeScanned}
            />
          )}
          {busy && (
            <View style={styles.overlay}>
              <ActivityIndicator size="large" color={colors.background} />
              <Text style={styles.overlayText}>Buscando a nota na SEFAZ…</Text>
            </View>
          )}
        </View>

        <Text style={styles.hint}>Aponte a câmera para o QR code impresso no fim do cupom fiscal.</Text>

        <ErrorBanner message={error} />
        {waitingForRetry && <Button title="Escanear novamente" variant="secondary" onPress={scanAgain} />}

        <View style={styles.manual}>
          <TextField
            label="Ou cole o link do QR code"
            value={pastedUrl}
            onChangeText={setPastedUrl}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            placeholder="https://www.nfce.fazenda.sp.gov.br/..."
          />
          <Button
            title="Importar link"
            onPress={() => submit(pastedUrl)}
            loading={busy}
            disabled={pastedUrl.trim().length === 0}
          />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function PermissionRequest({ canAskAgain, onRequest }: { canAskAgain: boolean; onRequest: () => void }) {
  return (
    <View style={styles.permission}>
      <Text style={styles.permissionText}>
        Precisamos da câmera para ler o QR code das suas notas fiscais.
      </Text>
      {canAskAgain ? (
        <Button title="Permitir câmera" onPress={onRequest} />
      ) : (
        <Button title="Abrir configurações" onPress={() => void Linking.openSettings()} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.md, gap: spacing.md },
  cameraBox: {
    aspectRatio: 1,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  overlayText: { color: colors.background, fontSize: 16 },
  hint: { fontSize: 14, color: colors.textMuted, textAlign: 'center' },
  permission: { padding: spacing.lg, gap: spacing.md, alignItems: 'stretch' },
  permissionText: { fontSize: 15, color: colors.text, textAlign: 'center' },
  manual: { marginTop: spacing.md },
});
