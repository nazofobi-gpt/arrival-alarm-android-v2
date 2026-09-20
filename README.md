# Arrival Alarm Android V2

Clean-room restart of the Arrival Alarm Android app.

## Baseline

- Public GitHub repository
- Android 16 / API 36
- Kotlin + Jetpack Compose + Material 3
- GitHub Actions as the primary build/test surface
- No source or commit history copied from the previous private repository

The first engineering gate is intentionally small: prove a reproducible API 36 source build on GitHub Actions, then add API 36 emulator instrumentation before product features expand.
