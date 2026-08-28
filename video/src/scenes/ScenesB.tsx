import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";
import { SceneShell } from "../components/Layers";
import { Counter, Entrance, WordReveal } from "../components/Text";
import { DeviceScene } from "../components/Device";
import type { Layout } from "../Showcase";

/** S06 CONTEXT METER 1380–1680: macro meter recreation over the run's end. */
export const S06ContextMeter: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const landscape = layout === "landscape";
  const fill = spring({
    frame: frame - 40,
    fps,
    config: { damping: 30, stiffness: 40 },
  });
  const barWidth = landscape ? 760 : 780;
  return (
    <SceneShell>
      <DeviceScene
        layout={layout}
        src="shots/still-rec3-end.png"
        still
        captions={[
          { text: "The line under the composer?", at: 12 },
          { text: "Your context window, filling.", hero: "context", at: 46 },
        ]}
      />
      <div
        style={{
          position: "absolute",
          bottom: landscape ? 90 : 170,
          left: 0,
          right: 0,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 20,
          opacity: interpolate(frame, [30, 55], [0, 1], {
            easing: theme.ease.signature,
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        <div
          style={{
            width: barWidth,
            height: 10,
            borderRadius: 5,
            background: "rgba(127,138,131,0.35)",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              width: `${interpolate(fill, [0, 1], [0, 34.7])}%`,
              height: "100%",
              borderRadius: 5,
              background: theme.colors.primary,
            }}
          />
        </div>
        <div
          style={{
            fontFamily: theme.fonts.mono,
            fontSize: landscape ? 40 : 36,
            color: theme.colors.textDim,
            fontVariantNumeric: "tabular-nums",
            display: "flex",
            gap: 14,
            alignItems: "baseline",
          }}
        >
          <Counter target={139} delay={40} suffix="k" fontSize={landscape ? 46 : 40} />
          <span>of 400k tokens · always visible</span>
        </div>
      </div>
    </SceneShell>
  );
};

/** S07 MODEL PICKER 1680–1920: rec4 — pinned apply bar. */
export const S07Picker: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell>
    <DeviceScene
      layout={layout}
      src="shots/rec4-picker.mp4"
      startFrom={210}
      captions={[
        { text: "351 models.", hero: "351", at: 16 },
        { text: "The apply button never scrolls away.", at: 70 },
      ]}
    />
  </SceneShell>
);

/** S08 MISSION CONTROL 1920–2220: rec13 — the fleet cockpit. */
export const S08MissionControl: React.FC<{ layout: Layout }> = ({ layout }) => (
  <SceneShell>
    <DeviceScene
      layout={layout}
      src="shots/rec13-mission-control.mp4"
      startFrom={100}
      captions={[
        { text: "Mission Control.", hero: "Control", at: 16 },
        { text: "Every session, every project, one glance.", at: 70 },
        { text: "Anything needing you rises to the top.", at: 180 },
      ]}
    />
  </SceneShell>
);

/** S09 NOTIFICATIONS 2220–2520: stylized recreation of the shade actions. */
export const S09Notifications: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const landscape = layout === "landscape";
  // The shade card slides down as rigid material: slower, zero overshoot.
  const drop = spring({
    frame: frame - 18,
    fps,
    config: theme.spring.heroEntrance,
    durationInFrames: 30,
  });
  const pulse =
    frame > 120 && frame < 186 ? 1 + Math.sin((frame - 120) / 10.5) * 0.03 : 1;
  const cardWidth = landscape ? 700 : 860;
  const button = (label: string, filled: boolean, scale = 1) => (
    <div
      style={{
        fontFamily: theme.fonts.body,
        fontWeight: 600,
        fontSize: 30,
        padding: "14px 30px",
        borderRadius: 999,
        color: filled ? "#052117" : theme.colors.text,
        background: filled ? theme.colors.primary : "rgba(255,255,255,0.08)",
        transform: `scale(${scale})`,
      }}
    >
      {label}
    </div>
  );
  return (
    <SceneShell>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: landscape ? "row" : "column",
          gap: landscape ? 110 : 54,
          padding: landscape ? 90 : 60,
        }}
      >
        <WordReveal
          text="Approve a tool from the shade. Without opening the app."
          delay={26}
          style={{
            fontFamily: theme.fonts.body,
            fontWeight: 600,
            fontSize: landscape ? 54 : 48,
            lineHeight: 1.12,
            color: theme.colors.text,
            maxWidth: landscape ? 640 : 880,
          }}
        />
        <div
          style={{
            width: cardWidth,
            opacity: drop,
            transform: `translateY(${interpolate(drop, [0, 1], [-90, 0])}px)`,
          }}
        >
          <div
            style={{
              background: "#1B211D",
              border: "1px solid rgba(255,255,255,0.09)",
              borderRadius: 26,
              padding: 34,
              boxShadow: "0 40px 80px -20px rgba(0,0,0,0.6)",
              display: "flex",
              flexDirection: "column",
              gap: 22,
            }}
          >
            <div
              style={{
                fontFamily: theme.fonts.body,
                fontSize: 27,
                color: theme.colors.textDim,
              }}
            >
              OpenCode · now
            </div>
            <div
              style={{
                fontFamily: theme.fonts.body,
                fontWeight: 600,
                fontSize: 33,
                color: theme.colors.text,
              }}
            >
              OpenCode needs permission
            </div>
            <div
              style={{
                fontFamily: theme.fonts.body,
                fontSize: 28,
                color: theme.colors.textDim,
              }}
            >
              Tap to review the pending request.
            </div>
            <div style={{ display: "flex", gap: 18, marginTop: 6 }}>
              {button("Allow once", true, pulse)}
              {button("Deny", false)}
              {button("Reply", false)}
            </div>
          </div>
        </div>
      </AbsoluteFill>
    </SceneShell>
  );
};

