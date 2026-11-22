/**
 * MSE Transport Tailwind (v3) theme extension, direction A "Route".
 * Spread into `theme.extend` of each React app:
 *   import { brandTheme } from './src/brand/tailwind.tokens.js'
 *   theme: { extend: { ...brandTheme, ... } }
 * Pair with tokens.css, which defines the CSS variables for light and dark.
 */
const v = (name) => `rgb(var(--${name}) / <alpha-value>)`;

export const brandTheme = {
  fontFamily: {
    sans: ['"Inter Variable"', '"IBM Plex Sans Arabic"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
    arabic: ['"IBM Plex Sans Arabic"', '"Inter Variable"', 'sans-serif'],
    mono: ['"JetBrains Mono Variable"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
  },
  colors: Object.fromEntries(
    ['bg', 'panel', 'subtle', 'line', 'fg', 'muted', 'faint', 'accent', 'accent-fg', 'signal', 'signal-ink',
      'success', 'warn', 'danger', 'info', 'chart-2', 'brand-tile', 'brand-ink', 'brand-dot'].map((n) => [n, v(n)]),
  ),
};
