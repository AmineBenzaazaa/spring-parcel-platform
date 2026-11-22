import { brandTheme } from './src/brand/tailwind.tokens.js';

/** @type {import('tailwindcss').Config} */

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      ...brandTheme,
      boxShadow: {
        card: '0 1px 2px rgb(0 0 0 / 0.04), 0 1px 1px rgb(0 0 0 / 0.02)',
        lift: '0 8px 24px -6px rgb(0 0 0 / 0.18), 0 2px 6px rgb(0 0 0 / 0.06)',
      },
      keyframes: {
        pulseRing: {
          '0%': { transform: 'scale(1)', opacity: '0.6' },
          '100%': { transform: 'scale(2.4)', opacity: '0' },
        },
        flash: {
          '0%': { boxShadow: '0 0 0 0 rgb(var(--accent) / 0.55)' },
          '100%': { boxShadow: '0 0 0 10px rgb(var(--accent) / 0)' },
        },
        shimmer: { '100%': { transform: 'translateX(100%)' } },
      },
      animation: {
        'pulse-ring': 'pulseRing 1.6s cubic-bezier(0.2, 0.6, 0.4, 1) infinite',
        flash: 'flash 1.2s ease-out',
      },
    },
  },
  plugins: [],
};
