/** Only SEFAZ-SP pages (http or https) may load; anything else is refused instead of hanging. */
export function isSefazSpPage(url: string): boolean {
  return /^https?:\/\/([a-z0-9-]+\.)*fazenda\.sp\.gov\.br(\/|$|\?)/i.test(url) || url === 'about:blank';
}