/** S10 HUB MONTAGE 2520–3180: four ~165f beats — More, Settings, Files, Review. */
const BEATS = [
  {
    src: "shots/rec7-more-hub.mp4",
    startFrom: 470,
    caption: "Organized, finally.",
    hero: "finally.",
  },
  {
    src: "shots/rec8-settings.mp4",
    startFrom: 120,
    caption: "Settings that end.",
    hero: "end.",
  },
  {
    src: "shots/rec9-files.mp4",
    startFrom: 900,
    caption: "A file browser that knows file types.",
    hero: "knows",
  },
  {
    src: "shots/rec10-review.mp4",
    startFrom: 1600,
    caption: "Review 250 files. It counts for you.",
    hero: "counts",
  },
];

export const S10HubMontage: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const per = 165;
  const index = Math.min(BEATS.length - 1, Math.floor(frame / per));
  const local = frame - index * per;
  const beat = BEATS[index];
  const enter = interpolate(local, [0, 12], [70, 0], {
    easing: theme.ease.signature,
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const opacity = interpolate(local, [0, 10], [0, 1], {
    easing: theme.ease.signature,
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <SceneShell>
      <div
        style={{
          position: "absolute",
          inset: 0,
          opacity,
          transform: `translateX(${enter}px)`,
        }}
      >
        <DeviceScene
          key={index}
          layout={layout}
          src={beat.src}
          startFrom={beat.startFrom}
          deviceDelay={0}
          captions={[{ text: beat.caption, hero: beat.hero, at: 14 }]}
        />
      </div>
      <Entrance delay={4} style={{ position: "absolute", top: 56, left: 0, right: 0 }}>
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: 14,
          }}
        >
          {BEATS.map((_, i) => (
            <div
              key={i}
              style={{
                width: i === index ? 44 : 12,
                height: 12,
                borderRadius: 6,
                background:
                  i === index ? theme.colors.primary : "rgba(255,255,255,0.18)",
              }}
            />
          ))}
        </div>
      </Entrance>
    </SceneShell>
  );
};
