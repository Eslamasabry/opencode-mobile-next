import React from "react";
import { AbsoluteFill, Audio, interpolate, Sequence, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { linearTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { colors, fonts, Layout, PHONE_BEATS, PHONE_TAPS, SCENES, sceneStarts, XFADE } from "./config";
import { Desktop } from "./scenes/Desktop";
import { Inbox } from "./scenes/Inbox";
import { Intro } from "./scenes/Intro";
import { Outro } from "./scenes/Outro";
import { PhoneAct } from "./scenes/PhoneAct";

const sfx = (name: string) => staticFile(`sfx/${name}.wav`);

const Footer: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [0, 18], [0, 1], { extrapolateRight: "clamp" });
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        bottom: layout === "vertical" ? 56 : 34,
        textAlign: "center",
        fontFamily: fonts.mono,
        fontSize: layout === "vertical" ? 22 : 21,
        color: colors.dim,
        opacity: o,
        padding: "0 60px",
      }}
    >
      Phone footage rendered from the app's real screens; desktop recorded live on Linux.
    </div>
  );
};

export const Demo: React.FC<{ layout: Layout }> = ({ layout }) => {
  const { durationInFrames } = useVideoConfig();
  const starts = sceneStarts();
  const xfade = () => (
    <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: XFADE })} />
  );

  return (
    <AbsoluteFill style={{ background: colors.bg }}>
      <TransitionSeries>
        <TransitionSeries.Sequence durationInFrames={SCENES.intro}>
          <Intro layout={layout} />
        </TransitionSeries.Sequence>
        {xfade()}
        <TransitionSeries.Sequence durationInFrames={SCENES.phone}>
          <PhoneAct layout={layout} />
        </TransitionSeries.Sequence>
        {xfade()}
        <TransitionSeries.Sequence durationInFrames={SCENES.inbox}>
          <Inbox layout={layout} />
        </TransitionSeries.Sequence>
        {xfade()}
        <TransitionSeries.Sequence durationInFrames={SCENES.desktop}>
          <Desktop layout={layout} />
        </TransitionSeries.Sequence>
        {xfade()}
        <TransitionSeries.Sequence durationInFrames={SCENES.outro}>
          <Outro layout={layout} />
        </TransitionSeries.Sequence>
      </TransitionSeries>

      {/* honesty footer, last 10 s */}
      <Sequence from={durationInFrames - 300} layout="none">
        <Footer layout={layout} />
      </Sequence>

      {/* sound design: no music, sparse sfx on beats */}
      <Sequence from={0} layout="none"><Audio src={sfx("whoosh-in")} volume={0.55} /></Sequence>
      {PHONE_BEATS.slice(1).map((b) => (
        <Sequence key={b.at} from={starts.phone + b.at} layout="none"><Audio src={sfx("tick")} volume={0.7} /></Sequence>
      ))}
      <Sequence from={starts.phone} layout="none"><Audio src={sfx("click")} volume={0.5} /></Sequence>
      {PHONE_TAPS.map((t) => (
        <Sequence key={t} from={starts.phone + t} layout="none"><Audio src={sfx("pop")} volume={0.6} /></Sequence>
      ))}
      <Sequence from={starts.inbox} layout="none"><Audio src={sfx("tick")} volume={0.7} /></Sequence>
      <Sequence from={starts.desktop} layout="none"><Audio src={sfx("whoosh-out")} volume={0.45} /></Sequence>
      <Sequence from={starts.outro} layout="none"><Audio src={sfx("bass-hit")} volume={0.4} /></Sequence>
    </AbsoluteFill>
  );
};
