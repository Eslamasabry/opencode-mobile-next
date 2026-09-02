import React from "react";
import { Composition } from "remotion";
import { Demo } from "./Demo";
import { FPS, totalDuration } from "./config";
import { loadFonts } from "./fonts";

loadFonts();

export const Root: React.FC = () => (
  <>
    <Composition
      id="Demo"
      component={Demo}
      durationInFrames={totalDuration()}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{ layout: "landscape" as const }}
    />
    <Composition
      id="DemoVertical"
      component={Demo}
      durationInFrames={totalDuration()}
      fps={FPS}
      width={1080}
      height={1920}
      defaultProps={{ layout: "vertical" as const }}
    />
  </>
);
