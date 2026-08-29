// theme.ts — single source of truth. NEVER inline colors/easings in scenes.
// Palette mirrors the app's own AppTheme (docs/showcase-video-plan.md §4).
import { Easing } from "remotion";

export const theme = {
  colors: {
    bg: "#101310",
    bgAlt: "#151A17",
    primary: "#83CDAA", // THE hero mint — max one element per frame
    accent: "#A8CBB9",
    text: "#E3E8E4",
    textDim: "#7F8A83",
    error: "#FFB4AB",
    glow: "rgba(131, 205, 170, 0.35)",
  },
  fonts: {
    display: "JetBrains Mono", // hero lines, 600–700
    body: "Inter", // captions
    mono: "JetBrains Mono",
  },
  ease: {
    signature: Easing.bezier(0.4, 0, 0.2, 1), // ~80% of moves (Premium archetype)
    emphasized: Easing.bezier(0.05, 0.7, 0.1, 1), // hero entrances only
    exit: Easing.bezier(0.3, 0, 1, 1), // exits, faster than entrances
    inOut: Easing.bezier(0.83, 0, 0.17, 1), // Ken Burns / long moves
  },
  spring: {
    heroEntrance: { damping: 200 }, // zero-overshoot premium entrance
    uiPop: { damping: 18, stiffness: 140, mass: 0.8 },
  },
  // Duration palette at 30fps (motion personality: Premium).
  dur: { quick: 8, standard: 14, slow: 24, entrance: 20, exit: 10 },
} as const;
