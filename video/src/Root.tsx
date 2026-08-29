import { Composition } from "remotion";
import { Showcase } from "./Showcase";

// 30fps × 4500f = 2:30, per docs/showcase-video-plan.md §1.
const FPS = 30;
const DURATION = 4500;

export const Root: React.FC = () => (
  <>
    <Composition
      id="ShowcaseLandscape"
      component={Showcase}
      durationInFrames={DURATION}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{ layout: "landscape" as const }}
    />
    <Composition
      id="ShowcaseVertical"
      component={Showcase}
      durationInFrames={DURATION}
      fps={FPS}
      width={1080}
      height={1920}
      defaultProps={{ layout: "vertical" as const }}
    />
  </>
);
