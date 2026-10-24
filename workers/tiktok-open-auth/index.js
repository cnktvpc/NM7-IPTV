/**
 * NM7 TikTok OpenSDK backend.
 *
 * Required Cloudflare Worker secrets:
 *   TIKTOK_CLIENT_KEY, TIKTOK_CLIENT_SECRET, TIKTOK_SESSION_SECRET
 * Required plain-text variable:
 *   TIKTOK_REDIRECT_URI (must exactly match the redirect URI configured in TikTok Developer Portal)
 *
 * App flow:
 *   POST /api/tiktok/mobile/exchange
 *   GET  /api/tiktok/mobile/videos   Authorization: Bearer <opaque encrypted session token>
 *
 * The client secret never leaves this Worker. Session tokens are AES-GCM encrypted and
 * opaque; the Android client stores them encrypted again with Android Keystore.
 */
const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,OPTIONS",
  "access-control-allow-headers": "content-type,authorization",
  "access-control-max-age": "86400",
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...CORS },
  });
}

function b64url(bytes) {
  let binary = "";
  for (const value of bytes) binary += String.fromCharCode(value);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromB64url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, ch => ch.charCodeAt(0));
}

async function sessionKey(secret) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

async function sealSession(env, value) {
  if (!env.TIKTOK_SESSION_SECRET || env.TIKTOK_SESSION_SECRET.length < 32) {
    throw new Error("TIKTOK_SESSION_SECRET must contain at least 32 characters");
  }
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await sessionKey(env.TIKTOK_SESSION_SECRET);
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(value)));
  const combined = new Uint8Array(iv.length + encrypted.byteLength);
  combined.set(iv, 0);
  combined.set(new Uint8Array(encrypted), iv.length);
  return b64url(combined);
}

async function openSession(env, token) {
  if (!env.TIKTOK_SESSION_SECRET || env.TIKTOK_SESSION_SECRET.length < 32) {
    throw new Error("TIKTOK_SESSION_SECRET is not configured");
  }
  const combined = fromB64url(token);
  if (combined.length < 29) throw new Error("invalid session token");
  const iv = combined.slice(0, 12);
  const encrypted = combined.slice(12);
  const key = await sessionKey(env.TIKTOK_SESSION_SECRET);
  const decoded = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, encrypted);
  return JSON.parse(new TextDecoder().decode(decoded));
}

async function exchangeCode(env, input) {
  if (!env.TIKTOK_CLIENT_KEY || !env.TIKTOK_CLIENT_SECRET || !env.TIKTOK_SESSION_SECRET || !env.TIKTOK_REDIRECT_URI) {
    throw new Error("TikTok Worker secrets/redirect URI are not configured");
  }
  if (input.redirect_uri !== env.TIKTOK_REDIRECT_URI) {
    throw new Error("redirect_uri does not exactly match the registered TikTok redirect URI");
  }
  if (!input.code || !input.code_verifier || !input.state) throw new Error("code, code_verifier and state are required");

  const form = new URLSearchParams({
    client_key: env.TIKTOK_CLIENT_KEY,
    client_secret: env.TIKTOK_CLIENT_SECRET,
    code: input.code,
    grant_type: "authorization_code",
    redirect_uri: env.TIKTOK_REDIRECT_URI,
    code_verifier: input.code_verifier,
  });
  const response = await fetch("https://open.tiktokapis.com/v2/oauth/token/", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: form,
  });
  const payload = await response.json();
  if (!response.ok || !payload.access_token || !payload.refresh_token) {
    throw new Error("TikTok token exchange failed: " + (payload.error_description || payload.error || response.status));
  }

  const profile = await fetchProfile(payload.access_token);
  const sessionToken = await sealSession(env, {
    access_token: payload.access_token,
    refresh_token: payload.refresh_token,
    expires_at: Date.now() + Number(payload.expires_in || 86400) * 1000,
    open_id: payload.open_id || "",
    display_name: profile.display_name || "",
  });
  return { session_token: sessionToken, profile };
}

async function fetchProfile(accessToken) {
  const fields = "open_id,avatar_url,display_name,profile_deep_link,bio_description";
  const response = await fetch("https://open.tiktokapis.com/v2/user/info/?fields=" + encodeURIComponent(fields), {
    headers: { authorization: "Bearer " + accessToken },
  });
  const body = await response.json();
  if (!response.ok || body.error?.code && body.error.code !== "ok") {
    throw new Error("TikTok user.info failed: " + (body.error?.message || response.status));
  }
  return body.data?.user || {};
}

