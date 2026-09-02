import React from "react";
import { interpolate, useCurrentFrame } from "remotion";
import { colors, ease, fonts, Layout } from "../config";

/**
 * Typography-led caption: one big display line + optional mono sub-line.
 * In: 12-frame slide+fade. Out: 6-frame fade at the end of `duration`.
 */
export const Caption: React.FC<{
  text: string;
  sub?: string;
  duration: number;
  layout?: Layout;
  size?: number;
  align?: "left" | "center";
  style?: React.CSSProperties;
}> = ({ text, sub, duration, layout = "landscape", size, align, style }) => {
  const frame = useCurrentFrame();
  const inP = interpolate(frame, [0, 12], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.emph });
  const outP = interpolate(frame, [duration - 6, duration], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.exit });
  const opacity = inP * outP;
  const y = (1 - inP) * 34;
  const subIn = interpolate(frame, [6, 18], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.emph });
  const vertical = layout === "vertical";
  const fs = size ?? (vertical ? 78 : 84);
  const centered = align === "center" || (vertical && align !== "left");

  return (
    <div
      style={{
        opacity,
        transform: `translateY(${y}px)`,
        display: "flex",
        flexDirection: "column",
        alignItems: centered ? "center" : "flex-start",
        textAlign: centered ? "center" : "left",
        ...style,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "stretch",
          gap: 28,
        }}
      >
        {!centered && (
          <div
            style={{
              width: 7,
              borderRadius: 4,
              background: colors.primary,
              transform: `scaleY(${inP})`,
              transformOrigin: "top",
              boxShadow: `0 0 24px ${colors.glow}`,
            }}
          />
        )}
        <div
          style={{
            fontFamily: fonts.display,
            fontWeight: 700,
            fontSize: fs,
            lineHeight: 1.02,
            letterSpacing: "-0.025em",
            color: colors.text,
            maxWidth: vertical ? 900 : 880,
          }}
        >
          {text}
        </div>
      </div>
      {sub ? (
        <div
          style={{
            marginTop: 26,
            marginLeft: centered ? 0 : 35,
            fontFamily: fonts.mono,
            fontWeight: 500,
            fontSize: vertical ? 30 : 28,
            color: colors.muted,
            opacity: subIn,
            transform: `translateY(${(1 - subIn) * 12}px)`,
            letterSpacing: "0.01em",
          }}
        >
          {sub}
        </div>
      ) : null}
    </div>
  );
};
