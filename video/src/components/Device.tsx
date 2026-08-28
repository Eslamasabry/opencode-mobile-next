import {
  AbsoluteFill,
  Img,
  OffthreadVideo,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { theme } from "../theme";
import { WordReveal } from "./Text";
import type { Layout } from "../Showcase";

/** Rounded phone bezel around a 1080x2400 recording or still. */
export const DeviceFrame: React.FC<{
  src: string;
  still?: boolean;
  startFrom?: number;
  height: number;
  crop?: { scale: number; x: number; y: number };
}> = ({ src, still = false, startFrom = 0, height, crop }) => {
  const frame = useCurrentFrame();
  const breathe = 1 + Math.sin(frame / 30) * 0.004;
  const width = height * (1080 / 2400);
  const media = still ? (
    <Img
      src={staticFile(src)}
      style={{ width: "100%", height: "100%", objectFit: "cover" }}
    />
  ) : (
    <OffthreadVideo
      src={staticFile(src)}
      startFrom={startFrom}
      muted
      style={{ width: "100%", height: "100%", objectFit: "cover" }}
    />
  );
  return (
    <div
      style={{
        width,
        height,
        borderRadius: height * 0.045,
        border: `3px solid rgba(255,255,255,0.10)`,
        outline: `10px solid #060806`,
        overflow: "hidden",
        boxShadow: "0 40px 80px -20px rgba(0,0,0,0.6)",
        transform: `scale(${breathe})`,
        background: "#000",
      }}
    >
      <div
        style={{
          width: "100%",
          height: "100%",
          transform: crop
            ? `scale(${crop.scale}) translate(${crop.x}px, ${crop.y}px)`
            : undefined,
        }}
      >
        {media}
      </div>
    </div>
  );
};

export type CaptionLine = { text: string; hero?: string; at: number };

/**
 * The workhorse scene: device footage on one side, staggered captions on the
 * other (landscape) or below (vertical). Device enters with the emphasized
 * hero curve; captions word-reveal at their cue frames.
 */
export const DeviceScene: React.FC<{
  layout: Layout;
  src: string;
  still?: boolean;
  startFrom?: number;
  captions: CaptionLine[];
  crop?: { scale: number; x: number; y: number };
  deviceDelay?: number;
}> = ({ layout, src, still, startFrom, captions, crop, deviceDelay = 0 }) => {
  const frame = useCurrentFrame();
  const { fps, width, height } = useVideoConfig();
  const landscape = layout === "landscape";
  const p = spring({
    frame: frame - deviceDelay,
    fps,
    config: theme.spring.heroEntrance,
    durationInFrames: 24,
  });
  const slide = interpolate(p, [0, 1], [landscape ? 120 : 80, 0]);
  const deviceHeight = landscape ? height * 0.86 : height * 0.62;
  const captionBlock = (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 34,
        maxWidth: landscape ? width * 0.38 : width * 0.86,
      }}
    >
      {captions.map((line, i) => (
        <WordReveal
          key={i}
          text={line.text}
          heroWord={line.hero}
          delay={line.at}
          style={{
            fontFamily: theme.fonts.body,
            fontWeight: 600,
            fontSize: landscape ? 54 : 52,
            letterSpacing: "-0.02em",
            lineHeight: 1.12,
            color: theme.colors.text,
          }}
        />
      ))}
    </div>
  );
  return (
    <AbsoluteFill
      style={{
        flexDirection: landscape ? "row" : "column",
        alignItems: "center",
        justifyContent: "center",
        gap: landscape ? 110 : 48,
        padding: landscape ? 90 : 60,
      }}
    >
      {landscape ? captionBlock : null}
      <div
        style={{
          opacity: p,
          transform: `translate${landscape ? "X" : "Y"}(${slide}px)`,
        }}
      >
        <DeviceFrame
          src={src}
          still={still}
          startFrom={startFrom}
          height={deviceHeight}
          crop={crop}
        />
      </div>
      {landscape ? null : captionBlock}
    </AbsoluteFill>
  );
};
