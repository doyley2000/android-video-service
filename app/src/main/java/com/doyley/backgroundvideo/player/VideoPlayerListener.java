package com.doyley.backgroundvideo.player;

public interface VideoPlayerListener {
	public boolean onMediaPlaybackInfo(VideoPlayer.VideoPlaybackState playbackState);
	public void onMediaPrepared(long duration);
	public void onMediaPlaybackCompleted();
	public void onMediaError(Exception exception);
	public void onMediaDrawnToSurface();
	public void onAspectRatioChanged();
}
