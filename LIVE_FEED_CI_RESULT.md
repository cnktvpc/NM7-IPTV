# NM7 IPTV Mobile 1.10.146 — CI result

- Result: **APK BUILD PASS / UNIT TEST WARNING**
- The exact source commit 4fc8b187 still uses TextureView in the mini-player XML. The newer SurfaceView-only assertion  fails on this requested baseline; it was kept non-blocking to avoid silently replacing the user's selected YouTube implementation.
- Unit tests must not be described as fully passing for this build.
- Branch: port/news-reader-4fc-stable142-20261022
- Source base: 4fc8b187db7e48591e1bcb11969436a56d27d06a
- Source commit: 2ca106dacf459be53b3a512741869ae527876677
- Workflow run: https://github.com/cnktvpc/NM7-IPTV/actions/runs/39867881644
- Artifact: [Download NM7-IPTV-Mobile-1.10.146-SOURCE4FC-NEWS-READER](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39867881644/artifacts/12465755222)
- Artifact ID: 12465755222

## SHA-256

```text
77219f781fb68b87197ca1caf6ebc507303bd6fee25af2d8ee5370feeaec7838  app-mobile-debug.apk
```

This is a candidate build. CI does not replace real-device playback validation.
