import React from "react";
import { AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame } from "remotion";
import { Caption } from "../components/Caption";
import { PhoneFrame, PHONE_BEZEL, PHONE_W } from "../components/PhoneFrame";
import { ease, Layout, PHONE, SCENES } from "../config";
import { Backdrop } from "./Intro";

const CONTENT_H = PHONE.h; // full still, nothing to crop
const DEVICE_H = CONTENT_H + PHONE.statusBar + PHONE_BEZEL * 2;

/** "Keeps going when you leave." — activity inbox still with a slow push. */
export const Inbox: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const vertical = layout === "vertical";
  const k = vertical ? 0.84 : 0.71;
  const cx = vertical ? 540 : 520;
  const cy = vertical ? 690 : 540;
  const p = interpolate(frame, [0, SCENES.inbox], [0, 1], { extrapolateRight: "clamp", easing: ease.inOut });
  const scale = 1 + 0.04 * p;
  return (
    <AbsoluteFill>
      <Backdrop glow={0.6} />
      <div
        style={{
          position: "absolute",
          left: cx - (PHONE_W * k) / 2,
          top: cy - (DEVICE_H * k) / 2,
          width: PHONE_W * k,
          height: DEVICE_H * k,
          transform: `translateY(${-p * 8}px) scale(${scale})`,
        }}
      >
        <div style={{ transform: `scale(${k})`, transformOrigin: "top left" }}>
          <PhoneFrame contentHeight={CONTENT_H}>
            <Img src={staticFile("shots/07-activity-inbox.png")} style={{ display: "block", width: PHONE.w, height: PHONE.h }} />
          </PhoneFrame>
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: vertical ? 0 : 900,
          right: vertical ? 0 : 80,
          top: vertical ? 1300 : 0,
          bottom: 0,
          display: "flex",
          alignItems: vertical ? "flex-start" : "center",
          justifyContent: vertical ? "center" : "flex-start",
          paddingTop: vertical ? 60 : 0,
        }}
      >
        <Caption text="Keeps going when you leave." sub="Needs attention · 2 — pick up where you left off" duration={SCENES.inbox} layout={layout} />
      </div>
    </AbsoluteFill>
  );
};
