# Generated Android copy of `strings_os.xml`

`values*/strings_os.xml` here is written by `strings convert` (`:tools:strings`) from
`src/commonMain/composeResources/values*/strings_os.xml`, re-escaped for aapt2 (a literal `%` in a string with
arguments becomes `%%`). It is not a source set: ADR §7.2 keeps `androidMain` out of `:ui:words`. `:androidApp`
adds this folder to its `res` (plan 06 §13.2) so notifications, widgets, tiles and shortcuts read it through
`R.string`. Do not edit it; edit the CMP file and rerun `strings convert`. `strings check` fails when the two differ.
