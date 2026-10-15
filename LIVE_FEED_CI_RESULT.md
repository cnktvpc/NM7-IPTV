# NM7 IPTV Mobile 1.10.136 — STABLE release record

- Release status: **STABLE** (user tested and confirmed OK)
- Stable branch: `stable/mobile-1.10.136`
- Previous stable branch preserved unchanged: `stable/mobile-1.10.135`
- Historical stable branch preserved unchanged: `stable/mobile-1.10.128`
- Development branch tested: `fix/mobile-1.10.136-live-fullscreen-server`
- Feature commit: `a72bece44b89afa1bf4a79782ec6884f26739cc3`
- CI-validated commit: `1d74b8fa1f023c818b13f3f972316c1c3f70afff`
- GitHub Actions run: [38467292630 — SUCCESS](https://github.com/phuongnm7/iptv-player-android/actions/runs/38467292630)
- APK artifact: [NM7-IPTV-Mobile-1.10.136-LIVE-FULLSCREEN-SERVER](https://github.com/phuongnm7/iptv-player-android/actions/runs/38467292630/artifacts/11849217586)
- Artifact ID: `11849217586`

## SHA-256

```text
8dd23d2d80fbb569b008522ddf1d381d1b46ca71a561c8e0b5490c8c64688230  app-mobile-debug.apk
```

## Validation

- APK build: PASS
- Full Mobile unit-test suite: PASS
- Dedicated Live regression guard: 26 checks PASS
- Fullscreen server selector regression guard: 7 checks PASS
- Real-device user validation: OK

## Release summary

Fullscreen Live playback now has a server selector directly in the Media3 player controls. The selector is shown only when the active Live event has at least two sources and reuses the existing server picker and source-switching behavior. The ordinary inline picker and 1.10.135 Live duration/newest-first behavior remain intact.
