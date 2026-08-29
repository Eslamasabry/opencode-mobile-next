import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";
import { SceneShell } from "../components/Layers";
import { Caret, Entrance, TypeOn, WordReveal } from "../components/Text";
import { DeviceScene } from "../components/Device";
import type { Layout } from "../Showcase";

/** S01 HOOK 0–120: ❯ opencode types on; claim lands under it. */
export const S01Hook: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const landscape = layout === "landscape";
  const heroSize = landscape ? 150 : 108;
  const wordP = spring({
    frame: frame - 64,
    fps,
    config: theme.spring.heroEntrance,
    durationInFrames: 20,
  });
  return (
    <SceneShell>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "column",
          gap: 46,
        }}
      >
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span
            style={{
              fontFamily: theme.fonts.mono,
              fontWeight: 700,
              fontSize: heroSize,
              color: theme.colors.primary,
              textShadow: `0 0 60px ${theme.colors.glow}`,
              marginRight: 0.35 * heroSize,
            }}
          >
            ❯
          </span>
          <TypeOn
            text="opencode"
            delay={8}
            perChar={2}
            fontSize={heroSize}
            caret
          />
        </div>
        <div
          style={{
            opacity: wordP,
            transform: `translateY(${interpolate(wordP, [0, 1], [24, 0])}px) scale(${interpolate(wordP, [0, 1], [0.98, 1])})`,
          }}
        >
          <WordReveal
            text="Your coding agent. In your pocket."
            heroWord="pocket"
            delay={66}
            style={{
              fontFamily: theme.fonts.body,
              fontWeight: 600,
              fontSize: landscape ? 56 : 46,
              color: theme.colors.textDim,
              justifyContent: "center",
            }}
          />
        </div>
      </AbsoluteFill>
    </SceneShell>
  );
};

/** S02 CONTEXT 120–360: the desk problem, two staggered lines over drift. */
export const S02Context: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const landscape = layout === "landscape";
  const drift = interpolate(frame, [0, 240], [0, -60], {
    easing: theme.ease.inOut,
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
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
        <pre
          style={{
            position: "absolute",
            fontFamily: theme.fonts.mono,
            fontSize: 26,
            lineHeight: 1.7,
            color: theme.colors.textDim,
            opacity: 0.14,
            transform: `translateY(${drift}px)`,
          }}
        >
          {`$ opencode
❯ refactor the auth flow
  ⚙ edit lib/auth/session.ts
  ⚙ shell npm test
  ✓ 148 passing
❯ _`}
        </pre>
        <Entrance delay={10}>
          <WordReveal
            text="Your agent is mid-task on your desktop."
            delay={12}
            style={{
              fontFamily: theme.fonts.body,
              fontWeight: 600,
              fontSize: landscape ? 62 : 50,
              color: theme.colors.text,
              justifyContent: "center",
            }}
          />
        </Entrance>
        <Entrance delay={54}>
          <WordReveal
            text="You're not at your desk."
            heroWord="not"
            delay={58}
            style={{
              fontFamily: theme.fonts.body,
              fontWeight: 600,
              fontSize: landscape ? 62 : 50,
              color: theme.colors.textDim,
              justifyContent: "center",
            }}
          />
        </Entrance>
      </AbsoluteFill>
    </SceneShell>
  );
};

/** S03 FIRST RUN 360–570: rec1 window (typing → Test → success). */
export const S03FirstRun: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell>
    <DeviceScene
      layout={layout}
      src="shots/rec1-first-run.mp4"
      startFrom={400}
      captions={[
        { text: "Fresh install to connected in under a minute.", hero: "connected", at: 14 },
        { text: "Paste an address. It tests the connection for you.", at: 92 },
      ]}
    />
  </SceneShell>
);

/** S04 QUICK-ASK 570–780: rec2, the composer-first home. */
export const S04QuickAsk: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell>
    <DeviceScene
      layout={layout}
      src="shots/rec2-quick-ask.mp4"
      startFrom={40}
      captions={[{ text: "The home invites typing.", hero: "typing", at: 20 }]}
    />
  </SceneShell>
);

/** S05 LIVE RUN 780–1380: rec3 — send, caret, tools ticking. */
export const S05LiveRun: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const landscape = layout === "landscape";
  return (
    <SceneShell>
      <DeviceScene
        layout={layout}
        src="shots/rec3-live-run.mp4"
        startFrom={1150}
        captions={[
          { text: "Watch it work. Tool by tool.", hero: "work", at: 24 },
          { text: "Reads, searches, shell runs — grouped as they happen.", at: 260 },
        ]}
      />
      {/* Breathing caption pill during the long observation stretch. */}
      <div
        style={{
          position: "absolute",
          bottom: landscape ? 70 : 150,
          left: 0,
          right: 0,
          display: "flex",
          justifyContent: "center",
          opacity: interpolate(frame, [430, 460, 560, 585], [0, 1, 1, 0], {
            easing: theme.ease.signature,
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          transform: `translateY(${Math.sin(frame / 30) * 3}px)`,
        }}
      >
        <div
          style={{
            fontFamily: theme.fonts.mono,
            fontSize: landscape ? 34 : 30,
            fontWeight: 600,
            color: theme.colors.text,
            background: "rgba(21,26,23,0.92)",
            border: `1px solid ${theme.colors.primary}55`,
            borderRadius: 14,
            padding: "16px 28px",
          }}
        >
          Shell · Find files · mgrep
          <Caret size={1.15} />
        </div>
      </div>
    </SceneShell>
  );
};
