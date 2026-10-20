package vn.phuong.iptvplayer;

/** Wall-clock rate of rendered decoder buffers, not the declared stream frame rate. */
public final class FpsMeter {
    public static final long FRAME_STALL_TIMEOUT_MS = 6_000L;

    private long lastTime = -1, lastFrames;
    private long lastFrameProgressMs = -1L;
    private long lastObservedFrames = -1L;
    private boolean stallRecoveryIssued;

    public void reset() {
        lastTime = -1;
        lastFrames = 0;
        resetFrameStall();
    }

    public double sample(long nowMs, long renderedFrames, boolean playing) {
        if (!playing) { lastTime = -1; lastFrames = renderedFrames; return 0; }
        double fps = Double.NaN;
        if (lastTime >= 0 && nowMs > lastTime && renderedFrames >= lastFrames)
            fps = (renderedFrames - lastFrames) * 1000.0 / (nowMs - lastTime);
        lastTime = nowMs;
        lastFrames = renderedFrames;
        return fps;
    }

    /** Returns true once after six seconds with an expected, visible video but no new output. */
    public boolean shouldRecoverFromFrameStall(long nowMs, long renderedFrames, boolean eligible) {
        if (!eligible || nowMs < 0L) {
            resetFrameStall();
            return false;
        }
        if (lastFrameProgressMs < 0L || nowMs < lastFrameProgressMs
                || renderedFrames < lastObservedFrames) {
            lastFrameProgressMs = nowMs;
            lastObservedFrames = renderedFrames;
            stallRecoveryIssued = false;
            return false;
        }
        if (renderedFrames > lastObservedFrames) {
            lastFrameProgressMs = nowMs;
            lastObservedFrames = renderedFrames;
            stallRecoveryIssued = false;
            return false;
        }
        if (!stallRecoveryIssued && nowMs - lastFrameProgressMs >= FRAME_STALL_TIMEOUT_MS) {
            stallRecoveryIssued = true;
            return true;
        }
        return false;
    }

    public void resetFrameStall() {
        lastFrameProgressMs = -1L;
        lastObservedFrames = -1L;
        stallRecoveryIssued = false;
    }
}
