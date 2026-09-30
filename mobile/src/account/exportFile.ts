import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

/** "meus-dados-2026-09-30.json" (local date) */
export function exportFileName(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `meus-dados-${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}.json`;
}

/** Writes the export to a temporary file and opens the share sheet (save to Files, Drive, e-mail...). */
export async function shareExport(data: unknown): Promise<void> {
  const file = new File(Paths.cache, exportFileName());
  if (file.exists) {
    file.delete();
  }
  file.create();
  file.write(JSON.stringify(data, null, 2));
  if (!(await Sharing.isAvailableAsync())) {
    throw new Error('Compartilhamento indisponível neste aparelho');
  }
  await Sharing.shareAsync(file.uri, {
    mimeType: 'application/json',
    UTI: 'public.json',
    dialogTitle: 'Exportar meus dados',
  });
}
