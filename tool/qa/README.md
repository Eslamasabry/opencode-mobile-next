# Verification runner

`run_serial_tests.py` discovers every `test/**/*_test.dart` file recursively,
sorts the manifest, and runs bounded chunks one at a time with
`flutter test --no-pub --concurrency=1`. It retains stdout/stderr logs and one
JSON result per chunk attempt under `build/traycer/serial-<run-id>/` by default.

Start a run with an explicit Flutter executable (use the full `.bat` path on
Windows):

```powershell
python tool/qa/run_serial_tests.py `
  --flutter 'C:\path\to\flutter.bat' `
  --chunk-size 25
```

Resume an interrupted or failed run:

```powershell
python tool/qa/run_serial_tests.py --resume build/traycer/serial-<run-id>
```

The default output location is `build/traycer/serial-<run-id>`.

`run.json`, `test-manifest.json`, and `source-manifest.json` are the immutable
run snapshot. Resume is refused if any file under `assets/`, `contracts/`,
`lib/`, `packages/`, or `test/`, or one of the root Flutter configuration files
(`analysis_options.yaml`, `l10n.yaml`, `pubspec.lock`, `pubspec.yaml`) changed.
Old chunk logs/results are retained; retries receive a new attempt number.
