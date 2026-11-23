import { useId, type ReactNode } from 'react';
import { Info } from 'lucide-react';
import { GLOSSARY, type TermKey } from '@/brand/suite-content';
import { useI18n } from '@/lib/i18n';

/** A domain word with an info tooltip from the shared MSE glossary (hover or keyboard focus). */
export function Term({ k, children, className }: { k: TermKey; children?: ReactNode; className?: string }) {
  const { lang } = useI18n();
  const id = useId();
  const entry = GLOSSARY[k][lang];
  return (
    <span className={`group/term relative inline-flex items-center gap-1 ${className ?? ''}`}>
      {children ?? entry.term}
      <button type="button" aria-label={`${entry.term}: ${entry.definition}`} aria-describedby={id} data-testid={`term-${k}`}
        className="focus-ring inline-grid h-4 w-4 shrink-0 place-items-center rounded-full text-faint hover:text-accent focus-visible:text-accent"
        onClick={(e) => e.stopPropagation()}>
        <Info className="h-3.5 w-3.5" aria-hidden />
      </button>
      <span role="tooltip" id={id}
        className="pointer-events-none invisible absolute start-0 top-full z-50 mt-1.5 w-64 rounded-lg border border-line bg-panel p-2.5 text-start text-[12px] font-normal normal-case leading-snug tracking-normal text-muted opacity-0 shadow-lift transition group-hover/term:visible group-hover/term:opacity-100 group-focus-within/term:visible group-focus-within/term:opacity-100">
        <span className="mb-0.5 block font-semibold text-fg">{entry.term}</span>
        {entry.definition}
      </span>
    </span>
  );
}

/** Same tooltip as Term, for a one-off explanation (board columns, buttons) instead of a glossary entry. */
export function Hint({ label, text, children, testId }: { label: string; text: string; children?: ReactNode; testId?: string }) {
  const id = useId();
  return (
    <span className="group/term relative inline-flex items-center gap-1">
      {children}
      <button type="button" aria-label={`${label}: ${text}`} aria-describedby={id} data-testid={testId}
        className="focus-ring inline-grid h-4 w-4 shrink-0 place-items-center rounded-full text-faint hover:text-accent focus-visible:text-accent">
        <Info className="h-3.5 w-3.5" aria-hidden />
      </button>
      <span role="tooltip" id={id}
        className="pointer-events-none invisible absolute start-0 top-full z-50 mt-1.5 w-60 whitespace-normal rounded-lg border border-line bg-panel p-2.5 text-start text-[12px] font-normal normal-case leading-snug tracking-normal text-muted opacity-0 shadow-lift transition group-hover/term:visible group-hover/term:opacity-100 group-focus-within/term:visible group-focus-within/term:opacity-100">
        {text}
      </span>
    </span>
  );
}
