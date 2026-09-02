import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { Glyph } from "../components/Glyph";
import { colors, ease, fonts, Layout } from "../config";

export const Backdrop: React.FC<{ glow?: number }> = ({ glow = 1 }) => (
  <AbsoluteFill style={{ background: colors.bg }}>
    <AbsoluteFill
      style={{
        opacity: glow,
        background:
          "radial-gradient(60% 50% at 50% 45%, rgba(131,205,170,0.10) 0%, rgba(131,205,170,0.03) 45%, rgba(16,19,16,0) 75%)",
      }}
    />
  </AbsoluteFill>
);

/** Reusable brand lockup: glyph + wordmark + a line under it. */
export const Lockup: React.FC<{
  frame: number;
  layout: Layout;
  line: React.ReactNode;
  glyphSize?: number;
  draw?: boolean;
}> = ({ frame, layout, line, glyphSize, draw = true }) => {
  const vertical = layout === "vertical";
  const gs = glyphSize ?? (vertical ? 240 : 210);
  const progress = draw ? interpolate(frame, [4, 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) : 1;
  const fill = interpolate(frame, [26, 48], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.std });
  const rise = interpolate(frame, [0, 34], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.emph });
  const wm = interpolate(frame, [22, 40], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.emph });
  const tg = interpolate(frame, [36, 54], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.emph });
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", transform: `translateY(${(1 - rise) * 40}px)` }}>
        <div style={{ opacity: draw ? interpolate(frame, [0, 6], [0, 1], { extrapolateRight: "clamp" }) : 1 }}>
          <Glyph size={gs} progress={progress} fill={fill} />
        </div>
        <div
          style={{
            marginTop: vertical ? 44 : 36,
            fontFamily: fonts.display,
            fontWeight: 700,
            fontSize: vertical ? 92 : 100,
            letterSpacing: "-0.03em",
            color: colors.text,
            opacity: wm,
            transform: `translateY(${(1 - wm) * 24}px)`,
            whiteSpace: "nowrap",
          }}
        >
          OpenCode Mobile
        </div>
        <div
          style={{
            marginTop: vertical ? 22 : 16,
            opacity: tg,
            transform: `translateY(${(1 - tg) * 16}px)`,
            textAlign: "center",
          }}
        >
          {line}
        </div>
      </div>
    </AbsoluteFill>
  );
};

export const Intro: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill>
      <Backdrop glow={interpolate(frame, [0, 40], [0, 1], { extrapolateRight: "clamp" })} />
      <Lockup
        frame={frame}
        layout={layout}
        line={
          <div style={{ fontFamily: fonts.display, fontWeight: 500, fontSize: layout === "vertical" ? 40 : 38, color: colors.muted, letterSpacing: "-0.01em" }}>
            Your coding agent, in your pocket.
          </div>
        }
      />
    </AbsoluteFill>
  );
};
