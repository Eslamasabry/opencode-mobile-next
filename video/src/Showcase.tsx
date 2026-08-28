import { Sequence } from "remotion";
import { S01Hook } from "./scenes/S01Hook";

export type Layout = "landscape" | "vertical";

// Storyboard frame map (plan §2). Scenes S02–S15 are built during the
// scene-building step against recorded footage; the map is the contract.
export const SCENES = [
  { id: "hook", from: 0, to: 120 },
  { id: "context", from: 120, to: 360 },
  { id: "firstRun", from: 360, to: 570 },
  { id: "quickAsk", from: 570, to: 780 },
  { id: "liveRun", from: 780, to: 1380 },
  { id: "contextMeter", from: 1380, to: 1680 },
  { id: "modelPicker", from: 1680, to: 1920 },
  { id: "steal", from: 1920, to: 2220 },
  { id: "notifications", from: 2220, to: 2520 },
  { id: "hubMontage", from: 2520, to: 3180 },
  { id: "terminal", from: 3180, to: 3420 },
  { id: "themes", from: 3420, to: 3780 },
  { id: "desktopTease", from: 3780, to: 3900 },
  { id: "numbers", from: 3900, to: 4200 },
  { id: "cta", from: 4200, to: 4500 },
] as const;

export const Showcase: React.FC<{ layout: Layout }> = ({ layout }) => (
  <>
    <Sequence from={0} durationInFrames={120}>
      <S01Hook layout={layout} />
    </Sequence>
    {/* S02–S15 mount here as they are built. */}
  </>
);
