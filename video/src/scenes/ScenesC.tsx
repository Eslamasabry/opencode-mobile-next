import {
  AbsoluteFill,
  Img,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";
import { SceneShell } from "../components/Layers";
import { Caret, Counter, Entrance, TypeOn, WordReveal } from "../components/Text";
import { DeviceScene } from "../components/Device";
import type { Layout } from "../Showcase";

/** S11 TERMINAL 3180–3420: rec11, stronger grain, minimal chrome. */
export const S11Terminal: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell grain={0.08}>
    <DeviceScene
      layout={layout}
      src="shots/rec11-terminal.mp4"
      startFrom={780}
      captions={[
        { text: "A real terminal.", hero: "real", at: 16 },
        { text: "Cursor-safe across sleep.", at: 120 },
      ]}
    />
  </SceneShell>
);

/** S12 THEMES 3420–3780: rec12 pack cycling. */
export const S12Themes: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell>
    <DeviceScene
      layout={layout}
      src="shots/rec12-themes.mp4"
      startFrom={60}
      captions={[
        { text: "Your terminal, your colors.", hero: "your", at: 16 },
        { text: "Catppuccin. Gruvbox. Solarized. Or Android's own.", at: 120 },
      ]}
    />
  </SceneShell>
);

/** S13 DESKTOP TEASE 3780–3900: stylized Linux window, Ken Burns. */
export const S13Desktop: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const landscape = layout === "landscape";
  const scale = interpolate(frame, [0, durationInFrames], [1, 1.08], {
    easing: theme.ease.inOut,
  });
  const pan = interpolate(frame, [0, durationInFrames], [0, -18]);
  const winWidth = landscape ? 1150 : 900;
  return (
    <SceneShell>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "column",
          gap: 44,
        }}
      >
        <Entrance delay={4}>
          <div
            style={{
              width: winWidth,
              borderRadius: 18,
              overflow: "hidden",
              border: "1px solid rgba(255,255,255,0.10)",
              boxShadow: "0 40px 80px -20px rgba(0,0,0,0.6)",
              background: "#0C0F0D",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "14px 18px",
                background: "#151A17",
                borderBottom: "1px solid rgba(255,255,255,0.07)",
              }}
            >
              {["#FF5F57", "#FEBC2E", "#28C840"].map((c) => (
                <div
                  key={c}
                  style={{
                    width: 14,
                    height: 14,
                    borderRadius: 7,
                    background: c,
                    opacity: 0.85,
                  }}
                />
              ))}
              <span
                style={{
                  fontFamily: theme.fonts.mono,
                  fontSize: 20,
                  color: theme.colors.textDim,
                  marginLeft: 10,
                }}
              >
                OpenCode — Linux
              </span>
            </div>
            <div style={{ height: landscape ? 560 : 700, overflow: "hidden" }}>
              <Img
                src={staticFile("shots/still-workspace.png")}
                style={{
                  width: "100%",
                  objectFit: "cover",
                  objectPosition: "top",
                  transform: `scale(${scale}) translateX(${pan}px)`,
                }}
              />
            </div>
          </div>
        </Entrance>
        <WordReveal
          text="Oh — it runs on Linux too. Same codebase."
          heroWord="Linux"
          delay={26}
          style={{
            fontFamily: theme.fonts.body,
            fontWeight: 600,
            fontSize: landscape ? 50 : 44,
            color: theme.colors.text,
            justifyContent: "center",
          }}
        />
      </AbsoluteFill>
    </SceneShell>
  );
};

/** S14 NUMBERS 3900–4200: three real counters, biggest entrance. */
const STATS: Array<{ target: number; suffix: string; label: string }> = [
  { target: 524, suffix: "", label: "tests green" },
  { target: 188, suffix: "", label: "server operations mapped" },
  { target: 7, suffix: "", label: "previews in 48 hours" },
];

export const S14Numbers: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const landscape = layout === "landscape";
  return (
    <SceneShell>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: landscape ? "row" : "column",
          gap: landscape ? 130 : 70,
        }}
      >
        {STATS.map((stat, i) => {
          const p = spring({
            frame: frame - 10 - i * 6,
            fps,
            config: theme.spring.uiPop,
          });
          return (
            <div
              key={i}
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: 12,
                opacity: p,
                transform: `translateY(${interpolate(p, [0, 1], [60, 0])}px) scale(${interpolate(p, [0, 1], [0.85, 1])})`,
              }}
            >
              <Counter
                target={stat.target}
                delay={14 + i * 6}
                suffix={stat.suffix}
                fontSize={landscape ? 130 : 104}
              />
              <span
                style={{
                  fontFamily: theme.fonts.body,
                  fontWeight: 500,
                  fontSize: landscape ? 36 : 32,
                  color: theme.colors.textDim,
                }}
              >
                {stat.label}
              </span>
            </div>
          );
        })}
      </AbsoluteFill>
    </SceneShell>
  );
};

/** S15 CTA 4200–4500: identity close; caret blinks to the last frame. */
export const S15Cta: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const landscape = layout === "landscape";
  // Everything except the caret exits at f240; the caret holds to the end.
  const exitO = interpolate(frame, [durationInFrames - 60, durationInFrames - 42], [1, 0], {
    easing: theme.ease.exit,
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <SceneShell exit={false}>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "column",
          gap: 40,
        }}
      >
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span
            style={{
              fontFamily: theme.fonts.mono,
              fontWeight: 700,
              fontSize: landscape ? 120 : 92,
              color: theme.colors.primary,
              textShadow: `0 0 60px ${theme.colors.glow}`,
              marginRight: 34,
              opacity: exitO,
            }}
          >
            ❯
          </span>
          <div style={{ opacity: exitO }}>
            <TypeOn
              text="OpenCode mobile"
              delay={10}
              perChar={2}
              fontSize={landscape ? 120 : 92}
              caret={false}
            />
          </div>
          {/* The caret outlives the exit: it blinks to the last frame. */}
          <Caret size={landscape ? 4.6 : 3.5} />
        </div>
        <div style={{ opacity: exitO }}>
          <WordReveal
            text="Get the preview APK on GitHub."
            heroWord="GitHub."
            delay={54}
            style={{
              fontFamily: theme.fonts.body,
              fontWeight: 600,
              fontSize: landscape ? 52 : 44,
              color: theme.colors.text,
              justifyContent: "center",
            }}
          />
        </div>
        <Entrance delay={84} style={{ opacity: exitO }}>
          <span
            style={{
              fontFamily: theme.fonts.mono,
              fontSize: landscape ? 32 : 27,
              color: theme.colors.textDim,
            }}
          >
            github.com/Eslamasabry/opencode-mobile/releases
          </span>
        </Entrance>
      </AbsoluteFill>
    </SceneShell>
  );
};
