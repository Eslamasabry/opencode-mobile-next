import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../theme";

/** Drifting radial mesh — never a flat background (skill rule 5). */
export const BgMesh: React.FC = () => {
  const frame = useCurrentFrame();
  const d1 = Math.sin(frame / 55) * 50;
  const d2 = Math.cos(frame / 70) * 40;
  return (
    <AbsoluteFill style={{ background: theme.colors.bg }}>
      <div
        style={{
          position: "absolute",
          width: 1200,
          height: 1200,
          borderRadius: "50%",
          top: -450,
          left: -300 + d1,
          filter: "blur(50px)",
          background: `radial-gradient(circle, ${theme.colors.primary}22, transparent 62%)`,
        }}
      />
      <div
        style={{
          position: "absolute",
          width: 900,
          height: 900,
          borderRadius: "50%",
          bottom: -400,
          right: -250 - d2,
          filter: "blur(70px)",
          background: `radial-gradient(circle, ${theme.colors.accent}18, transparent 65%)`,
        }}
      />
    </AbsoluteFill>
  );
};

export const Grade: React.FC = () => (
  <AbsoluteFill style={{ pointerEvents: "none" }}>
    <AbsoluteFill
      style={{
        backgroundColor: theme.colors.primary,
        mixBlendMode: "soft-light",
        opacity: 0.14,
      }}
    />
    <AbsoluteFill
      style={{
        background:
          "linear-gradient(180deg, rgba(0,0,0,0.10), transparent 28%, transparent 72%, rgba(0,0,0,0.2))",
      }}
    />
  </AbsoluteFill>
);

export const Grain: React.FC<{ opacity?: number }> = ({ opacity = 0.05 }) => {
  const frame = useCurrentFrame();
  const noise = `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='220' height='220'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='220' height='220' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E")`;
  return (
    <AbsoluteFill
      style={{
        pointerEvents: "none",
        backgroundImage: noise,
        backgroundSize: "220px",
        backgroundPosition: `${(frame * 7) % 220}px ${(frame * 13) % 220}px`,
        opacity,
        mixBlendMode: "overlay",
      }}
    />
  );
};

export const Vignette: React.FC = () => (
  <AbsoluteFill
    style={{
      pointerEvents: "none",
      background:
        "radial-gradient(ellipse at center, transparent 56%, rgba(0,0,0,0.22) 100%)",
    }}
  />
);

/** Scene exit: everything rises and fades in the last 12 frames (rule 4). */
export const useSceneExit = (): React.CSSProperties => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const y = interpolate(
    frame,
    [durationInFrames - 12, durationInFrames - 2],
    [0, -42],
    { easing: theme.ease.exit, extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );
  const o = interpolate(
    frame,
    [durationInFrames - 12, durationInFrames - 2],
    [1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );
  return { transform: `translateY(${y}px)`, opacity: o };
};

/** Five-layer stack wrapper: mesh → content → grade → grain → vignette. */
export const SceneShell: React.FC<{
  children: React.ReactNode;
  grain?: number;
  exit?: boolean;
}> = ({ children, grain = 0.05, exit = true }) => {
  const exitStyle = useSceneExit();
  return (
    <AbsoluteFill>
      <BgMesh />
      <AbsoluteFill style={exit ? exitStyle : undefined}>{children}</AbsoluteFill>
      <Grade />
      <Grain opacity={grain} />
      <Vignette />
    </AbsoluteFill>
  );
};
