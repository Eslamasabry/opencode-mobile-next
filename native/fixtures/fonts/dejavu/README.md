# Linux desktop golden font inventory

These four unmodified DejaVu Sans 2.37 faces are test fixtures, licensed by the
Bitstream Vera font license in `LICENSE.txt`. `manifest.json` records their sizes,
SHA-256 digests, and distribution provenance. They are not application resources
and are not packaged into an Android or desktop app.

The Arabic settings/composer golden baselines were recorded with this fallback.
Compose Desktop 1.13 maps generic `FontFamily.SansSerif` on Linux to the ordered
aliases Noto Sans, DejaVu Sans, Arial. Installing Noto Sans can therefore change
both Latin and Arabic text metrics in an Arabic-locale capture even though the
production theme still asks for the same system sans family. In particular, the
What we collect screen contains English copy under its Arabic theme.

A diagnostic with only these four faces made all ten existing Arabic golden
comparisons pass on the VPS without changing any baseline image or tolerance.
`native/gradle/desktop-golden-fonts.gradle` preserves that recording environment
for the two modules' Linux `desktopTest` processes. Its declared preparation task
writes an isolated Fontconfig file under each module's `build/golden-fonts/`;
there are no system configuration includes or system font directories. The
runtime font cache is also confined to that module build directory.

English Geist and Geist Mono remain bundled Compose resources. Production
Android typography continues to use the device's system Arabic font; this setup
does not replace fonts in production or change non-Linux test environments.
