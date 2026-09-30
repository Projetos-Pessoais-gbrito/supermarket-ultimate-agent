import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

import { errorMessage } from '../../api/messages';
import { useImportReceiptPage } from '../../receipts/queries';
import { Button, ErrorBanner } from '../../ui/components';
import { colors, spacing } from '../../ui/theme';

const CONSULTATION_URL = 'https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaPublica.aspx?chNFe=';

// Runs after every page load: sends the page back once SEFAZ shows the receipt (its items table)
const DETECT_RECEIPT = `
  (function () {
    var hasReceipt = !!document.getElementById('tabResult');
    window.ReactNativeWebView.postMessage(JSON.stringify(
      hasReceipt ? { type: 'receipt', html: document.documentElement.outerHTML } : { type: 'page' }
    ));
  })();
  true;
`;

/**
 * The official SEFAZ "consulta por chave" page inside the app. The user solves the captcha; when the
 * receipt shows up, its page is sent to the backend and imported.
 */
export default function SefazConsultationScreen() {
  const router = useRouter();
  const { key } = useLocalSearchParams<{ key: string }>();
  const importPage = useImportReceiptPage();
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  // The detection script fires on every load; import only once
  const importing = useRef(false);

  function onMessage(event: WebViewMessageEvent) {
    let message: { type: string; html?: string };
    try {
      message = JSON.parse(event.nativeEvent.data);
    } catch {
      return;
    }
    if (message.type !== 'receipt' || !message.html || importing.current) {
      return;
    }
    importing.current = true;
    importPage.mutate(
      { accessKey: key, html: message.html },
      {
        onSuccess: receipt => router.replace({ pathname: '/receipts/[id]', params: { id: String(receipt.id) } }),
        onError: () => {
          importing.current = false;
        },
      },
    );
  }

  function tryAgain() {
    importPage.reset();
    importing.current = false;
    setReloadKey(value => value + 1);
  }

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ title: 'Consulta na SEFAZ' }} />
      <View style={styles.instructions}>
        <Text style={styles.instructionsTitle}>Resolva o captcha para importar a nota</Text>
        <Text style={styles.instructionsText}>
          Digite os caracteres da imagem e toque em Consultar. Quando a nota aparecer, ela é importada
          automaticamente.
        </Text>
        {importPage.error && (
          <>
            <ErrorBanner message={errorMessage(importPage.error)} />
            <Button title="Tentar de novo" variant="secondary" onPress={tryAgain} />
          </>
        )}
      </View>

      <View style={styles.page}>
        <WebView
          key={reloadKey}
          source={{ uri: CONSULTATION_URL + key }}
          injectedJavaScript={DETECT_RECEIPT}
          onMessage={onMessage}
          onLoadStart={() => setLoading(true)}
          onLoadEnd={() => setLoading(false)}
          // Only SEFAZ-SP pages, never navigate the user away from the official site
          originWhitelist={['https://www.nfce.fazenda.sp.gov.br', 'https://nfce.fazenda.sp.gov.br']}
          setSupportMultipleWindows={false}
        />
        {(loading || importPage.isPending) && (
          <View style={styles.overlay}>
            <ActivityIndicator size="large" color={colors.primary} />
            {importPage.isPending && <Text style={styles.overlayText}>Importando a nota…</Text>}
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  instructions: {
    padding: spacing.md,
    gap: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  instructionsTitle: { fontSize: 16, fontWeight: '700', color: colors.text },
  instructionsText: { fontSize: 14, color: colors.textMuted, lineHeight: 20 },
  page: { flex: 1 },
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(255,255,255,0.8)',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  overlayText: { fontSize: 16, color: colors.text },
});
