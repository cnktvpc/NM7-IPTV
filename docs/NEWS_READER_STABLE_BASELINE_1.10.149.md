# NM7 IPTV Mobile 1.10.149 — Reader-only transplant

- Exact baseline commit: `4fc8b187db7e48591e1bcb11969436a56d27d06a`, identified in the successful 1.10.142 build record dated 2026-10-20.
- Baseline release: `stable/mobile-1.10.142`, versionName 1.10.142 / versionCode 158.
- Candidate branch: `rebuild/reader-only-from-stable-142-20261023`.
- Candidate version: 1.10.149 / versionCode 165.
- Scope: native Reader activities, extraction, category routing, plugin runtime/store, dependencies, tests and Reader navigation only.
- YouTube, floating mini-player, SurfaceView, Back handling and SmartTube patch scripts remain from the baseline commit; no candidate YouTube patch scripts are copied.
- Shared bottom bar uses baseline layout/padding; only Reader entry/launch is added.
- CI pass is required before APK readiness; device testing remains necessary.