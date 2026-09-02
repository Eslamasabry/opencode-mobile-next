import React from "react";
import { AbsoluteFill, useCurrentFrame } from "remotion";
import { colors, fonts, Layout } from "../config";
import { Backdrop, Lockup } from "./Intro";

export const Outro: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const vertical = layout === "vertical";
  return (
    <AbsoluteFill>
      <Backdrop />
      <Lockup
        frame={frame + 14}
        layout={layout}
        glyphSize={vertical ? 200 : 170}
        line={
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 18 }}>
            <div style={{ fontFamily: fonts.mono, fontWeight: 500, fontSize: vertical ? 32 : 30, color: colors.primary, letterSpacing: "0.02em" }}>
              Free · open source · alpha
            </div>
            <div style={{ fontFamily: fonts.mono, fontWeight: 400, fontSize: vertical ? 28 : 30, color: colors.muted }}>
              github.com/Eslamasabry/opencode-mobile-next
            </div>
          </div>
        }
      />
    </AbsoluteFill>
  );
};
