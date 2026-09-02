import React from "react";
import { colors, fonts, PHONE } from "../config";

export const PHONE_BEZEL = 16;
export const PHONE_W = PHONE.w + PHONE_BEZEL * 2; // 617

const StatusBar: React.FC = () => (
  <div
    style={{
      position: "absolute",
      top: 0,
      left: 0,
      width: PHONE.w,
      height: PHONE.statusBar,
      background: colors.bg,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "0 26px",
      boxSizing: "border-box",
      color: colors.text,
      fontFamily: fonts.display,
      fontWeight: 500,
      fontSize: 19,
      letterSpacing: "0.01em",
    }}
  >
    <span style={{ marginTop: 5 }}>10:24</span>
    <svg width="90" height="18" viewBox="0 0 90 18" style={{ marginTop: 5 }}>
      {/* signal */}
      <rect x="0" y="12" width="3" height="5" rx="1" fill={colors.text} />
      <rect x="5" y="9" width="3" height="8" rx="1" fill={colors.text} />
      <rect x="10" y="6" width="3" height="11" rx="1" fill={colors.text} />
      <rect x="15" y="3" width="3" height="14" rx="1" fill={colors.text} />
      {/* wifi */}
      <path d="M26 8 a12 12 0 0 1 16 0" stroke={colors.text} strokeWidth="2.2" fill="none" strokeLinecap="round" />
      <path d="M29.5 11.5 a7 7 0 0 1 9 0" stroke={colors.text} strokeWidth="2.2" fill="none" strokeLinecap="round" />
      <circle cx="34" cy="15.5" r="1.8" fill={colors.text} />
      {/* battery */}
      <rect x="52" y="3.5" width="30" height="13" rx="3.5" stroke={colors.text} strokeWidth="1.8" fill="none" />
      <rect x="54.5" y="6" width="22" height="8" rx="1.5" fill={colors.primary} />
      <rect x="83.5" y="7.5" width="2.5" height="5" rx="1" fill={colors.text} />
    </svg>
  </div>
);

/**
 * Android-style device frame rendered at native footage scale (585 px wide
 * screen); scale it with a parent transform. `contentHeight` is the height of
 * the app content below the fake status bar.
 */
export const PhoneFrame: React.FC<{
  contentHeight: number;
  children: React.ReactNode;
}> = ({ contentHeight, children }) => {
  const screenH = contentHeight + PHONE.statusBar;
  const h = screenH + PHONE_BEZEL * 2;
  return (
    <div
      style={{
        position: "relative",
        width: PHONE_W,
        height: h,
        borderRadius: 74,
        background: "#0B0D0B",
        boxShadow:
          "0 40px 90px rgba(0,0,0,0.55), 0 8px 24px rgba(0,0,0,0.45), inset 0 0 0 1.5px rgba(255,255,255,0.10), inset 0 0 0 4px rgba(0,0,0,0.6)",
      }}
    >
      {/* side buttons */}
      <div style={{ position: "absolute", right: -5, top: 260, width: 5, height: 70, borderRadius: 3, background: "#1c211d" }} />
      <div style={{ position: "absolute", right: -5, top: 350, width: 5, height: 120, borderRadius: 3, background: "#1c211d" }} />
      <div
        style={{
          position: "absolute",
          top: PHONE_BEZEL,
          left: PHONE_BEZEL,
          width: PHONE.w,
          height: screenH,
          borderRadius: 60,
          overflow: "hidden",
          background: colors.bg,
        }}
      >
        <div style={{ position: "absolute", top: PHONE.statusBar, left: 0, width: PHONE.w, height: contentHeight, overflow: "hidden" }}>
          {children}
        </div>
        <StatusBar />
        {/* punch-hole camera */}
        <div style={{ position: "absolute", top: 8, left: PHONE.w / 2 - 7, width: 14, height: 14, borderRadius: 7, background: "#050605", boxShadow: "inset 0 0 0 2px #1a1f1b" }} />
        {/* glass sheen */}
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: 60,
            pointerEvents: "none",
            background: "linear-gradient(115deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0) 35%)",
          }}
        />
      </div>
    </div>
  );
};
