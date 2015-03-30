package com.doyley.backgroundvideo.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;

import com.doyley.backgroundvideo.activity.VideoPlayerActivity;
import com.doyley.backgroundvideo.model.Video;
import com.doyley.backgroundvideo.model.VideoPlayerMetadata;
import com.doyley.backgroundvideo.player.VideoExoPlayerImpl;
import com.doyley.backgroundvideo.player.VideoPlayer;
import com.doyley.backgroundvideo.player.VideoPlayerListener;
import com.doyley.backgroundvideo.view.MediaController;
import com.google.android.exoplayer.VideoSurfaceView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class VideoService extends Service implements MediaController.MediaPlayerControl, VideoPlayerListener {

	public static final int TARGET_BIT_RATE = 640;
	public static final Set<String> VALID_VIDEO_MIMETYPES = new HashSet<>(Arrays.asList(new String[]{"video/webm", "video/mp4", "video/3gpp"}));

	private static final String VIDEO_SERVICE_URI = "com.spotify.music.service.video";

	public static final String ACTION_MEDIA_BUTTON = VIDEO_SERVICE_URI + ".action.media_button";
	public static final String ACTION_PLAYER_TOGGLE_PAUSED = VIDEO_SERVICE_URI + ".action.player.TOGGLE_PAUSED";
	public static final String ACTION_PLAYER_PREVIOUS = VIDEO_SERVICE_URI + ".action.player.PREVIOUS";
	public static final String ACTION_PLAYER_NEXT = VIDEO_SERVICE_URI + ".action.player.NEXT";
	public static final String ACTION_PLAYER_PAUSE = VIDEO_SERVICE_URI + ".action.player.PAUSE";
	public static final String ACTION_PLAYER_PLAY = VIDEO_SERVICE_URI + ".action.player.PLAY";
	public static final String ACTION_DISPLAY_VIDEO = VIDEO_SERVICE_URI + ".action.player.DISPLAY_VIDEO";
	public static final String ACTION_START_VIDEO = VIDEO_SERVICE_URI + ".action.player.START_VIDEO";
	public static final String ACTION_DISCARD_VIDEO = VIDEO_SERVICE_URI + ".action.player.DISCARD_VIDEO";
	private static final int MSG_CHECK_PROGRESS = 1;
	private static final long MESSAGE_POLLING_INTERVAL_IN_MS = 1000;
	public static final String EXTRA_WITH_ACTIVITY = "EXTRA_WITH_ACTIVITY";
	public static final String EXTRA_START_PLAYBACK = "EXTRA_START_PLAYBACK";

	// Local binder pattern...
	public class LocalBinder extends Binder {

		public VideoService getVideoService() {
			return VideoService.this;
		}
	}

	public static boolean bindToService(Context context, ServiceConnection serviceConnection) {
		Intent serviceIntent = new Intent(context, VideoService.class);
		return context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
	}


	public static Intent getIntent(Context context, String action) {
		Intent intent = new Intent(action);
		intent.setClass(context, VideoService.class);
		return intent;
	}

	private LocalBinder mLocalBinder = new LocalBinder();
	private Handler mBackgroundHandler;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private Set<VideoServiceListener> mVideoServiceListeners = new HashSet<>();
	private final Object mVideoServiceListenersMutex = new Object();

	private boolean mStartRequested;
	private boolean mActivityRequested;
	private VideoPlayerMetadata mMetadata;
	private Video mVideo;

	private VideoPlayer mVideoPlayer;
	private Surface mSurface;

	public boolean onMediaPlaybackInfo(VideoPlayer.VideoPlaybackState playbackState) {
		Log.d(this.getClass().getSimpleName(), "onMediaPlaybackInfo - state = " + playbackState);
		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onMediaPlayerInfo(playbackState);
			}
		}
		return true;
	}

	public void onMediaPlaybackCompleted() {
		Log.d(this.getClass().getSimpleName(), "onMediaPlaybackCompleted");

		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onCompletion();
			}
		}
	}

	@Override
	public void onMediaPrepared(final long duration) {
		Log.d(this.getClass().getSimpleName(), "onMediaPrepared");

		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onPreparedProcess(duration);
			}
		}

		// only start if a start has been requested - if not, wait for an intent to start
		if (mStartRequested) {
			Log.d(this.getClass().getSimpleName(), " - start requested so try start now");
			startVideo();
		}
	}

	@Override
	public void onMediaDrawnToSurface() {
		Log.d(this.getClass().getSimpleName(), "onMediaDrawnToSurface");
	}

	public void onVideoSizeChanged() {
		Log.d(this.getClass().getSimpleName(), "onVideoSizeChanged");

		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.notifyVideoSizeChange();
			}
		}

	}

	public VideoPlayer.VideoPlaybackState getCurrentState() {
		if (isMediaPlayerActive()) {
			return mVideoPlayer.getPlaybackState();
		} else {
			return VideoPlayer.VideoPlaybackState.STATE_IDLE;
		}
	}

	@Override
	public void onCreate() {
		Log.d(this.getClass().getSimpleName(), "onCreate");
		super.onCreate();

		// Background handler for doing media playback stuff - should never be done on main thread
		HandlerThread handlerThread = new HandlerThread("background");
		handlerThread.start();
		mBackgroundHandler = new Handler(handlerThread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				long position;
				switch (msg.what) {
					case MSG_CHECK_PROGRESS:
						position = getCurrentPosition();
						synchronized (mVideoServiceListenersMutex) {
							for (VideoServiceListener listener : mVideoServiceListeners) {
								listener.processIntervalAndTimeEvents(position);
							}
						}
						msg = obtainMessage(MSG_CHECK_PROGRESS);
						sendMessageDelayed(msg, MESSAGE_POLLING_INTERVAL_IN_MS - (position % MESSAGE_POLLING_INTERVAL_IN_MS));
						break;
				}
			}
		};
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(this.getClass().getSimpleName(), "onStartCommand - action = " + intent.getAction());
		int continuationConstant = START_NOT_STICKY;
		if (intent == null) {
			// this happens when launching after a crash
			return continuationConstant;
		}

		String action = intent.getAction();

		if (action != null) {
			if (action.equals(ACTION_DISPLAY_VIDEO)) {
//				resumeActivity();
			} else if (action.equals(ACTION_START_VIDEO)) {
				mActivityRequested = intent.getBooleanExtra(EXTRA_WITH_ACTIVITY, false);
				mStartRequested = intent.getBooleanExtra(EXTRA_START_PLAYBACK, false);
				startVideo();
			} else if (action.equals(ACTION_DISCARD_VIDEO)) {
				stop();
			} else if (action.equals(ACTION_MEDIA_BUTTON)) {
				handleCommandMediaButton(intent);
			} else if (action.equals(ACTION_PLAYER_TOGGLE_PAUSED)) {
				togglePlayPause();
			} else if (action.equals(ACTION_PLAYER_NEXT)) {
				next();
			} else if (action.equals(ACTION_PLAYER_PREVIOUS)) {
				prev();
			} else if (action.equals(ACTION_PLAYER_PAUSE)) {
				pause();
			} else if (action.equals(ACTION_PLAYER_PLAY)) {
				start();
			}
		}

		return continuationConstant;
	}

	/**
	 * Loads the video track to the media player.
	 *
	 * @param metadata      Video metadata
	 */
	public void loadVideo(final VideoPlayerMetadata metadata, Video video) {
		Log.d(this.getClass().getSimpleName(), "loadVideo - metadata = " + metadata + "; video = " + video);

		// This is important or else the service might get shut down when we unbind
		startService(new Intent(this, VideoService.class));

		mMetadata = metadata;

		mVideo = video;

		prepareVideoPlayer();
	}

	private void prepareVideoPlayer() {
		Log.d(this.getClass().getSimpleName(), "prepareVideoPlayer");

		if (mVideoPlayer == null) {
			mVideoPlayer = new VideoExoPlayerImpl(this, this, mHandler, mBackgroundHandler);
		}

		if (mVideo != null) {
			mVideoPlayer.initialize(mVideo);
		}
	}

	public void setVideoSize() {
		Log.d(this.getClass().getSimpleName(), "setVideoSize");
		mVideoPlayer.setVideoSize();
	}

	private void startVideo() {

		Log.d(this.getClass().getSimpleName(), "startVideo : mActivityRequested = " + mActivityRequested);
		if (mStartRequested) {
			start();
		}
		// only start if prepared - if not prepared the prepared method will call this method when both
		// prepared and a start has been requested.
		if (mActivityRequested && isPlayerPrepared()) {
			Log.d(this.getClass().getSimpleName(), " - starting activity");
			startVideoActivity(mMetadata.getTitle());
			mActivityRequested = false;
		}
	}

	@Override
	public void start() {
		Log.d(this.getClass().getSimpleName(), "start");

		mStartRequested = true;
		if (isPlayerPrepared()) {
			Log.d(this.getClass().getSimpleName(), "- actually starting");
			mVideoPlayer.start();
			mBackgroundHandler.sendEmptyMessage(MSG_CHECK_PROGRESS);
			mStartRequested = false;
			mMetadata.setPaused(false);

			synchronized (mVideoServiceListenersMutex) {
				for (VideoServiceListener listener : mVideoServiceListeners) {
					listener.onPlaying(true);
				}
			}
		}
	}


	private void startVideoActivity(String videoTitle) {
		Log.d(this.getClass().getSimpleName(), "startVideoActivity");
		Intent intent = new Intent(this, VideoPlayerActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, videoTitle);
		startActivity(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mLocalBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		if (mSurface != null) {
			mSurface = null;
			if (isPlayerPrepared()) {
				setForegroundSurface(null, null);
			}
		}
		return false;
	}

	public void setBackgrounded(boolean backgrounded, VideoSurfaceView surfaceView, Display display) {
		Log.d(this.getClass().getSimpleName(), "setBackgrounded : " + backgrounded);
		if (isMediaPlayerActive()) {
			mVideoPlayer.setBackgrounded(backgrounded);
			setForegroundSurface(surfaceView, display);
		}
	}


	private void handleCommandMediaButton(Intent intent) {
		KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

		// A call has not paused the music and action is UP
		if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP) {
			switch (keyEvent.getKeyCode()) {
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
					togglePlayPause();
					break;
				case KeyEvent.KEYCODE_MEDIA_NEXT:
					next();
					break;
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
					prev();
					break;
				case KeyEvent.KEYCODE_HEADSETHOOK:
					pause();
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Tell existing MediaPlayer which Surface to use
	 */
	public void setForegroundSurface(final VideoSurfaceView surfaceView, final Display display) {
		Log.d(this.getClass().getSimpleName(), "setForegroundSurface : " + surfaceView);

		mSurface = surfaceView != null ? surfaceView.getHolder().getSurface() : null;

		if (isMediaPlayerActive()) {
			mVideoPlayer.attachSurface(surfaceView, display);
		}

		if (mSurface != null) {

			if (!isPlaying()) {

				// check if we reached here via a start request and if so start the video
				if (mStartRequested) {
					start();
				} else {

					// if video is paused on reattaching to activity - we need to seek to it's current
					// position - otherwise we will have a blank screen
					Runnable runnable = new Runnable() {
						public void run() {
							seekTo(getCurrentPosition());
						}
					};
					mBackgroundHandler.post(runnable);
				}
			}

		}

	}

	public void tearDown() {
		Log.d(this.getClass().getSimpleName(), "tearDown");

		synchronized (mVideoServiceListenersMutex) {
			mVideoServiceListeners.clear();
		}

		if (mVideoPlayer != null) {
			mVideoPlayer.tearDown();
			mVideoPlayer = null;
		}

		// Allow the service to be destroyed
		stopSelf();


	}

	public void onMediaError(Exception e) {
		Log.e(this.getClass().getSimpleName(), "onMediaError", e);
		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onError();
			}
		}
		tearDown();
	}

	@Override
	public void onDestroy() {
		Log.d(this.getClass().getSimpleName(), "onDestroy");

		// Just to be sure...
		if (mVideoPlayer != null) {
			mVideoPlayer.tearDown();
			mVideoPlayer = null;
		}

		quitLooperSafely(mBackgroundHandler);

		super.onDestroy();
	}

	private void quitLooperSafely(Handler handler) {
		handler.getLooper().quitSafely();
	}

	@Override
	public long getCurrentPosition() {

		if (!isMediaPlayerActive()) {
			return 0;
		}
		return mVideoPlayer.getCurrentPosition();
	}

	@Override
	public long getDuration() {

		if (!isMediaPlayerActive()) {
			return 0;
		}
		return mVideoPlayer.getDuration();
	}

	@Override
	public boolean isPlaying() {
		return isMediaPlayerActive() && mVideoPlayer.isPlaying() && isPlayerPrepared();
	}

	public boolean isPlayerPrepared() {
		Log.d(this.getClass().getSimpleName(), "isPlayerPrepared : " + (isMediaPlayerActive()
				&& (mVideoPlayer.getPlaybackState() == VideoPlayer.VideoPlaybackState.STATE_READY
				|| mVideoPlayer.getPlaybackState() == VideoPlayer.VideoPlaybackState.STATE_BUFFERING)));
		return isMediaPlayerActive() && (mVideoPlayer.getPlaybackState() == VideoPlayer.VideoPlaybackState.STATE_READY || mVideoPlayer.getPlaybackState() == VideoPlayer.VideoPlaybackState.STATE_BUFFERING);
	}

	@Override
	public int getBufferPercentage() {
		if (isMediaPlayerActive()) {
			return mVideoPlayer.getBufferedPercentage();
		}
		return 0;
	}

	@Override
	public void pause() {
		if (isMediaPlayerActive()) {
			mVideoPlayer.pause();

			mMetadata.setPaused(true);

			synchronized (mVideoServiceListenersMutex) {
				for (VideoServiceListener listener : mVideoServiceListeners) {
					listener.onPlaying(false);
				}
			}
		}
	}

	@Override
	public void seekTo(long i) {
		if (isPlayerPrepared()) {
			mVideoPlayer.seekTo(i);
		}
	}

	@Override
	public void prev() {
		if (isPlayerPrepared()) {
			// go back to start
			mVideoPlayer.seekTo(0);
		}
	}

	public void stop() {
		if (isPlayerPrepared()) {
			mVideoPlayer.stop();
			onMediaPlaybackCompleted();
		}
	}

	@Override
	public void next() {
		if (isPlayerPrepared()) {
			mVideoPlayer.stop();
			onMediaPlaybackCompleted();
		}
	}

	@Override
	public boolean isNextEnabled() {
		return isMediaPlayerActive();
	}

	@Override
	// prev button is disabled by default - future clients should revisit this
	public boolean isPrevEnabled() {
		return false;
	}

	private void togglePlayPause() {
		if (isPlaying()) {
			pause();
		} else {
			start();
		}
	}

	private boolean isMediaPlayerActive() {
		return mVideoPlayer != null && mVideoPlayer.isMediaPlayerActive();
	}

	public void registerListener(VideoServiceListener listener) {
		synchronized (mVideoServiceListenersMutex) {
			mVideoServiceListeners.add(listener);
		}
	}

	public void unregisterListener(VideoServiceListener listener) {
		synchronized (mVideoServiceListenersMutex) {
			mVideoServiceListeners.remove(listener);
		}
	}


}
