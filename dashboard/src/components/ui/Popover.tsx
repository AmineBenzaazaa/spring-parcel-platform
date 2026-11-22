import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import clsx from 'clsx';

/**
 * Small accessible menu: a trigger button and a panel. Closes on Escape, outside click and
 * selection; focus moves into the panel on open and back to the trigger on Escape.
 */
export function Popover({ trigger, label, children, align = 'start', className, testId }: {
  trigger: (open: boolean) => ReactNode;
  label: string;
  children: (close: () => void) => ReactNode;
  align?: 'start' | 'end';
  className?: string;
  testId?: string;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => { if (!root.current?.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { setOpen(false); button.current?.focus(); }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        const items = Array.from(panel.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
        if (!items.length) return;
        e.preventDefault();
        const i = items.indexOf(document.activeElement as HTMLElement);
        const next = e.key === 'ArrowDown' ? (i + 1) % items.length : (i - 1 + items.length) % items.length;
        items[next].focus();
      }
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    panel.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey); };
  }, [open]);

  return (
    <div ref={root} className="relative">
      <button ref={button} type="button" aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined}
        aria-label={label} title={label} data-testid={testId} onClick={() => setOpen((o) => !o)}
        className="focus-ring rounded-lg">
        {trigger(open)}
      </button>
      {open && (
        <div ref={panel} id={id} role="menu" aria-label={label}
          className={clsx('absolute top-full z-50 mt-2 min-w-[240px] rounded-xl border border-line bg-panel p-1.5 shadow-lift',
            align === 'end' ? 'end-0' : 'start-0', className)}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}

export const menuItem =
  'flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-start text-[13px] text-fg outline-none hover:bg-subtle focus-visible:bg-subtle';
