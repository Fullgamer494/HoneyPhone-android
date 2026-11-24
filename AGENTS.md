# HoneyPhone Android agent instructions

## APK delivery

When asked to build, update, or deliver an Android APK:

1. If the user asks for an APK without naming a variant, default to the signed `release` variant. Build `debug` only when the user explicitly asks for a test build; never present a debug or unsigned APK as a release.
2. Verify the final APK's signature, package ID, version name, and version code. Include its SHA-256 in the completion report.
3. Keep exactly one retained release APK at `../releases/HoneyPhone.apk` relative to this repository. Gradle's files under `app/build/outputs/` are temporary build outputs, not additional delivery copies; do not put debug builds in the release location.
4. Replace the previous canonical APK and remove other APKs from `../releases/` only after the new APK has built and passed verification. If signing configuration is missing or verification fails, preserve the existing canonical APK and report the blocker.
5. Do not upload to S3, install on a device, publish externally, or create commits unless the user explicitly asks.

## Repository hygiene

- Do not create commits unless explicitly requested. Do not commit Markdown files by default.
- Do not create or reintroduce `docs/` inside this application repository unless explicitly requested.
- Do not delete, move, or rename existing project files during an audit or cleanup plan until the user has asked for that cleanup to be applied.
