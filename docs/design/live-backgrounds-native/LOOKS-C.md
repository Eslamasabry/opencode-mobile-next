# Builder C looks

Sources: `native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/live/looks/`. Tests:
`native/ui/kit/src/desktopTest/kotlin/io/github/eslamasabry/oc/ui/kit/live/LiveLooksCTest.kt`.

| Look | One line | Still | 3 s |
|---|---|---|---|
| `matrixRain` | Half the columns carry small 3×5 pixel glyphs drifting down at 20–45 dp/s in the accent, a brighter head and a long fading trail; glyphs change every couple of seconds. | [dark](c-matrixRain-dark.png) · [light](c-matrixRain-light.png) | [dark](c-matrixRain-dark.gif) · [light](c-matrixRain-light.gif) |
| `blueprint` | A faint accent wash with a 12 dp grid, heavier lines every 60 dp, measurement ticks and a few registration rings, drifting ~2 dp/s under a wandering soft light. | [dark](c-blueprint-dark.png) · [light](c-blueprint-light.png) | [dark](c-blueprint-dark.gif) · [light](c-blueprint-light.gif) |
| `circuit` | Board traces on a 22 dp grid that always join up: long buses with short branches, ring pads at ends, dots at junctions; accent pulses run along some lanes at ~25 dp/s. | [dark](c-circuit-dark.png) · [light](c-circuit-light.png) | [dark](c-circuit-dark.gif) · [light](c-circuit-light.gif) |
| `oscilloscope` | A scope graticule (48 dp divisions, ticked centre axes) with one calm accent trace across the middle, a phosphor glow and a faint afterimage; it breathes, never flickers. | [dark](c-oscilloscope-dark.png) · [light](c-oscilloscope-light.png) | [dark](c-oscilloscope-dark.gif) · [light](c-oscilloscope-light.gif) |
| `radar` | Range rings and a crosshair above centre; a soft beam turns once every 19 s and a few contacts light up as it passes and fade (new ones each turn). | [dark](c-radar-dark.png) · [light](c-radar-light.png) | [dark](c-radar-dark.gif) · [light](c-radar-light.gif) |
| `honeycomb` | A fine 18 dp hexagon lattice like a carbon panel, each cell lit faintly from above; a wide soft accent band slides diagonally and lifts the edges it crosses. | [dark](c-honeycomb-dark.png) · [light](c-honeycomb-light.png) | [dark](c-honeycomb-dark.gif) · [light](c-honeycomb-light.gif) |
| `brushedMetal` | Fine horizontal brushed grain (two stretched noise scales and a hairline speckle), lit from the top, with a broad soft highlight gliding across it carrying a hint of accent. | [dark](c-brushedMetal-dark.png) · [light](c-brushedMetal-light.png) | [dark](c-brushedMetal-dark.gif) · [light](c-brushedMetal-light.gif) |

## Readability (measured on the rendered pixels, 3 times × 2 themes × 3 intensities)

Lowest contrast of secondary text (`text2`) over any background pixel; the plain ground gives about 7:1.

| Look | dark .7 / 1 / 1.6 | light .7 / 1 / 1.6 |
|---|---|---|
| matrixRain | 5.31 / 5.04 / 4.91 | 5.29 / 5.02 / 4.90 |
| blueprint | 6.26 / 5.62 / 5.13 | 5.99 / 5.68 / 5.20 |
| circuit | 5.74 / 5.31 / 5.04 | 5.70 / 5.34 / 5.05 |
| oscilloscope | 5.29 / 4.97 / 4.84 | 5.41 / 5.13 / 4.95 |
| radar | 5.31 / 5.04 / 4.90 | 5.44 / 5.15 / 4.97 |
| honeycomb | 5.63 / 5.21 / 4.97 | 5.55 / 5.20 / 4.96 |
| brushedMetal | 6.17 / 5.64 / 5.25 | 5.81 / 5.42 / 5.00 |

## Not measured yet (needs the emulator)

GPU frame time (`adb shell dumpsys gfxinfo <pkg> framestats`, 1080×2400) and UI-thread time per look: no Android SDK
or emulator in the builder's container. Budget from the brief: under 4 ms GPU and under 1 ms UI thread. Cost per
pixel, by construction: matrixRain, oscilloscope and radar are straight-line maths with no loops;
circuit four segment distances; honeycomb one hex lookup; blueprint one value-noise sample; brushedMetal two noise
samples and a hash. Each value-noise sample is four hashes; the shared output adds two hashes for dither. The heaviest
is likely brushedMetal.
