import type { Lang } from './i18n';

const locales: Record<Lang, string> = { en: 'en-GB', fr: 'fr-MA' };

/** COD amounts are always Moroccan dirhams. "1,249.50 MAD" / "1 249,50 MAD". */
export function formatMad(amount: number | null | undefined, lang: Lang = 'en', compact = false): string {
  if (amount == null) return '—';
  const n = new Intl.NumberFormat(locales[lang], {
    minimumFractionDigits: compact ? 0 : 2,
    maximumFractionDigits: compact ? 0 : 2,
  }).format(amount);
  return `${n} MAD`;
}

export function formatNumber(n: number, lang: Lang = 'en'): string {
  return new Intl.NumberFormat(locales[lang]).format(n);
}

export function formatTime(iso: string, lang: Lang = 'en', withMillis = false): string {
  const d = new Date(iso);
  const base = d.toLocaleTimeString(locales[lang], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
  return withMillis ? `${base}.${String(d.getMilliseconds()).padStart(3, '0')}` : base;
}

export function relativeTime(iso: string, lang: Lang = 'en', now = Date.now()): string {
  const diff = Math.round((new Date(iso).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locales[lang], { numeric: 'auto', style: 'short' });
  const abs = Math.abs(diff);
  if (abs < 45) return rtf.format(Math.round(diff), 'second');
  if (abs < 2700) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 64800) return rtf.format(Math.round(diff / 3600), 'hour');
  return rtf.format(Math.round(diff / 86400), 'day');
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('');
}
