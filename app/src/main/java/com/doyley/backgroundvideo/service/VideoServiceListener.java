package com.doyley.backgroundvideo.service;

import com.doyley.backgroundvideo.player.VideoPlayer;

public interface VideoServiceListener {

	void onCompletion();

	void onPrepared();

	void onMediaPlayerInfo(VideoPlayer.VideoPlaybackState playbackState);

	void onPlaying(boolean isPlaying);

	void onError();

	void notifyAspectRatioChange();
}
