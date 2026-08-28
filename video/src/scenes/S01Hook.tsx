// Placeholder stub — the real scene is built in the scene-building step,
// AFTER reading references/motion-patterns.md, per the skill's workflow.
import { AbsoluteFill } from "remotion";
import { theme } from "../theme";
import type { Layout } from "../Showcase";

export const S01Hook: React.FC<{ layout: Layout }> = () => (
  <AbsoluteFill style={{ backgroundColor: theme.colors.bg }} />
);
