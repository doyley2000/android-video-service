package com.doyley.backgroundvideo.player;

import android.view.Display;
import android.view.SurfaceView;

public interface VideoPlayer {

	public enum VideoPlaybackState {
		STATE_IDLE,
		STATE_PREPARING,
		STATE_BUFFERING,
		STATE_READY,
		STATE_ENDED;
	}

	public void onPlayerStateChanged(boolean playWhenReady, int playbackState);


	public void initialize(String videoUri);

	public boolean isMediaPlayerActive();

	public void setBackgrounded(boolean background);

	public void attachSurface(SurfaceView view, Display display);

	public void tearDown();

	public long getDuration();

	public long getCurrentPosition();

	public int getBufferedPercentage();

	public boolean isPlaying();

	public VideoPlaybackState getPlaybackState();

	public void seekTo(long i);

	public void pause();

	public void start();

	public void stop();

	public void resetSurfaceAspectRatio();

}
