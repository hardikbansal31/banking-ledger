/**
 * theme.js — Single source of truth for all design tokens.
 *
 * These values are injected as CSS custom properties in globals.css.
 * Import this file anywhere you need token values in JS
 * (e.g. chart colors, conditional inline styles).
 *
 * For CSS: use var(--color-primary) etc.
 * For JS:  import { colors } from '../theme'
 */

export const colors = {
  // Brand
  primary: "#F97316", // vivid orange
  primaryDark: "#EA580C", // hover / pressed
  primaryLight: "#FFF7ED", // tinted background for orange elements
  primaryBorder: "#FDBA74", // subtle orange border

  // Backgrounds
  bgPage: "#F8F8F6", // off-white page bg — warmer than pure white
  bgCard: "#FFFFFF", // card surfaces
  bgSubtle: "#F3F4F6", // input backgrounds, subtle fills
  bgDark: "#111827", // sidebar background

  // Text
  textPrimary: "#111827", // near-black body text
  textSecondary: "#6B7280", // muted labels, metadata
  textInverse: "#FFFFFF", // text on dark backgrounds
  textOrange: "#EA580C", // orange-tinted text (headings on light bg)

  // Borders
  border: "#E5E7EB", // default border
  borderFocus: "#F97316", // focus ring

  // Semantic — transaction states
  credit: "#16A34A", // green — money in
  creditBg: "#F0FDF4",
  creditBorder: "#BBF7D0",

  debit: "#DC2626", // red — money out
  debitBg: "#FEF2F2",
  debitBorder: "#FECACA",

  pending: "#D97706", // amber — in-flight
  pendingBg: "#FFFBEB",
  pendingBorder: "#FDE68A",

  settled: "#16A34A",
  failed: "#DC2626",
  authorized: "#2563EB",
  authorizedBg: "#EFF6FF",

  // Utility
  white: "#FFFFFF",
  black: "#000000",
  shadow: "rgba(0, 0, 0, 0.08)",
  shadowMd: "rgba(0, 0, 0, 0.12)",
  overlay: "rgba(0, 0, 0, 0.45)",
};

export const radius = {
  sm: "6px",
  md: "10px",
  lg: "14px",
  xl: "20px",
  full: "9999px",
};

export const shadows = {
  sm: "0 1px 3px rgba(0,0,0,0.08)",
  md: "0 4px 12px rgba(0,0,0,0.08)",
  lg: "0 8px 24px rgba(0,0,0,0.10)",
  card: "0 2px 8px rgba(0,0,0,0.06), 0 0 0 1px rgba(0,0,0,0.04)",
};

export const typography = {
  fontDisplay: "'DM Serif Display', Georgia, serif",
  fontBody: "'DM Sans', system-ui, sans-serif",
  fontMono: "'JetBrains Mono', 'Courier New', monospace",
};

export const spacing = {
  sidebarWidth: "240px",
  navHeight: "64px",
  contentMax: "1200px",
};

export const transitions = {
  fast: "120ms ease",
  normal: "200ms ease",
  slow: "350ms ease",
};
