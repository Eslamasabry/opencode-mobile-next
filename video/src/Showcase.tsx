import { AbsoluteFill, Audio, Sequence, staticFile } from "remotion";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { S01Hook, S02Context, S03FirstRun, S04QuickAsk, S05LiveRun } from "./scenes/ScenesA";
import {
  S06ContextMeter,
  S07Picker,
  S08MissionControl,
  S09Notifications,
  S10HubMontage,
} from "./scenes/ScenesB";
import { S11Terminal, S12Themes, S13Desktop, S14Numbers, S15Cta } from "./scenes/ScenesC";
import { theme } from "./theme";

loadMono();
loadInter();

export type Layout = "landscape" | "vertical";

/** Storyboard frame map (docs/showcase-video-plan.md §2). */
export const SCENES = [
  { id: "hook", from: 0, to: 120, C: S01Hook },
  { id: "context", from: 120, to: 360, C: S02Context },
  { id: "firstRun", from: 360, to: 570, C: S03FirstRun },
  { id: "quickAsk", from: 570, to: 780, C: S04QuickAsk },
  { id: "liveRun", from: 780, to: 1380, C: S05LiveRun },
  { id: "contextMeter", from: 1380, to: 1680, C: S06ContextMeter },
  { id: "modelPicker", from: 1680, to: 1920, C: S07Picker },
  { id: "missionControl", from: 1920, to: 2220, C: S08MissionControl },
  { id: "notifications", from: 2220, to: 2520, C: S09Notifications },
  { id: "hubMontage", from: 2520, to: 3180, C: S10HubMontage },
  { id: "terminal", from: 3180, to: 3420, C: S11Terminal },
  { id: "themes", from: 3420, to: 3780, C: S12Themes },
  { id: "desktopTease", from: 3780, to: 3900, C: S13Desktop },
  { id: "numbers", from: 3900, to: 4200, C: S14Numbers },
  { id: "cta", from: 4200, to: 4500, C: S15Cta },
] as const;

const sfx = (name: string) => staticFile(`sfx/${name}.wav`);

/** Every HIT gets sound 2–3 frames before the visual lands (design rule). */
const SfxTrack: React.FC = () => (
  <>
    {/* Scene-entrance whooshes. */}
    {SCENES.filter((scene) => scene.from > 0).map((scene) => (
      <Sequence key={scene.id} from={scene.from - 3} durationInFrames={20}>
        <Audio src={sfx("whoosh-in")} volume={0.5} />
      </Sequence>
    ))}
    {/* Hook: claim word lands. */}
    <Sequence from={62} durationInFrames={24}>
      <Audio src={sfx("bass-hit")} volume={0.6} />
    </Sequence>
    {/* Montage beat swaps. */}
    {[1, 2, 3].map((i) => (
      <Sequence key={i} from={2520 + i * 165 - 2} durationInFrames={10}>
        <Audio src={sfx("click")} volume={0.6} />
      </Sequence>
    ))}
    {/* Riser into the numbers payoff, bass on the cut, ticks per counter. */}
    <Sequence from={3862} durationInFrames={38}>
      <Audio src={sfx("riser")} volume={0.55} />
    </Sequence>
    <Sequence from={3898} durationInFrames={24}>
      <Audio src={sfx("bass-hit")} volume={0.7} />
    </Sequence>
    {[0, 1, 2].map((i) => (
      <Sequence key={i} from={3912 + i * 6} durationInFrames={6}>
        <Audio src={sfx("tick")} volume={0.5} />
      </Sequence>
    ))}
    {/* CTA pop. */}
    <Sequence from={4206} durationInFrames={10}>
      <Audio src={sfx("pop")} volume={0.6} />
    </Sequence>
  </>
);

export const Showcase: React.FC<{ layout: Layout }> = ({ layout }) => (
  <AbsoluteFill style={{ background: theme.colors.bg }}>
    {SCENES.map(({ id, from, to, C }) => (
      <Sequence key={id} from={from} durationInFrames={to - from}>
        <C layout={layout} />
      </Sequence>
    ))}
    <SfxTrack />
  </AbsoluteFill>
);
