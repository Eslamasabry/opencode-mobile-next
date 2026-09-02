import React from "react";
import { AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame } from "remotion";
import { Caption } from "../components/Caption";
import { Laptop, LAPTOP_H, LAPTOP_W } from "../components/Laptop";
import { colors, DESKTOP, desktopSourceFrame, ease, fonts, Layout, pad4, SCENES } from "../config";
import { Backdrop } from "./Intro";

/** Placeholder until the Linux capture lands (see DESKTOP in config.ts). */
const Placeholder: React.FC<{ frame: number }> = ({ frame }) => {
  const pulse = 0.5 + 0.5 * Math.sin(frame / 9);
  return (
    <AbsoluteFill style={{ background: colors.surface, alignItems: "center", justifyContent: "center" }}>
      <svg width="360" height="240" viewBox="0 0 360 240" style={{ opacity: 0.9 }}>
        <rect x="40" y="20" width="280" height="170" rx="14" fill="none" stroke={colors.primary} strokeWidth="4" />
        <rect x="56" y="36" width="248" height="138" rx="6" fill="rgba(131,205,170,0.08)" />
        <rect x="10" y="196" width="340" height="18" rx="6" fill="none" stroke={colors.primary} strokeWidth="4" />
        <rect x="150" y="196" width="60" height="6" fill={colors.primary} />
        <text x="180" y="112" textAnchor="middle" fill={colors.primary} fontFamily="JetBrains Mono, monospace" fontSize="22" opacity={0.6 + 0.4 * pulse}>
          $ opencode2 serve
        </text>
      </svg>
      <div style={{ marginTop: 28, fontFamily: fonts.display, fontWeight: 700, fontSize: 56, color: colors.text, letterSpacing: "-0.02em" }}>
        Linux desktop
      </div>
      <div style={{ marginTop: 12, fontFamily: fonts.mono, fontSize: 24, color: colors.dim }}>
        live capture pending
      </div>
    </AbsoluteFill>
  );
};

const Footage: React.FC<{ frame: number }> = ({ frame }) => {
  const { index, prev, mix } = desktopSourceFrame(frame);
  const img = (n: number) => (
    <Img src={staticFile(`${DESKTOP.dir}/${pad4(n)}.png`)} style={{ position: "absolute", inset: 0, display: "block", width: DESKTOP.w, height: DESKTOP.h }} />
  );
  return (
    <AbsoluteFill>
      {img(index)}
      {prev !== null && mix > 0 ? <div style={{ position: "absolute", inset: 0, opacity: mix }}>{img(prev)}</div> : null}
    </AbsoluteFill>
  );
};

export const Desktop: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const vertical = layout === "vertical";
  const p = interpolate(frame, [0, SCENES.desktop], [0, 1], { extrapolateRight: "clamp", easing: ease.inOut });
  const entrance = interpolate(frame, [0, 26], [0, 1], { extrapolateRight: "clamp", easing: ease.emph });
  const k = vertical ? 0.66 : 0.8;
  const w = (LAPTOP_W + 120) * k;
  const h = LAPTOP_H * k;
  const cx = vertical ? 540 : 960;
  const cy = vertical ? 1000 : 596;
  return (
    <AbsoluteFill>
      <Backdrop glow={0.7} />
      <div
        style={{
          position: "absolute",
          left: cx - w / 2,
          top: cy - h / 2,
          width: w,
          height: h,
          transform: `translateY(${(1 - entrance) * 40}px) scale(${(1 + 0.035 * p) * (0.97 + 0.03 * entrance)})`,
          opacity: entrance,
        }}
      >
        <div style={{ transform: `scale(${k})`, transformOrigin: "top left" }}>
          <Laptop>{DESKTOP.enabled ? <Footage frame={frame} /> : <Placeholder frame={frame} />}</Laptop>
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: vertical ? 0 : 64,
          right: vertical ? 0 : 64,
          top: vertical ? 300 : 56,
          display: "flex",
          justifyContent: vertical ? "center" : "flex-start",
        }}
      >
        <Caption text="Now on Linux too." sub="recorded live on a virtual display" duration={SCENES.desktop} layout={layout} size={vertical ? 78 : 66} />
      </div>
    </AbsoluteFill>
  );
};
