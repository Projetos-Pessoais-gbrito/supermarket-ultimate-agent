import { CameraView, useCameraPermissions, type BarcodeScanningResult } from 'expo-camera';
import { Stack, useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Linking, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage, KEY_ONLY_LINK_MESSAGE } from '../../api/messages';
import { accessKeyFromLink, isKeyOnlyLink, isReceiptQrCode } from '../../receipts/qrCode';
import { useImportReceipt } from '../../receipts/queries';
import { Button, ErrorBanner, TextField } from '../../ui/components';
import { makeStyles, spacing, useColors } from '../../ui/theme';

// The loading layer sits on the camera image, always dark, in light and dark mode alike
const CAMERA_OVERLAY_TEXT = '#FFFFFF';

const NOT_A_RECEIPT = 'Esse QR code não é de uma nota fiscal (NFC-e). Procure o QR code no fim do cupom.';

export default function ScanScreen() {
  const colors = useColors();
  const styles = useStyles();
  const router = useRouter();
  const [permission, requestPermission] = useCameraPermissions();
  const importReceipt = useImportReceipt();
  const [error, setError] = useState<string | null>(null);
  const [pastedUrl, setPastedUrl] = useState('');
  const [scanPaused, setScanPaused] = useState(false);
  // Key of a key-only link: offers opening the SEFAZ page to solve the captcha
  const [captchaKey, setCaptchaKey] = useState<string | null>(null);
  // Camera callbacks fire many times per second, before state updates land; only the first read counts
  const handled = useRef(false);

  function submit(qrCodeUrl: string) {
    setCaptchaKey(null);
    if (!isReceiptQrCode(qrCodeUrl)) {
      setError(NOT_A_RECEIPT);
      return;
    }
    if (isKeyOnlyLink(qrCodeUrl)) {
      setError(KEY_ONLY_LINK_MESSAGE);
      setCaptchaKey(accessKeyFromLink(qrCodeUrl));
      return;
    }
    setError(null);
    importReceipt.mutate(qrCodeUrl, {
      onSuccess: ({ receipt, alreadyImported }) =>
        router.replace({
          pathname: '/receipts/[id]',
          params: { id: String(receipt.id), ...(alreadyImported ? { alreadyImported: '1' } : {}) },
        }),
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
              <ActivityIndicator size="large" color={CAMERA_OVERLAY_TEXT} />
              <Text style={styles.overlayText}>Buscando a nota na SEFAZ…</Text>
            </View>
          )}
        </View>

        <Text style={styles.hint}>Aponte a câmera para o QR code impresso no fim do cupom fiscal.</Text>

        <ErrorBanner message={error} />
        {captchaKey && (
          <Button
            title="Abrir na SEFAZ e resolver o captcha"
            onPress={() => router.push({ pathname: '/sefaz', params: { key: captchaKey } })}
          />
        )}
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
  const styles = useStyles();
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

const useStyles = makeStyles(colors => ({
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
  overlayText: { color: CAMERA_OVERLAY_TEXT, fontSize: 16 },
  hint: { fontSize: 14, color: colors.textMuted, textAlign: 'center' },
  permission: { padding: spacing.lg, gap: spacing.md, alignItems: 'stretch' },
  permissionText: { fontSize: 15, color: colors.text, textAlign: 'center' },
  manual: { marginTop: spacing.md },
}));
