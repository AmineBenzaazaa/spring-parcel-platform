import { BRAND } from '@/brand/brand';

/**
 * Brand mark: one route line drawing the M, ending in the destination dot (brand/logo/mse-mark.svg).
 * Colours come from the --brand-* tokens, so it switches to the dark variant with the theme.
 * `onBrand` is for the navy login panel: a translucent tile with a white route and a green dot.
 */
export function Logo({ size = 26, className, onBrand }: { size?: number; className?: string; onBrand?: boolean }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className={className ?? 'shrink-0'}>
      <rect width="32" height="32" rx="7" fill={onBrand ? 'rgb(255 255 255 / 0.12)' : 'rgb(var(--brand-tile))'} />
      <path d="M8 23.5V9.5l8 8.5 8-8.5v7.5" fill="none" stroke={onBrand ? '#fff' : 'rgb(var(--brand-ink))'} strokeWidth="3.3"
        strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="24" cy="23.4" r="2.7" fill={onBrand ? 'rgb(0 179 126)' : 'rgb(var(--brand-dot))'} />
    </svg>
  );
}

/** Mark + "MSE Product" lockup. */
export function Lockup({ size = 26, product = BRAND.product, className }: { size?: number; product?: string; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className ?? ''}`}>
      <Logo size={size} />
      <span className="whitespace-nowrap text-[15px] font-semibold tracking-tight">
        {BRAND.name} <span className="font-medium text-muted">{product}</span>
      </span>
    </span>
  );
}
