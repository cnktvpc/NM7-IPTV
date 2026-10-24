# NM7 TikTok OpenSDK backend

This Worker keeps the TikTok client secret off the APK and serves the recent-public-video list used by NM7's native vertical pager.

## Deploy

1. From this directory, run `npx wrangler deploy`.
2. Set the required secrets in the Worker:
   ```sh
   npx wrangler secret put TIKTOK_CLIENT_KEY
   npx wrangler secret put TIKTOK_CLIENT_SECRET
   npx wrangler secret put TIKTOK_SESSION_SECRET
   ```
   `TIKTOK_SESSION_SECRET` must be at least 32 characters. The redirect URI variable is in `wrangler.toml` and must match the redirect URI registered for the same TikTok Developer app.
3. In TikTok Developer Portal, register the NM7 Android app with its application ID and signing fingerprints; enable Login Kit and Display API. Request `user.info.basic` and `video.list`; TikTok approval and end-user authorization are required.
4. Build NM7 with the Worker URL and TikTok Client Key:
   ```sh
   gradle -PtiktokClientKey='<client-key>' -PtiktokApiBaseUrl='https://<your-worker-domain>' :app:assembleMobileDebug
   ```
   Do not put the TikTok Client Secret in Gradle properties, app resources, or the APK.
5. Confirm `GET /health` returns `configured: true`.

## Endpoints

- `POST /api/tiktok/mobile/exchange`: accepts the Login Kit authorization code and PKCE verifier, exchanges it server-side, fetches the basic profile, and returns an opaque AES-GCM-encrypted session token.
- `GET /api/tiktok/mobile/videos`: accepts the opaque token as a Bearer token, refreshes access tokens if needed, and returns recent public videos and their official `embed_link`.

## Important limits

TikTok Display API only returns videos for the authenticated user's account; it does not expose the For You recommendation feed, likes/follow/comment actions, or the complete TikTok app UI. The native vertical pager shows the authorized user's recent public posts with TikTok's official individual-post player. This is an official integration path, not a recreation of TikTok's proprietary For You app.
