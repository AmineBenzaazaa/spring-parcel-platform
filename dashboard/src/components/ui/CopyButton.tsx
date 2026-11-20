import { useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { useI18n } from '@/lib/i18n';

export function CopyButton({ value }: { value: string }) {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      title={copied ? t('actions.copied') : t('actions.copy')}
      aria-label={t('actions.copy')}
      onClick={() => {
        navigator.clipboard?.writeText(value).catch(() => {});
        setCopied(true);
        setTimeout(() => setCopied(false), 1200);
      }}
      className="focus-ring inline-flex h-5 w-5 items-center justify-center rounded text-faint hover:bg-subtle hover:text-fg"
    >
      {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
    </button>
  );
}
