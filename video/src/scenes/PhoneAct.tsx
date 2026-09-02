import React from "react";
import { AbsoluteFill, Img, interpolate, Sequence, staticFile, useCurrentFrame } from "remotion";
import { Caption } from "../components/Caption";
import { PhoneFrame, PHONE_BEZEL, PHONE_W } from "../components/PhoneFrame";
import { colors, ease, Layout, pad4, PHONE, PHONE_BEATS, PHONE_FOCUS, phoneSourceFrame, SCENES } from "../config";
import { Backdrop } from "./Intro";

const CONTENT_H = PHONE.h - PHONE.cropBottom; // 1206
const SCREEN_H = CONTENT_H + PHONE.statusBar; // 1236
const DEVICE_H = SCREEN_H + PHONE_BEZEL * 2;

/** Camera: continuous Ken Burns (1.00 <-> 1.04 per beat) + focus pushes. */
const useCamera = (frame: number) => {
  let beatIdx = 0;
  for (let i = 0; i < PHONE_BEATS.length; i++) if (frame >= PHONE_BEATS[i].at) beatIdx = i;
  const start = PHONE_BEATS[beatIdx].at;
  const end = beatIdx + 1 < PHONE_BEATS.length ? PHONE_BEATS[beatIdx + 1].at : SCENES.phone;
  const p = interpolate(frame, [start, end], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.inOut });
  const kb = beatIdx % 2 === 0 ? 1 + 0.04 * p : 1.04 - 0.04 * p;
  const drift = (beatIdx % 2 === 0 ? 1 : -1) * (p - 0.5) * 10;

  let zoomAmt = 0;
  let fy = 0;
  let focusZoom = 1;
  for (const f of PHONE_FOCUS) {
    const a = interpolate(frame, f.in, [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.std });
    const b = interpolate(frame, f.out, [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease.std });
    const amt = a * b;
    if (amt > 0) {
      zoomAmt = amt;
      focusZoom = f.zoom;
      // focus point, in unscaled device px relative to the device centre
      fy = f.y * CONTENT_H + PHONE.statusBar + PHONE_BEZEL - DEVICE_H / 2;
      break;
    }
  }
  const scale = kb * (1 + zoomAmt * (focusZoom - 1));
  // bring the focus point to the canvas centre as the push completes
  // (ty is in unscaled device px; caller multiplies by the layout scale)
  const ty = -zoomAmt * fy * scale;
  return { scale, tx: drift, ty, zoomAmt };
};

export const PhoneAct: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const vertical = layout === "vertical";
  const src = phoneSourceFrame(frame);
  const cam = useCamera(frame);
  const k = vertical ? 0.86 : 0.73;
  const cx = vertical ? 540 : 520;
  const cy = vertical ? 690 : 540;

  return (
    <AbsoluteFill>
      <Backdrop glow={0.6} />
      {/* soft ground glow behind the device */}
      <div
        style={{
          position: "absolute",
          left: cx - 420,
          top: cy - 380,
          width: 840,
          height: 760,
          borderRadius: "50%",
          background: "radial-gradient(closest-side, rgba(131,205,170,0.10), rgba(131,205,170,0))",
          filter: "blur(30px)",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: cx - (PHONE_W * k) / 2,
          top: cy - (DEVICE_H * k) / 2,
          width: PHONE_W * k,
          height: DEVICE_H * k,
          transform: `translate(${cam.tx}px, ${cam.ty * k}px) scale(${cam.scale})`,
          transformOrigin: "50% 50%",
        }}
      >
        <div style={{ transform: `scale(${k})`, transformOrigin: "top left" }}>
          <PhoneFrame contentHeight={CONTENT_H}>
            <Img
              src={staticFile(`${PHONE.dir}/${pad4(src)}.png`)}
              style={{ display: "block", width: PHONE.w, height: PHONE.h }}
            />
          </PhoneFrame>
        </div>
      </div>

      {/* captions */}
      {PHONE_BEATS.map((b, i) => {
        const next = i + 1 < PHONE_BEATS.length ? PHONE_BEATS[i + 1].at : SCENES.phone;
        return (
          <Sequence key={b.at} from={b.at} durationInFrames={next - b.at} layout="none">
            <div
              style={{
                position: "absolute",
                left: vertical ? 0 : 900,
                right: vertical ? 0 : 80,
                top: vertical ? 1300 : 0,
                bottom: vertical ? 0 : 0,
                display: "flex",
                alignItems: vertical ? "flex-start" : "center",
                justifyContent: vertical ? "center" : "flex-start",
                paddingTop: vertical ? 60 : 0,
              }}
            >
              <Caption text={b.text} sub={b.sub} duration={next - b.at} layout={layout} />
            </div>
          </Sequence>
        );
      })}
      {/* top-left running label */}
      <div
        style={{
          position: "absolute",
          right: vertical ? 48 : 64,
          top: vertical ? 48 : 52,
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: vertical ? 24 : 22,
          color: colors.dim,
          letterSpacing: "0.08em",
          textTransform: "uppercase",
          opacity: interpolate(frame, [8, 30], [0, 1], { extrapolateRight: "clamp" }) * (1 - cam.zoomAmt),
        }}
      >
        Android · OpenCode Mobile
      </div>
    </AbsoluteFill>
  );
};
