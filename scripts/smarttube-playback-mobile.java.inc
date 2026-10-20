    private static PlaybackActivity sNm7Active;
    private static boolean sNm7Mini;
    private static boolean sNm7RestorePending;
    private static boolean sNm7ResumeAfterIptv;
    private static boolean sNm7SuspendedForIptv;
    private boolean mNm7Stopped;
    private boolean mNm7LeavingForMini;
    private boolean mNm7TargetRestoreWaiting;
    private boolean mNm7ResumeAfterTarget;
    private com.google.android.exoplayer2.video.VideoListener mNm7FirstFrameListener;
    private Runnable mNm7TargetRestoreTimeout;
    private long mNm7TargetRestoreStarted;
    private long mNm7TargetRestoreSequence;
    private long mNm7TargetRestoreHandoffId;
    private com.google.android.exoplayer2.ui.PlayerView mNm7TargetRestoreView;
    private Object mNm7LastRenderedSurfaceToken;
    private int mNm7TargetRestoreRecoveryAttempts;
    private long mNm7TargetRestoreLastRecoveryMs;
    private boolean mNm7TargetRestoreFrameConfirmed;
    private boolean mNm7TargetRestoreFailed;
    private boolean mNm7RestoreFailed;
    private String mNm7TargetRestoreType;
    private float mNm7DesiredVolume = 1f;
    private com.google.android.exoplayer2.ui.PlayerView mNm7VideoTarget;
    private boolean mNm7OwnsPlayback;
    private Object mNm7BackCallback;
    private com.google.android.exoplayer2.SimpleExoPlayer mNm7ObservedPlayer;
    private com.google.android.exoplayer2.Player.EventListener mNm7PlaybackObserver;
    private com.google.android.exoplayer2.video.VideoListener mNm7FrameObserver;
    private long mNm7LastFirstFrameMs;
    private long mNm7LastErrorRetryMs;
    private int mNm7DecoderRecoveryAttempts;
    private int mNm7RenderRecoveryAttempts;

    private boolean isNm7DecoderError(Throwable error) {
        Throwable cause = error;
        for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.contains("MediaCodec")
                    || name.contains("DecoderInitializationException")
                    || name.contains("DecoderException")
                    || name.contains("VideoDecoder")) {
                return true;
            }
        }
        return false;
    }

    private void recoverNm7DecoderError(com.google.android.exoplayer2.ExoPlaybackException error) {
        if (mPlayer == null || mNm7Stopped || sNm7SuspendedForIptv || isFinishing()) return;
        if (!isNm7DecoderError(error)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mNm7LastErrorRetryMs < 1500L) return;
        mNm7LastErrorRetryMs = now;
        mNm7DecoderRecoveryAttempts++;
        long position = Math.max(0L, mPlayer.getCurrentPosition());
        try {
            android.util.Log.w("NM7Playback",
                    "decoder_recovery attempt=" + mNm7DecoderRecoveryAttempts
                            + " state=" + mPlayer.getPlaybackState()
                            + " position=" + position);

            if (mNm7DecoderRecoveryAttempts == 3 && mNm7TrackSelector != null) {
                try {
                    mNm7TrackSelector.setParameters(
                            mNm7TrackSelector.buildUponParameters()
                                    .setMaxVideoSize(1280, 720)
                                    .setMaxVideoBitrate(4_000_000)
                                    .setExceedVideoConstraintsIfNecessary(false));
                } catch (RuntimeException ignored) { }
            }

            if (mNm7DecoderRecoveryAttempts <= 3) {
                mPlayer.retry();
                mPlayer.seekTo(position);
                if (!mNm7TargetRestoreWaiting && nm7ClaimYoutubeAudio("decoder_recovery")) {
                    mPlayer.setPlayWhenReady(true);
                } else {
                    mPlayer.setVolume(0f);
                    mPlayer.setPlayWhenReady(false);
                }
                bindNm7PlayerTarget();
            } else {
                mNm7DecoderRecoveryAttempts = 0;
                restartEngine();
            }
        } catch (RuntimeException recoveryError) {
            android.util.Log.w("NM7Playback", "decoder recovery failed", recoveryError);
        }
    }

    private void ensureNm7PlaybackObserver() {
        if (mPlayer == null || mPlayer == mNm7ObservedPlayer) return;
        if (mNm7ObservedPlayer != null && mNm7PlaybackObserver != null) {
            try { mNm7ObservedPlayer.removeListener(mNm7PlaybackObserver); } catch (RuntimeException ignored) { }
        }
        if (mNm7ObservedPlayer != null && mNm7FrameObserver != null) {
            try { mNm7ObservedPlayer.removeVideoListener(mNm7FrameObserver); } catch (RuntimeException ignored) { }
        }

        mNm7ObservedPlayer = mPlayer;
        mNm7LastFirstFrameMs = 0L;
        mNm7LastRenderedSurfaceToken = null;
        mNm7DecoderRecoveryAttempts = 0;
        mNm7RenderRecoveryAttempts = 0;

        mNm7PlaybackObserver = new com.google.android.exoplayer2.Player.EventListener() {
            // Decoder errors are routed through Nm7EngineErrorOwner so the presenter and
            // decoder recovery never race each other.
            @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                    mNm7DecoderRecoveryAttempts = 0;
                    if (playWhenReady) armNm7RenderWatchdog();
                }
            }
        };
        mNm7FrameObserver = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                mNm7LastFirstFrameMs = android.os.SystemClock.elapsedRealtime();
                mNm7LastRenderedSurfaceToken = nm7SurfaceToken(mNm7VideoTarget);
                mNm7RenderRecoveryAttempts = 0;
                mNm7DecoderRecoveryAttempts = 0;
            }
        };
        mPlayer.addListener(mNm7PlaybackObserver);
        mPlayer.addVideoListener(mNm7FrameObserver);
    }

    private void armNm7RenderWatchdog() {
        if (mPlayer == null || mNm7Stopped) return;
        final PlaybackActivity owner = this;
        final long started = android.os.SystemClock.elapsedRealtime();
        mHandler.postDelayed(() -> {
            if (owner.mPlayer == null || owner.mNm7Stopped || owner.mPlayer != mPlayer) return;
            int state = owner.mPlayer.getPlaybackState();
            boolean wantsPlay = owner.mPlayer.getPlayWhenReady();
            if (wantsPlay && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                    && owner.mNm7LastFirstFrameMs < started) {
                owner.mNm7RenderRecoveryAttempts++;
                android.util.Log.w("NM7Playback", "render_watchdog_no_first_frame_ms="
                        + (android.os.SystemClock.elapsedRealtime() - started)
                        + " attempt=" + owner.mNm7RenderRecoveryAttempts);
                try {
                    long position = Math.max(0L, owner.mPlayer.getCurrentPosition());
                    owner.bindNm7PlayerTarget();
                    owner.mPlayer.seekTo(position);
                    if (owner.mNm7TargetRestoreWaiting) {
                        owner.mPlayer.setVolume(0f);
                        owner.mPlayer.setPlayWhenReady(true);
                    } else if (owner.nm7ClaimYoutubeAudio("render_watchdog")) {
                        owner.mPlayer.setPlayWhenReady(true);
                    }
                    if (owner.mNm7RenderRecoveryAttempts >= 3) {
                        owner.mNm7RenderRecoveryAttempts = 0;
                        owner.restartEngine();
                    }
                } catch (RuntimeException error) {
                    android.util.Log.w("NM7Playback", "render watchdog recovery failed", error);
                }
            }
        }, 1800L);
    }

    private void bindNm7PlayerTarget() {
        // ErrorFixerController may rebuild the engine while PlaybackActivity is stopped.
        // Binding the new player to its hidden full view leaves the visible mini black.
        if (sNm7SuspendedForIptv) {
            mPlayerView.setPlayer(null);
            mPlayer.setPlayWhenReady(false);
            return;
        }
        com.google.android.exoplayer2.ui.PlayerView target = mPlayerView;
        if (sNm7Mini) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView && view.isAttachedToWindow()) {
                    target = (com.google.android.exoplayer2.ui.PlayerView) view;
                }
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read target after engine restart", error);
            }
        }
        ensureNm7PlaybackObserver();
        com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, mNm7VideoTarget, target);
        if (target.getPlayer() != mPlayer) target.setPlayer(mPlayer);
        mNm7VideoTarget = target;
        if (!mNm7OwnsPlayback) mPlayer.setVolume(0f);
        armNm7RenderWatchdog();
    }

    public static boolean isNm7Playing() {
        return isNm7SessionActive() && sNm7Active.mPlayer.getPlayWhenReady();
    }

    private static void recoverNm7EndedPlayback(PlaybackActivity active) {
        if (active == null || active.mPlayer == null || sNm7SuspendedForIptv) return;
        if (active.mPlayer.getPlaybackState() != Player.STATE_ENDED) return;
        try {
            android.util.Log.i("NM7Playback", "recover ended player: seek to start");
            active.mPlayer.seekTo(0);
            if (active.nm7ClaimYoutubeAudio("ended_recovery")) {
                active.mPlayer.setPlayWhenReady(true);
            }
        } catch (RuntimeException error) {
            android.util.Log.w("NM7Playback", "ended player prepare failed", error);
        }
    }

    public static void toggleNm7Playback() {
        if (!isNm7SessionActive() || sNm7SuspendedForIptv) return;
        PlaybackActivity active = sNm7Active;
        if (active.mPlayer.getPlaybackError() != null) {
            active.mPlayer.retry();
            if (active.nm7ClaimYoutubeAudio("mini_retry")) active.mPlayer.setPlayWhenReady(true);
        } else if (active.mNm7RestoreFailed) {
            com.google.android.exoplayer2.ui.PlayerView target = active.mNm7VideoTarget != null
                    ? active.mNm7VideoTarget : active.mPlayerView;
            long generation = active.beginNm7TargetRestore(target, "user_retry", true);
            if (generation > 0L) {
                active.mNm7RestoreFailed = false;
                active.mPlayer.setVolume(0f);
                active.armNm7FirstFrameResume(generation);
                active.mPlayer.setPlayWhenReady(true);
            }
        } else if (active.mPlayer.getPlaybackState() == Player.STATE_ENDED) {
            active.mPlayer.seekTo(0L);
            if (active.nm7ClaimYoutubeAudio("mini_replay")) active.mPlayer.setPlayWhenReady(true);
        } else if (active.mNm7TargetRestoreWaiting && active.mPlayer.getPlayWhenReady()) {
            sNm7ResumeAfterIptv = false;
            sNm7RestorePending = false;
            active.cancelNm7TargetRestore(false);
            active.mNm7RestoreFailed = false;
            active.mPlayer.setPlayWhenReady(false);
        } else if (active.mPlayer.getPlayWhenReady()) {
            active.mPlayer.setPlayWhenReady(false);
        } else if (active.nm7ClaimYoutubeAudio("mini_play")) {
            active.mPlayer.setPlayWhenReady(true);
        }
    }

    public static int getNm7PlaybackState() {
        if (!isNm7SessionActive()) return -1;
        if (sNm7Active.mPlayer.getPlaybackError() != null) return -1;
        return sNm7Active.mPlayer.getPlaybackState();
    }

    public static boolean isNm7SessionActive() {
        return sNm7Active != null && !sNm7Active.mNm7Stopped && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing() && sNm7Active.mPlayer != null;
    }

    public static boolean isNm7MiniPlayerActive() {
        return sNm7Mini && sNm7Active != null && !sNm7Active.isDestroyed()
                && !sNm7Active.isFinishing() && sNm7Active.mPlayer != null;
    }

    public static String getNm7VideoTitle() {
        if (sNm7Active == null || sNm7Active.mNm7SessionVideo == null) return null;
        try {
            return sNm7Active.mNm7SessionVideo.getTitle();
        } catch (Throwable error) {
            try {
                return (String) sNm7Active.mNm7SessionVideo.getClass().getField("title").get(sNm7Active.mNm7SessionVideo);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String getNm7VideoAuthor() {
        if (sNm7Active == null || sNm7Active.mNm7SessionVideo == null) return null;
        try {
            return sNm7Active.mNm7SessionVideo.getAuthor();
        } catch (Throwable error) {
            try {
                return (String) sNm7Active.mNm7SessionVideo.getClass().getField("author").get(sNm7Active.mNm7SessionVideo);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static long getNm7VideoPosition() {
        if (sNm7Active == null || sNm7Active.mPlayer == null) return 0L;
        try {
            return sNm7Active.mPlayer.getCurrentPosition();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static long getNm7VideoDuration() {
        if (sNm7Active == null || sNm7Active.mPlayer == null) return 0L;
        try {
            return sNm7Active.mPlayer.getDuration();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static void attachNm7MiniPlayer(android.view.View previousSurface, android.view.View nextSurface,
                                           long tabSwitchId) {
        PlaybackActivity active = sNm7Active;
        if (!isNm7MiniPlayerActive()) return;
        if (!(nextSurface instanceof com.google.android.exoplayer2.ui.PlayerView)) return;
        try {
            if (!Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("isCurrentTabHandoff", android.content.Context.class, long.class, String.class)
                    .invoke(null, active, tabSwitchId, "youtube"))) return;
        } catch (ReflectiveOperationException | RuntimeException error) { return; }
        // A delayed MainActivity attach must not take playback back from IPTV.
        if (sNm7SuspendedForIptv) {
            sNm7SuspendedForIptv = false;
        }

        active.blockEngine(false);
        com.google.android.exoplayer2.ui.PlayerView mini =
                (com.google.android.exoplayer2.ui.PlayerView) nextSurface;
        mini.setUseController(false);
        com.google.android.exoplayer2.ui.PlayerView oldTarget = active.mNm7VideoTarget;
        if (oldTarget == null) oldTarget = active.mPlayerView;
        boolean resume = sNm7ResumeAfterIptv || active.mPlayer.getPlayWhenReady();
        boolean sameRenderedSurface = oldTarget == mini && active.nm7TargetSurfaceReady(mini)
                && active.mNm7LastRenderedSurfaceToken != null
                && active.mNm7LastRenderedSurfaceToken == active.nm7SurfaceToken(mini);
        boolean needsFrameProof = !sameRenderedSurface;
        long generation = needsFrameProof
                ? active.beginNm7TargetRestore(mini, "fullscreen_to_mini", resume) : -1L;
        if (oldTarget != mini) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(active.mPlayer, oldTarget, mini);
        }
        active.mNm7VideoTarget = mini;
        sNm7Mini = true;
        android.util.Log.i("NM7Playback", "target=mini state=" + active.mPlayer.getPlaybackState());

        if (generation > 0L) {
            active.mPlayer.setVolume(0f);
            active.armNm7FirstFrameResume(generation);
            active.mPlayer.setPlayWhenReady(true);
        } else if (needsFrameProof) {
            sNm7ResumeAfterIptv = true;
            active.mPlayer.setVolume(0f);
            active.mPlayer.setPlayWhenReady(false);
            android.util.Log.e("NM7Playback", "handoff_failed reason=visual_generation_unavailable target=mini");
        } else if (resume) {
            sNm7ResumeAfterIptv = false;
            if (active.nm7ClaimYoutubeAudio("same_surface_mini")) active.mPlayer.setPlayWhenReady(true);
            else active.mPlayer.setPlayWhenReady(false);
        }
    }

    public static void restoreNm7Player() {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return;

        // Do not switch the video target while PlaybackActivity is still stopped.
        // Bring it to foreground first; onResume completes the surface handoff.
        if (sNm7RestorePending) return;
        sNm7RestorePending = true;
        active.mNm7ResumeAfterTarget = active.mPlayer.getPlayWhenReady() || sNm7ResumeAfterIptv;
        active.startActivity(new Intent(active, active.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
    }

    private void completeNm7RestoreOnResume() {
        if (mPlayer == null) return;
        sNm7SuspendedForIptv = false;
        com.google.android.exoplayer2.ui.PlayerView oldTarget = mNm7VideoTarget;
        if (oldTarget == null) {
            try {
                android.view.View view = (android.view.View) Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                        .getMethod("surfaceView").invoke(null);
                if (view instanceof com.google.android.exoplayer2.ui.PlayerView)
                    oldTarget = (com.google.android.exoplayer2.ui.PlayerView) view;
            } catch (ReflectiveOperationException error) {
                android.util.Log.e("NM7Playback", "Read mini target", error);
            }
        }
        boolean resume = sNm7ResumeAfterIptv || mNm7ResumeAfterTarget || mPlayer.getPlayWhenReady()
                || mPlayer.getPlaybackState() == Player.STATE_ENDED;
        boolean changedTarget = oldTarget != mPlayerView;
        boolean sameRenderedSurface = !changedTarget && nm7TargetSurfaceReady(mPlayerView)
                && mNm7LastRenderedSurfaceToken != null
                && mNm7LastRenderedSurfaceToken == nm7SurfaceToken(mPlayerView);
        boolean needsFrameProof = !sameRenderedSurface;
        long restoreGeneration = needsFrameProof
                ? beginNm7TargetRestore(mPlayerView, "mini_to_fullscreen", resume) : -1L;
        if (oldTarget != mPlayerView) {
            com.google.android.exoplayer2.ui.PlayerView.switchTargetView(mPlayer, oldTarget, mPlayerView);
        } else if (mPlayerView.getPlayer() != mPlayer) {
            mPlayerView.setPlayer(mPlayer);
        }
        mNm7VideoTarget = mPlayerView;
        sNm7Mini = false;
        if (restoreGeneration > 0L) {
            mPlayer.setVolume(0f);
            armNm7FirstFrameResume(restoreGeneration);
            mPlayer.setPlayWhenReady(true); // Decode the destination frame silently; audio waits for frame proof.
        } else if (needsFrameProof) {
            sNm7ResumeAfterIptv = true;
            sNm7RestorePending = false;
            mNm7RestoreFailed = true;
            mNm7ErrorLabel = "Không thể bắt đầu chuyển màn phát • chạm phát để thử lại";
            mPlayer.setVolume(0f);
            mPlayer.setPlayWhenReady(false);
            android.util.Log.e("NM7Playback", "handoff_failed reason=visual_generation_unavailable target=fullscreen");
        } else if (resume) {
            sNm7RestorePending = false;
            sNm7ResumeAfterIptv = false;
            mNm7ResumeAfterTarget = false;
            if (nm7ClaimYoutubeAudio("same_surface_restore")) mPlayer.setPlayWhenReady(true);
            else mPlayer.setPlayWhenReady(false);
        } else if (mPlayer.getPlaybackState() == Player.STATE_ENDED && !mNm7Stopped) {
            sNm7RestorePending = false;
            mPlayer.seekTo(0L);
            if (nm7ClaimYoutubeAudio("ended_restore")) mPlayer.setPlayWhenReady(true);
        } else {
            sNm7RestorePending = false;
        }
        blockEngine(false);
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer").getMethod("remove").invoke(null);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Remove mini after foreground handoff", error);
        }
        android.util.Log.i("NM7Playback", "target=fullscreen; mini=false; back enabled");
    }

    private long nm7BeginVisualHandoff(String type) {
        try {
            Object value = Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("beginVisualHandoff", android.content.Context.class, String.class)
                    .invoke(null, this, type);
            return value instanceof Number ? ((Number) value).longValue() : -1L;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "begin visual handoff failed", error);
            return -1L;
        }
    }

    private long nm7CurrentHandoffGeneration() {
        try {
            Object value = Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("currentHandoffGeneration").invoke(null);
            return value instanceof Number ? ((Number) value).longValue() : -1L;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return -1L; }
    }

    private boolean nm7IsCurrentYoutubeHandoff(long handoffId) {
        try {
            return Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("isCurrentVisualHandoff", android.content.Context.class, long.class)
                    .invoke(null, this, handoffId));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private boolean nm7CompleteVisualHandoff(long handoffId, String type) {
        try {
            return Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("completeVisualHandoff", android.content.Context.class, long.class, String.class)
                    .invoke(null, this, handoffId, type));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private boolean nm7ClaimYoutubeAudio(String reason) {
        if (mNm7OwnsPlayback) {
            if (mPlayer != null) mPlayer.setVolume(mNm7DesiredVolume);
            return true;
        }
        try {
            Class<?> app = Class.forName("vn.phuong.iptvplayer.MobileNm7Application");
            Object value = app.getMethod("beginYoutubeAudioHandoff", String.class).invoke(null, reason);
            if (!(value instanceof Number) || ((Number) value).longValue() <= 0L) return false;
            long handoffId = ((Number) value).longValue();
            boolean accepted = Boolean.TRUE.equals(app.getMethod("pauseIptvForYoutube", long.class)
                    .invoke(null, handoffId));
            if (!accepted) return false;
            if (mPlayer == null) return false;
            mPlayer.setVolume(mNm7DesiredVolume);
            mNm7OwnsPlayback = true;
            android.util.Log.i("NM7Playback", "audio_start_requested handoff_id=" + handoffId
                    + " owner=youtube reason=" + reason);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "YouTube audio ownership failed reason=" + reason, error);
            return false;
        }
    }

    private Object nm7SurfaceToken(com.google.android.exoplayer2.ui.PlayerView target) {
        if (target == null) return null;
        android.view.View surface = target.getVideoSurfaceView();
        if (surface instanceof android.view.SurfaceView) {
            return ((android.view.SurfaceView) surface).getHolder().getSurface();
        }
        if (surface instanceof android.view.TextureView) {
            return ((android.view.TextureView) surface).getSurfaceTexture();
        }
        return surface;
    }

    private boolean nm7TargetSurfaceReady(com.google.android.exoplayer2.ui.PlayerView target) {
        if (target == null || !target.isAttachedToWindow() || !target.isShown()
                || target.getWindowVisibility() != android.view.View.VISIBLE) return false;
        android.view.View surface = target.getVideoSurfaceView();
        if (surface instanceof android.view.SurfaceView) {
            return ((android.view.SurfaceView) surface).getHolder().getSurface().isValid();
        }
        if (surface instanceof android.view.TextureView) {
            return ((android.view.TextureView) surface).isAvailable();
        }
        return surface != null && surface.isAttachedToWindow();
    }

    private long beginNm7TargetRestore(com.google.android.exoplayer2.ui.PlayerView target,
                                       String type, boolean resume) {
        if (mPlayer == null || target == null) return -1L;
        cancelNm7TargetRestore(false);
        long handoffId = nm7BeginVisualHandoff(type);
        if (handoffId <= 0L) return -1L;
        long generation = ++mNm7TargetRestoreSequence;
        mNm7TargetRestoreHandoffId = handoffId;
        mNm7TargetRestoreView = target;
        mNm7TargetRestoreType = type;
        mNm7TargetRestoreWaiting = true;
        mNm7ResumeAfterTarget = resume;
        mNm7TargetRestoreStarted = android.os.SystemClock.elapsedRealtime();
        mNm7TargetRestoreRecoveryAttempts = 0;
        mNm7TargetRestoreLastRecoveryMs = 0L;
        mNm7TargetRestoreFrameConfirmed = false;
        mNm7TargetRestoreFailed = false;
        mPlayer.setVolume(0f);
        mPlayer.setPlayWhenReady(false);
        android.util.Log.i("NM7Playback", "target_view_attached handoff_id=" + handoffId
                + " generation=" + generation + " type=" + type
                + " attached=" + target.isAttachedToWindow()
                + " surface_ready=" + nm7TargetSurfaceReady(target));
        return generation;
    }

    private void armNm7FirstFrameResume(final long generation) {
        if (mPlayer == null || !mNm7TargetRestoreWaiting || mNm7FirstFrameListener != null) return;
        final com.google.android.exoplayer2.SimpleExoPlayer expectedPlayer = mPlayer;
        final com.google.android.exoplayer2.ui.PlayerView expectedTarget = mNm7TargetRestoreView;
        final long handoffId = mNm7TargetRestoreHandoffId;
        final String type = mNm7TargetRestoreType;
        mNm7FirstFrameListener = new com.google.android.exoplayer2.video.VideoListener() {
            @Override public void onRenderedFirstFrame() {
                if (generation != mNm7TargetRestoreSequence || !mNm7TargetRestoreWaiting
                        || expectedPlayer != mPlayer || expectedTarget != mNm7VideoTarget
                        || mNm7Stopped) return;
                if (!nm7IsCurrentYoutubeHandoff(handoffId)
                        || !nm7TargetSurfaceReady(expectedTarget)) {
                    android.util.Log.i("NM7Playback", "handoff_cancelled handoff_id=" + handoffId
                            + " generation=" + generation + " reason=stale_target_or_tab");
                    if (mNm7ResumeAfterTarget) sNm7ResumeAfterIptv = true;
                    expectedPlayer.setVolume(0f);
                    expectedPlayer.setPlayWhenReady(false);
                    cancelNm7TargetRestore(false);
                    return;
                }
                long elapsed = android.os.SystemClock.elapsedRealtime() - mNm7TargetRestoreStarted;
                com.google.android.exoplayer2.Format format = expectedPlayer.getVideoFormat();
                android.util.Log.i("NM7Playback", "first_frame_rendered handoff_id=" + handoffId
                        + " generation=" + generation + " type=" + type + " elapsed_ms=" + elapsed
                        + " state=" + expectedPlayer.getPlaybackState() + " video_format="
                        + (format == null ? "none" : format.width + "x" + format.height)
                        + " surface_ready=" + nm7TargetSurfaceReady(expectedTarget));
                mNm7TargetRestoreFrameConfirmed = true;
                boolean shouldResume = mNm7ResumeAfterTarget;
                if (!nm7CompleteVisualHandoff(handoffId, type)) {
                    if (mNm7ResumeAfterTarget && nm7IsCurrentYoutubeHandoff(handoffId)) {
                        sNm7ResumeAfterIptv = true;
                    }
                    expectedPlayer.setVolume(0f);
                    expectedPlayer.setPlayWhenReady(false);
                    cancelNm7TargetRestore(false);
                    return;
                }
                sNm7RestorePending = false;
                sNm7ResumeAfterIptv = false;
                mNm7RestoreFailed = false;
                cancelNm7TargetRestore(false);
                if (shouldResume) {
                    if (!nm7ClaimYoutubeAudio("first_frame")) {
                        sNm7ResumeAfterIptv = true;
                        mNm7RestoreFailed = true;
                        mNm7ErrorLabel = "Không thể chuyển quyền phát • chạm phát để thử lại";
                        expectedPlayer.setPlayWhenReady(false);
                        showProgressBar(false);
                        syncNm7KeepAlive();
                        return;
                    }
                    expectedPlayer.setPlayWhenReady(true);
                } else {
                    expectedPlayer.setPlayWhenReady(false);
                    expectedPlayer.setVolume(mNm7OwnsPlayback ? mNm7DesiredVolume : 0f);
                }
            }
        };
        mPlayer.addVideoListener(mNm7FirstFrameListener);
        mNm7TargetRestoreTimeout = new Runnable() {
            @Override public void run() {
                if (generation != mNm7TargetRestoreSequence || !mNm7TargetRestoreWaiting
                        || expectedPlayer != mPlayer) return;
                if (!nm7IsCurrentYoutubeHandoff(handoffId)) {
                    android.util.Log.i("NM7Playback", "handoff_cancelled handoff_id=" + handoffId
                            + " generation=" + generation + " reason=stale_generation");
                    sNm7RestorePending = false;
                    cancelNm7TargetRestore(false);
                    return;
                }
                long elapsed = android.os.SystemClock.elapsedRealtime() - mNm7TargetRestoreStarted;
                com.google.android.exoplayer2.Format format = expectedPlayer.getVideoFormat();
                com.google.android.exoplayer2.decoder.DecoderCounters counters = expectedPlayer.getVideoDecoderCounters();
                if (counters != null) counters.ensureUpdated();
                int rendered = counters == null ? 0 : counters.renderedOutputBufferCount;
                android.util.Log.w("NM7Playback", "handoff_timeout handoff_id=" + handoffId
                        + " generation=" + generation + " type=" + type + " elapsed_ms=" + elapsed
                        + " player_state=" + expectedPlayer.getPlaybackState()
                        + " surface_ready=" + nm7TargetSurfaceReady(expectedTarget)
                        + " video_format=" + (format == null ? "none" : format.width + "x" + format.height)
                        + " rendered_buffers=" + rendered);
                if (expectedPlayer.getPlaybackState() == com.google.android.exoplayer2.Player.STATE_READY
                        && format == null) {
                    com.google.android.exoplayer2.source.TrackGroupArray groups = expectedPlayer.getCurrentTrackGroups();
                    boolean hasVideoTrack = false;
                    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                        com.google.android.exoplayer2.source.TrackGroup group = groups.get(groupIndex);
                        for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                            com.google.android.exoplayer2.Format trackFormat = group.getFormat(trackIndex);
                            if (trackFormat.sampleMimeType != null
                                    && com.google.android.exoplayer2.util.MimeTypes.isVideo(trackFormat.sampleMimeType)) {
                                hasVideoTrack = true;
                            }
                        }
                    }
                    if (!hasVideoTrack && groups.length > 0) {
                        android.util.Log.i("NM7Playback", "handoff_completed handoff_id=" + handoffId
                                + " type=" + type + " frame=not_expected media=audio_only");
                        boolean shouldResume = mNm7ResumeAfterTarget;
                        if (nm7CompleteVisualHandoff(handoffId, type)) {
                            sNm7RestorePending = false;
                            sNm7ResumeAfterIptv = false;
                            cancelNm7TargetRestore(false);
                            if (shouldResume && nm7ClaimYoutubeAudio("audio_only")) {
                                expectedPlayer.setPlayWhenReady(true);
                            } else if (shouldResume) {
                                sNm7ResumeAfterIptv = true;
                                expectedPlayer.setPlayWhenReady(false);
                            } else {
                                expectedPlayer.setPlayWhenReady(false);
                                expectedPlayer.setVolume(mNm7OwnsPlayback ? mNm7DesiredVolume : 0f);
                            }
                        }
                        return;
                    }
                }
                if (elapsed < 9_000L) {
                    long nowMs = android.os.SystemClock.elapsedRealtime();
                    boolean rebindDue = mNm7TargetRestoreRecoveryAttempts == 0
                            || nowMs - mNm7TargetRestoreLastRecoveryMs >= 3_000L;
                    if (mNm7TargetRestoreRecoveryAttempts < 3 && rebindDue
                            && nm7TargetSurfaceReady(expectedTarget)
                            && expectedTarget.getPlayer() == expectedPlayer) {
                        mNm7TargetRestoreRecoveryAttempts++;
                        mNm7TargetRestoreLastRecoveryMs = nowMs;
                        expectedTarget.setPlayer(null);
                        expectedTarget.setPlayer(expectedPlayer);
                        android.util.Log.w("NM7Playback", "render_recovery handoff_id=" + handoffId
                                + " action=target_rebind attempt=" + mNm7TargetRestoreRecoveryAttempts
                                + " elapsed_ms=" + elapsed);
                    }
                    mHandler.postDelayed(this, 250L);
                    return;
                }
                mNm7RestoreFailed = true;
                if (mNm7ResumeAfterTarget) sNm7ResumeAfterIptv = true;
                mNm7ErrorLabel = "Không khôi phục được khung hình • chạm phát để thử lại";
                expectedPlayer.setVolume(0f);
                expectedPlayer.setPlayWhenReady(false);
                showProgressBar(false);
                syncNm7KeepAlive();
                sNm7RestorePending = false;
                android.util.Log.e("NM7Playback", "handoff_failed handoff_id=" + handoffId
                        + " generation=" + generation + " reason=first_frame_deadline");
                cancelNm7TargetRestore(false);
            }
        };
        mHandler.postDelayed(mNm7TargetRestoreTimeout, 750L);
    }

    private void cancelNm7TargetRestore(boolean resume) {
        if (mNm7TargetRestoreTimeout != null) mHandler.removeCallbacks(mNm7TargetRestoreTimeout);
        if (mNm7FirstFrameListener != null && mPlayer != null) mPlayer.removeVideoListener(mNm7FirstFrameListener);
        if (mNm7TargetRestoreWaiting && !mNm7TargetRestoreFrameConfirmed) {
            android.util.Log.i("NM7Playback", "handoff_cancelled handoff_id=" + mNm7TargetRestoreHandoffId
                    + " generation=" + mNm7TargetRestoreSequence + " reason=cancelled");
        }
        mNm7TargetRestoreSequence++;
        mNm7TargetRestoreTimeout = null;
        mNm7FirstFrameListener = null;
        mNm7TargetRestoreWaiting = false;
        mNm7TargetRestoreView = null;
        mNm7TargetRestoreHandoffId = 0L;
        mNm7TargetRestoreType = null;
        mNm7TargetRestoreFrameConfirmed = false;
        mNm7ResumeAfterTarget = false;
    }

    public static boolean consumeNm7BrowseBack() {
        if (!isNm7MiniPlayerActive()) return false;
        sNm7RestorePending = false;
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer").getMethod("remove").invoke(null);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Remove mini from Browse Back", error);
        }
        stopForNm7Iptv();
        return true;
    }

    public static boolean suspendForNm7Iptv(long handoffId) {
        PlaybackActivity active = sNm7Active;
        if (active == null || active.isDestroyed() || active.isFinishing() || active.mPlayer == null) return false;
        try {
            if (!Boolean.TRUE.equals(Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("isCurrentAudioHandoff", android.content.Context.class, long.class, String.class)
                    .invoke(null, active, handoffId, "iptv"))) return false;
        } catch (ReflectiveOperationException | RuntimeException error) { return false; }
        if (sNm7SuspendedForIptv) return true; // Do not overwrite the saved playing intent.
        sNm7SuspendedForIptv = true;

        // IPTV may own playback now, but keep the YouTube session and exact video/position alive.
        sNm7Mini = true;
        sNm7ResumeAfterIptv = active.mPlayer.getPlayWhenReady() || active.mNm7ResumeAfterTarget;
        active.cancelNm7TargetRestore(false);
        active.mNm7Stopped = false;
        active.mNm7OwnsPlayback = false;
        active.mPlayer.setVolume(0f);
        active.blockEngine(true);
        active.mPlayer.setPlayWhenReady(false);
        if (active.mNm7VideoTarget != null) active.mNm7VideoTarget.setPlayer(null);
        active.mNm7VideoTarget = null;
        active.mPlayerView.setPlayer(null);
        active.nm7SetBackground(false);
        active.stopService(new Intent().setClassName(active,
                "vn.phuong.iptvplayer.BackgroundPlaybackService"));
        android.util.Log.i("NM7Playback", "handoff_audio_owner=iptv handoff_id=" + handoffId
                + " youtube_play_when_ready=" + active.mPlayer.getPlayWhenReady());
        return !active.mPlayer.getPlayWhenReady();
    }

    public static void stopForNm7Iptv() {
        PlaybackActivity active = sNm7Active;
        sNm7Mini = false;
        sNm7RestorePending = false;
        sNm7ResumeAfterIptv = false;
        sNm7SuspendedForIptv = false;
        if (active == null || active.isDestroyed()) return;
        active.mNm7Stopped = true;
        active.cancelNm7TargetRestore(false);
        active.blockEngine(false);
        active.nm7SetBackground(false);
        if (active.mPlayer != null && active.mNm7PlaybackObserver != null) {
            try { active.mPlayer.removeListener(active.mNm7PlaybackObserver); } catch (RuntimeException ignored) { }
        }
        if (active.mPlayer != null && active.mNm7FrameObserver != null) {
            try { active.mPlayer.removeVideoListener(active.mNm7FrameObserver); } catch (RuntimeException ignored) { }
        }
        active.mNm7ObservedPlayer = null;
        active.mNm7PlaybackObserver = null;
        active.mNm7FrameObserver = null;
        active.stopService(new Intent().setClassName(active, "vn.phuong.iptvplayer.BackgroundPlaybackService"));
        active.finishReally();
        sNm7Active = null;
    }

    private void nm7SetBackground(boolean enabled) {
        System.setProperty("nm7.youtube.background", enabled ? "1" : "0");
        try {
            Class.forName("vn.phuong.iptvplayer.SharedPlaybackSession")
                    .getMethod("setYoutubeBackground", android.content.Context.class, boolean.class)
                    .invoke(null, this, enabled);
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Background session bridge failed", error);
        }
        if (enabled && mNm7OwnsPlayback) {
            try {
                Intent service = new Intent().setClassName(this, "vn.phuong.iptvplayer.BackgroundPlaybackService");
                service.putExtra("youtube", true);
                service.putExtra("channel_name", "YouTube");
                if (VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            } catch (RuntimeException error) {
                android.util.Log.e("NM7Playback", "Background service failed", error);
            }
        }
    }

    // API 33 types isolated from class loading on Android 6-12.
    private static final class Nm7BackApi {
        static void register(PlaybackActivity activity) {
            android.window.OnBackInvokedCallback callback = activity::onBackPressed;
            activity.mNm7BackCallback = callback;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        }
        static void unregister(PlaybackActivity activity) {
            if (activity.mNm7BackCallback != null) {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        (android.window.OnBackInvokedCallback) activity.mNm7BackCallback);
                activity.mNm7BackCallback = null;
            }
        }
    }


