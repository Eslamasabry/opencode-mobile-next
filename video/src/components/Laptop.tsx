import React from "react";
import { colors, DESKTOP } from "../config";

export const LAPTOP_BEZEL = 22;
export const LAPTOP_W = DESKTOP.w + LAPTOP_BEZEL * 2; // 1484
export const LAPTOP_H = DESKTOP.h + LAPTOP_BEZEL * 2 + 34; // lid + base

/** Laptop-style frame at native 1440x900 screen size; scale with a parent transform. */
export const Laptop: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div style={{ position: "relative", width: LAPTOP_W + 120, height: LAPTOP_H, marginLeft: -60 }}>
    <div
      style={{
        position: "absolute",
        left: 60,
        top: 0,
        width: LAPTOP_W,
        height: DESKTOP.h + LAPTOP_BEZEL * 2,
        borderRadius: 26,
        background: "#0B0D0B",
        boxShadow:
          "0 40px 100px rgba(0,0,0,0.55), inset 0 0 0 1.5px rgba(255,255,255,0.10), inset 0 0 0 4px rgba(0,0,0,0.6)",
      }}
    >
      <div
        style={{
          position: "absolute",
          top: LAPTOP_BEZEL,
          left: LAPTOP_BEZEL,
          width: DESKTOP.w,
          height: DESKTOP.h,
          borderRadius: 10,
          overflow: "hidden",
          background: colors.bg,
        }}
      >
        {children}
      </div>
      <div style={{ position: "absolute", top: 7, left: LAPTOP_W / 2 - 5, width: 10, height: 10, borderRadius: 5, background: "#050605", boxShadow: "inset 0 0 0 2px #1a1f1b" }} />
    </div>
    {/* base */}
    <div
      style={{
        position: "absolute",
        left: 0,
        top: DESKTOP.h + LAPTOP_BEZEL * 2 + 2,
        width: LAPTOP_W + 120,
        height: 30,
        borderRadius: "6px 6px 20px 20px",
        background: "linear-gradient(180deg, #1d221e 0%, #121613 60%, #0a0c0a 100%)",
        boxShadow: "0 30px 60px rgba(0,0,0,0.5)",
      }}
    >
      <div style={{ position: "absolute", left: (LAPTOP_W + 120) / 2 - 110, top: 0, width: 220, height: 8, borderRadius: "0 0 8px 8px", background: "#080a08" }} />
    </div>
  </div>
);
