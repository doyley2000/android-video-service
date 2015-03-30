package com.doyley.backgroundvideo.service;

import com.doyley.backgroundvideo.player.VideoPlayer;

import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeSet;

public abstract class VideoServiceListener {

	private int mCurrentInterval;
	private final Queue<Integer> mIntervals = new LinkedList<Integer>();
	private final TreeSet<Integer> mTimeEvents = new TreeSet<Integer>();
	private final int mNumberOfIntervals;
	private long mDuration;

	public VideoServiceListener(int numberOfIntervals, int... timeEventInMs) {
		mNumberOfIntervals = numberOfIntervals;
		for (int timeEvent : timeEventInMs) {
			mTimeEvents.add(timeEvent);
		}
	}

	private void setupIntervals() {
		if (mNumberOfIntervals > 0) {

			int stepSize = (int)(mDuration / mNumberOfIntervals);

			for (int i = 0; i < mNumberOfIntervals; i++) {
				int currentStep = i * stepSize;
				mIntervals.add(currentStep);
			}
		}
	}

	public void processIntervalAndTimeEvents(long position) {
		if (!mIntervals.isEmpty()) {
			int nextInterval = mIntervals.peek();
			if (nextInterval <= position) {
				onIntervalReached(mCurrentInterval);
				mCurrentInterval++;
				mIntervals.remove();
			}
		}
		if (!mTimeEvents.isEmpty()) {
			int nextEvent = mTimeEvents.first();
			if (nextEvent <= position) {
				onTimeEvent(nextEvent);
				mTimeEvents.remove(nextEvent);
			}
		}
	}

	protected abstract void onTimeEvent(int eventMillis);

	protected abstract void onIntervalReached(int interval);

	protected abstract void onCompletion();

	protected abstract void onPrepared();

	protected abstract void onMediaPlayerInfo(VideoPlayer.VideoPlaybackState playbackState);

	protected abstract void onPlaying(boolean isPlaying);

	protected abstract void onError();

	protected abstract void notifyVideoSizeChange();

	// the intervals can only be setup after we know the duration of the video which will be
	// onPrepared
	protected void onPreparedProcess(long duration) {
		mDuration = duration;
		setupIntervals();
		onPrepared();
	}

}

