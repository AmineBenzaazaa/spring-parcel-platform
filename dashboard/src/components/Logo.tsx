export function Logo({ size = 26 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className="shrink-0">
      <rect width="32" height="32" rx="8" className="fill-accent" />
      <path d="M9 12.5 16 9l7 3.5v7L16 23l-7-3.5z M9 12.5 16 16l7-3.5 M16 16v7" fill="none" className="stroke-accent-fg"
        strokeWidth="2" strokeLinejoin="round" />
    </svg>
  );
}