async function ensureFreshSession(env, session) {
  if (Number(session.expires_at || 0) > Date.now() + 120000) return session;
  const form = new URLSearchParams({
    client_key: env.TIKTOK_CLIENT_KEY,
    client_secret: env.TIKTOK_CLIENT_SECRET,
    grant_type: "refresh_token",
    refresh_token: session.refresh_token,
  });
  const response = await fetch("https://open.tiktokapis.com/v2/oauth/token/", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: form,
  });
  const body = await response.json();
  if (!response.ok || !body.access_token) throw new Error("TikTok token refresh failed");
  session.access_token = body.access_token;
  session.refresh_token = body.refresh_token || session.refresh_token;
  session.expires_at = Date.now() + Number(body.expires_in || 86400) * 1000;
  return session;
}

async function listVideos(accessToken) {
  const fields = "id,title,video_description,cover_image_url,share_url,embed_link,create_time";
  const response = await fetch("https://open.tiktokapis.com/v2/video/list/?fields=" + encodeURIComponent(fields), {
    method: "POST",
    headers: { authorization: "Bearer " + accessToken, "content-type": "application/json" },
    body: JSON.stringify({ max_count: 20 }),
  });
  const body = await response.json();
  if (!response.ok || body.error?.code && body.error.code !== "ok") {
    throw new Error("TikTok video.list failed: " + (body.error?.message || response.status));
  }
  return body.data?.videos || [];
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (request.method === "GET" && url.pathname === "/.well-known/assetlinks.json") {
      const fingerprint = String(env.TIKTOK_ANDROID_SHA256_FINGERPRINT || "").trim();
      const statements = fingerprint ? [{
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "vn.phuong.iptvplayer",
          sha256_cert_fingerprints: [fingerprint],
        },
      }] : [];
      return new Response(JSON.stringify(statements), {
        headers: {
          "content-type": "application/json; charset=utf-8",
          "cache-control": "no-store",
          ...CORS,
        },
      });
    }
    if (request.method === "GET" && url.pathname === "/callback") {
      return new Response(
        "<!doctype html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>NM7 TikTok Login</title><main style=\"font-family:system-ui;padding:24px\"><h2>Quay lại NM7 IPTV</h2><p>Nếu ứng dụng chưa tự mở, quay lại NM7 để hoàn tất đăng nhập. Hãy xác minh Android App Links và SHA-256 đã đăng ký trong TikTok Developer Portal.</p></main>",
        { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } }
      );
    }
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, configured: !!(env.TIKTOK_CLIENT_KEY && env.TIKTOK_CLIENT_SECRET && env.TIKTOK_SESSION_SECRET && env.TIKTOK_REDIRECT_URI) });
    }
    try {
      if (request.method === "POST" && url.pathname === "/api/tiktok/mobile/exchange") {
        const input = await request.json();
        const result = await exchangeCode(env, input);
        return json({ ok: true, ...result });
      }
      if (request.method === "GET" && url.pathname === "/api/tiktok/mobile/videos") {
        const authorization = request.headers.get("authorization") || "";
        if (!authorization.startsWith("Bearer ")) return json({ ok: false, error: "Bearer session token required" }, 401);
        const session = await openSession(env, authorization.slice(7));
        const fresh = await ensureFreshSession(env, session);
        const [profile, videos] = await Promise.all([
          fetchProfile(fresh.access_token),
          listVideos(fresh.access_token),
        ]);
        const newSession = await sealSession(env, fresh);
        return json({
          ok: true,
          session_token: newSession,
          profile,
          videos: videos.map(video => ({
            id: String(video.id || ""),
            title: video.title || "",
            description: video.video_description || "",
            share_url: video.share_url || "",
            embed_link: video.embed_link || "",
            cover_image_url: video.cover_image_url || "",
            create_time: video.create_time || 0,
          })),
        });
      }
      return json({ ok: false, error: "not_found" }, 404);
    } catch (error) {
      return json({ ok: false, error: String(error?.message || error) }, 400);
    }
  },
};
