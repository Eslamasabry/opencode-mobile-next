import { continueRender, delayRender, staticFile } from "remotion";

let started = false;

const load = (family: string, file: string, weight: string) => {
  const handle = delayRender(`font ${file}`);
  const face = new FontFace(family, `url(${staticFile(`fonts/${file}`)})`, { weight });
  face
    .load()
    .then((f) => {
      document.fonts.add(f);
      continueRender(handle);
    })
    .catch(() => continueRender(handle));
};

export const loadFonts = () => {
  if (started || typeof document === "undefined") return;
  started = true;
  load("Space Grotesk", "SpaceGrotesk-Bold.ttf", "700");
  load("Space Grotesk", "SpaceGrotesk-Medium.ttf", "500");
  load("Space Grotesk", "SpaceGrotesk-Regular.ttf", "400");
  load("JetBrains Mono", "JetBrainsMono-Regular.ttf", "400");
  load("JetBrains Mono", "JetBrainsMono-Medium.ttf", "500");
};
