import { CameraView, scanFromURLAsync, useCameraPermissions, type BarcodeScanningResult } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useRouter } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useRef, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Linking, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage, KEY_ONLY_LINK_MESSAGE } from '../../api/messages';
import { photoImportSummary, pickReceiptQrCode, type PhotoOutcome } from '../../receipts/photoImport';
import { accessKeyFromLink, isKeyOnlyLink, isReceiptQrCode } from '../../receipts/qrCode';
import { useImportReceipt } from '../../receipts/queries';
import { Button, ErrorBanner, TextField } from '../../ui/components';
import { makeStyles, spacing, useColors } from '../../ui/theme';

// The loading layer sits on the camera image, always dark, in light and dark mode alike
const CAMERA_OVERLAY_TEXT = '#FFFFFF';

const NOT_A_RECEIPT = 'Esse QR code não é de uma nota fiscal (NFC-e). Procure o QR code no fim do cupom.';
const NO_QR_CODE =
  'Não encontramos um QR code nessa foto. Use uma foto nítida, com o QR code inteiro e sem reflexo.';
const MAX_PHOTOS = 20;

export default function ScanScreen() {
  const colors = useColors();
  const styles = useStyles();
  const router = useRouter();
  // Edge-to-edge: Android does not resize for the keyboard, so pad below the header (see AuthForm)
  const headerHeight = useHeaderHeight();
  const [permission, requestPermission] = useCameraPermissions();
  const importReceipt = useImportReceipt();
  const [error, setError] = useState<string | null>(null);
  const [pastedUrl, setPastedUrl] = useState('');
  const [scanPaused, setScanPaused] = useState(false);
  // Key of a key-only link: offers opening the SEFAZ page to solve the captcha
  const [captchaKey, setCaptchaKey] = useState<string | null>(null);
  // Camera callbacks fire many times per second, before state updates land; only the first read counts
  const handled = useRef(false);
  // Gallery import: photos read so far, and the summary once the batch is done
  const [photoProgress, setPhotoProgress] = useState<{ outcomes: PhotoOutcome[]; total: number } | null>(null);
  const [photoSummary, setPhotoSummary] = useState<string | null>(null);

  function submit(qrCodeUrl: string) {
    setCaptchaKey(null);
    setPhotoSummary(null);
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
      onSuccess: ({ receipt, alreadyImported }) => openReceipt(receipt.id, alreadyImported),
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

  /** Reads the QR code of each picked photo and imports them one by one through the same flow. */
  async function importPhotos() {
    const picked = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: MAX_PHOTOS,
      quality: 1,
    });
    if (picked.canceled || !picked.assets?.length) {
      return;
    }
    const photos = picked.assets.slice(0, MAX_PHOTOS);
    handled.current = true;
    setScanPaused(true);
    setError(null);
    setCaptchaKey(null);
    setPhotoSummary(null);

    const outcomes: PhotoOutcome[] = [];
    let lastError: string | null = null;
    let keyOnlyKey: string | null = null;
    let lastResult: Awaited<ReturnType<typeof importReceipt.mutateAsync>> | null = null;
    for (const photo of photos) {
      setPhotoProgress({ outcomes: [...outcomes], total: photos.length });
      const qrCodeUrl = await readReceiptQrCode(photo.uri);
      if (qrCodeUrl === null) {
        outcomes.push('noQrCode');
        lastError = NO_QR_CODE;
      } else if (!isReceiptQrCode(qrCodeUrl)) {
        outcomes.push('notReceipt');
        lastError = NOT_A_RECEIPT;
      } else if (isKeyOnlyLink(qrCodeUrl)) {
        outcomes.push('keyOnly');
        lastError = KEY_ONLY_LINK_MESSAGE;
        keyOnlyKey = accessKeyFromLink(qrCodeUrl);
      } else {
        try {
          lastResult = await importReceipt.mutateAsync(qrCodeUrl);
          outcomes.push(lastResult.alreadyImported ? 'alreadyImported' : 'imported');
        } catch (e) {
          outcomes.push('failed');
          lastError = errorMessage(e);
        }
      }
    }
    setPhotoProgress(null);
    handled.current = false;
    setScanPaused(false);

    // A single photo behaves like a scan: open the receipt, or explain what went wrong
    if (photos.length === 1 && lastResult) {
      openReceipt(lastResult.receipt.id, lastResult.alreadyImported);
      return;
    }
    if (photos.length > 1) {
      setPhotoSummary(photoImportSummary(outcomes));
    }
    setError(lastError);
    setCaptchaKey(keyOnlyKey);
  }

  function openReceipt(id: number, alreadyImported: boolean) {
    router.replace({
      pathname: '/receipts/[id]',
      params: { id: String(id), ...(alreadyImported ? { alreadyImported: '1' } : {}) },
    });
  }

  function scanAgain() {
    handled.current = false;
    setScanPaused(false);
    importReceipt.reset();
    setError(null);
  }

  const busy = importReceipt.isPending || photoProgress !== null;
  const waitingForRetry = scanPaused && !busy && error !== null;

  return (
    <KeyboardAvoidingView style={styles.container} behavior="padding" keyboardVerticalOffset={headerHeight}>
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
              <Text style={styles.overlayText}>
                {photoProgress
                  ? `Foto ${photoProgress.outcomes.length + 1} de ${photoProgress.total}…`
                  : 'Buscando a nota na SEFAZ…'}
              </Text>
              {photoProgress && photoProgress.outcomes.length > 0 && (
                <Text style={styles.overlayDetail}>
                  {photoImportSummary(photoProgress.outcomes, photoProgress.total)}
                </Text>
              )}
            </View>
          )}
        </View>

        <Text style={styles.hint}>Aponte a câmera para o QR code impresso no fim do cupom fiscal.</Text>

        <Button
          title="Escolher foto da galeria"
          variant="secondary"
          onPress={() => void importPhotos()}
          disabled={busy}
        />

        {photoSummary && (
          <View accessibilityRole="summary" style={styles.summary}>
            <Text style={styles.summaryText}>{photoSummary}</Text>
          </View>
        )}
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

/** The receipt link in a picked photo, or null when no QR code could be read from it. */
async function readReceiptQrCode(uri: string): Promise<string | null> {
  try {
    const codes = await scanFromURLAsync(uri, ['qr']);
    return pickReceiptQrCode(codes.map(code => code.data)) ?? codes[0]?.data ?? null;
  } catch {
    return null;
  }
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
  overlayDetail: { color: CAMERA_OVERLAY_TEXT, fontSize: 13, textAlign: 'center', paddingHorizontal: spacing.md },
  summary: { backgroundColor: colors.chartTrack, borderRadius: 12, padding: spacing.md },
  summaryText: { fontSize: 15, color: colors.text },
  hint: { fontSize: 14, color: colors.textMuted, textAlign: 'center' },
  permission: { padding: spacing.lg, gap: spacing.md, alignItems: 'stretch' },
  permissionText: { fontSize: 15, color: colors.text, textAlign: 'center' },
  manual: { marginTop: spacing.md },
}));
