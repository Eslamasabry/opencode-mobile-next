import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { theme } from "../theme";

/** Square-wave blinking block caret — the brand's signature. */
export const Caret: React.FC<{ size?: number; alive?: boolean }> = ({
  size = 1,
  alive = true,
}) => {
  const frame = useCurrentFrame();
  const on = !alive || frame % 33 < 18;
  return (
    <span
      style={{
        display: "inline-block",
        width: 0.55 * size * 16,
        height: 1.1 * size * 16,
        marginLeft: 0.18 * size * 16,
        borderRadius: 2 * size,
        background: theme.colors.primary,
        opacity: on ? 1 : 0.15,
        verticalAlign: "text-bottom",
      }}
    />
  );
};

/** Character type-on with trailing caret — the signature entrance. */
export const TypeOn: React.FC<{
  text: string;
  delay?: number;
  perChar?: number;
  fontSize: number;
  color?: string;
  caret?: boolean;
}> = ({ text, delay = 0, perChar = 2, fontSize, color = theme.colors.text, caret = true }) => {
  const frame = useCurrentFrame();
  const shown = Math.max(
    0,
    Math.min(text.length, Math.floor((frame - delay) / perChar)),
  );
  return (
    <div
      style={{
        fontFamily: theme.fonts.mono,
        fontWeight: 700,
        fontSize,
        letterSpacing: "-0.03em",
        lineHeight: 1.05,
        color,
        whiteSpace: "pre",
      }}
    >
      {text.slice(0, shown)}
      {caret ? <Caret size={fontSize / 26} /> : null}
    </div>
  );
};

/** Word-by-word reveal; one word may carry the hero color. */
export const WordReveal: React.FC<{
  text: string;
  delay?: number;
  per?: number;
  heroWord?: string;
  style?: React.CSSProperties;
}> = ({ text, delay = 0, per = 3, heroWord, style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: "0.26em", ...style }}>
      {text.split(" ").map((word, i) => {
        const p = spring({
          frame: frame - delay - i * per,
          fps,
          config: theme.spring.uiPop,
        });
        const hero = heroWord != null && word.replace(/[.,]/g, "") === heroWord;
        return (
          <span
            key={i}
            style={{
              display: "inline-block",
              opacity: p,
              transform: `translateY(${interpolate(p, [0, 1], [30, 0])}px)`,
              color: hero ? theme.colors.primary : undefined,
              textShadow: hero ? `0 0 60px ${theme.colors.glow}` : undefined,
            }}
          >
            {word}
          </span>
        );
      })}
    </div>
  );
};

/** Premium entrance: fade + rise + scale, zero overshoot. */
export const Entrance: React.FC<{
  delay?: number;
  children: React.ReactNode;
  style?: React.CSSProperties;
}> = ({ delay = 0, children, style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({
    frame: frame - delay,
    fps,
    config: theme.spring.heroEntrance,
    durationInFrames: 24,
  });
  return (
    <div
      style={{
        opacity: p,
        transform: `translateY(${interpolate(p, [0, 1], [40, 0])}px) scale(${interpolate(p, [0, 1], [0.96, 1])})`,
        ...style,
      }}
    >
      {children}
    </div>
  );
};

/** Animated counter with tabular numerals and a soft settle. */
export const Counter: React.FC<{
  target: number;
  delay?: number;
  suffix?: string;
  fontSize: number;
}> = ({ target, delay = 0, suffix = "", fontSize }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({
    frame: frame - delay,
    fps,
    config: { damping: 30, stiffness: 60 },
  });
  const value = Math.round(interpolate(p, [0, 1], [0, target]));
  return (
    <span
      style={{
        fontFamily: theme.fonts.mono,
        fontWeight: 700,
        fontSize,
        letterSpacing: "-0.03em",
        color: theme.colors.text,
        fontVariantNumeric: "tabular-nums",
      }}
    >
      {value}
      {suffix}
    </span>
  );
};
