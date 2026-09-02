import React from "react";
import { interpolate } from "remotion";
import { colors, ease } from "../config";

/**
 * The app icon glyph (chevron + caret bar) as vector, so it can "draw in".
 * `progress` 0..1 drives the stroke draw; `fill` 0..1 the glow/tile fade.
 */
export const Glyph: React.FC<{ size: number; progress: number; fill: number }> = ({
  size,
  progress,
  fill,
}) => {
  const dash = interpolate(progress, [0, 1], [1, 0], { extrapolateRight: "clamp", extrapolateLeft: "clamp", easing: ease.emph });
  const bar = interpolate(progress, [0.55, 1], [0, 1], { extrapolateRight: "clamp", extrapolateLeft: "clamp", easing: ease.std });
  const tile = interpolate(fill, [0, 1], [0, 1], { extrapolateRight: "clamp", extrapolateLeft: "clamp" });
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" style={{ display: "block" }}>
      <defs>
        <filter id="glyphGlow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="3.5" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <rect
        x="2" y="2" width="96" height="96" rx="24"
        fill={colors.surface}
        stroke={colors.line}
        strokeWidth="1"
        opacity={tile}
      />
      <g filter="url(#glyphGlow)" opacity={0.55 + 0.45 * tile}>
        <polyline
          points="30,24 58,50 30,76"
          fill="none"
          stroke={colors.primary}
          strokeWidth="11"
          strokeLinecap="round"
          strokeLinejoin="round"
          pathLength={1}
          strokeDasharray={1}
          strokeDashoffset={dash}
        />
        <rect
          x="65" y="41" width="9" height="18" rx="4"
          fill={colors.primary}
          style={{ transform: `scaleY(${bar})`, transformOrigin: "69.5px 50px" }}
        />
      </g>
    </svg>
  );
};
