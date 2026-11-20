import type { ReactNode } from 'react';

export function EmptyState({ icon, title, hint, action }: { icon: ReactNode; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-8 text-center">
      <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl border border-line bg-subtle text-faint">
        {icon}
      </div>
      <p className="text-sm font-medium text-fg">{title}</p>
      {hint && <p className="mt-1 max-w-xs text-[13px] leading-relaxed text-muted">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
