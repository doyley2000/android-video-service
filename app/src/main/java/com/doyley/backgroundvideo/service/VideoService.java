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
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;

import com.doyley.backgroundvideo.activity.VideoPlayerActivity;
import com.doyley.backgroundvideo.model.VideoMetadata;
import com.doyley.backgroundvideo.player.VideoExoPlayerImpl;
import com.doyley.backgroundvideo.player.VideoPlayer;
import com.doyley.backgroundvideo.player.VideoPlayerListener;
import com.doyley.backgroundvideo.view.MediaController;
import com.google.android.exoplayer.VideoSurfaceView;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


public class VideoService extends Service implements MediaController.MediaPlayerControl, VideoPlayerListener {

	private static final String VIDEO_SERVICE_URI = "com.doyley.backgroundvideo.service";

	public static final String ACTION_MEDIA_BUTTON = VIDEO_SERVICE_URI + ".action.media_button";
	public static final String ACTION_PLAYER_TOGGLE_PAUSED = VIDEO_SERVICE_URI + ".action.player.TOGGLE_PAUSED";
	public static final String ACTION_PLAYER_PREVIOUS = VIDEO_SERVICE_URI + ".action.player.PREVIOUS";
	public static final String ACTION_PLAYER_NEXT = VIDEO_SERVICE_URI + ".action.player.NEXT";
	public static final String ACTION_PLAYER_PAUSE = VIDEO_SERVICE_URI + ".action.player.PAUSE";
	public static final String ACTION_PLAYER_PLAY = VIDEO_SERVICE_URI + ".action.player.PLAY";
	public static final String ACTION_LOAD_VIDEO = VIDEO_SERVICE_URI + ".action.player.LOAD_VIDEO";
	public static final String ACTION_START_VIDEO = VIDEO_SERVICE_URI + ".action.player.START_VIDEO";
	public static final String ACTION_DISCARD_VIDEO = VIDEO_SERVICE_URI + ".action.player.DISCARD_VIDEO";
	public static final String ACTION_RESUME_VIEWING_VIDEO = VIDEO_SERVICE_URI + ".action.player.ACTION_RESUME_VIEWING_VIDEO";

	public static final String EXTRA_WITH_ACTIVITY = "EXTRA_WITH_ACTIVITY";
	public static final String EXTRA_VIDEO_METADATA = "EXTRA_VIDEO_METADATA";

	private LocalBinder mLocalBinder = new LocalBinder();
	private Handler mBackgroundHandler;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private Set<VideoServiceListener> mVideoServiceListeners = new HashSet<>();
	private final Object mVideoServiceListenersMutex = new Object();

	private boolean mStartRequested;
	private boolean mActivityRequested;
	private VideoMetadata mMetadata;

	private VideoPlayer mVideoPlayer;
	private Surface mSurface;
	private FileInputStream mInputStream;

	// Local binder pattern...
	public class LocalBinder extends Binder {

		public VideoService getVideoService() {
			return VideoService.this;
		}
	}

	/** START static service accessor methods */

	public static boolean bindToService(Context context, ServiceConnection serviceConnection) {
		Intent serviceIntent = new Intent(context, VideoService.class);
		return context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	public static Intent getIntent(Context context, String action) {
		Intent intent = new Intent(action);
		intent.setClass(context, VideoService.class);
		return intent;
	}

	/** END static service accessor methods */

	/** START VideoPlayerListener callbacks */

	@Override
	public boolean onMediaPlaybackInfo(VideoPlayer.VideoPlaybackState playbackState) {
		Log.d(this.getClass().getSimpleName(), "onMediaPlaybackInfo - state = " + playbackState);
		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onMediaPlayerInfo(playbackState);
			}
		}
		return true;
	}

	@Override
	public void onMediaPlaybackCompleted() {
		Log.d(this.getClass().getSimpleName(), "onMediaPlaybackCompleted");

		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onCompletion();
			}
		}
		tearDown();
	}

	@Override
	public void onMediaPrepared(final long duration) {
		Log.d(this.getClass().getSimpleName(), "onMediaPrepared");

		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onPrepared();
			}
		}

		// only start if a start has been requested - if not, wait for an intent to start
		if (mStartRequested) {
			Log.d(this.getClass().getSimpleName(), " - start requested so try start now");
			beginVideo();
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

	public void onMediaError(Exception e) {
		Log.e(this.getClass().getSimpleName(), "onMediaError", e);
		synchronized (mVideoServiceListenersMutex) {
			for (VideoServiceListener listener : mVideoServiceListeners) {
				listener.onError();
			}
		}
		tearDown();
	}

	/** END VideoPlayerListener callbacks */

	/** START Service lifecycle methods */

	@Override
	public void onCreate() {
		Log.d(this.getClass().getSimpleName(), "onCreate");
		super.onCreate();

		// Background handler for doing media playback stuff - should never be done on main thread
		HandlerThread handlerThread = new HandlerThread("background");
		handlerThread.start();
		mBackgroundHandler = new Handler(handlerThread.getLooper());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(this.getClass().getSimpleName(), "onStartCommand - action = " + intent.getAction());

		String action = intent.getAction();
		if (action != null) {
			switch (action) {
				case ACTION_START_VIDEO:
					mActivityRequested = intent.getBooleanExtra(EXTRA_WITH_ACTIVITY, false);
					mStartRequested = true;
					if (!isPlayerPrepared()) {
						Log.d(this.getClass().getSimpleName(), "video is not prepared - call load first");
						VideoMetadata metadata = intent.getParcelableExtra(EXTRA_VIDEO_METADATA);
						mMetadata = metadata;
						loadVideo();
					}
					beginVideo();
					break;
				case ACTION_LOAD_VIDEO:
					mActivityRequested = false;
					mStartRequested = false;
					VideoMetadata metadata = intent.getParcelableExtra(EXTRA_VIDEO_METADATA);
					mMetadata = metadata;
					loadVideo();
					break;
				case ACTION_RESUME_VIEWING_VIDEO:
					mActivityRequested = true;
					mStartRequested = false;
					beginVideo();
					break;
				case ACTION_DISCARD_VIDEO:
					stop();
					break;
				case ACTION_MEDIA_BUTTON:
					handleCommandMediaButton(intent);
					break;
				case ACTION_PLAYER_TOGGLE_PAUSED:
					togglePlayPause();
					break;
				case ACTION_PLAYER_NEXT:
					next();
					break;
				case ACTION_PLAYER_PREVIOUS:
					prev();
					break;
				case ACTION_PLAYER_PAUSE:
					pause();
					break;
				case ACTION_PLAYER_PLAY:
					start();
					break;
			}
		}

		return START_NOT_STICKY;
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

	/** END Service lifecycle methods */

	/** START MediaController implementation */

	@Override
	public void start() {
		Log.d(this.getClass().getSimpleName(), "start");

		mStartRequested = true;
		if (isPlayerPrepared()) {
			Log.d(this.getClass().getSimpleName(), "- actually starting");
			mVideoPlayer.start();
			mStartRequested = false;
			mMetadata.setPaused(false);

			synchronized (mVideoServiceListenersMutex) {
				for (VideoServiceListener listener : mVideoServiceListeners) {
					listener.onPlaying(true);
				}
			}
		}
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
		return false;
	}

	@Override
	// prev button is disabled by default - future clients should revisit this
	public boolean isPrevEnabled() {
		return false;
	}

	/** END MediaController implementation */

	/**
	 * Loads the video track to the media player - does not start the video.
	 *
	 */
	private void loadVideo() {
		Log.d(this.getClass().getSimpleName(), "loadVideo - metadata = " + mMetadata);

		// This is important or else the service might get shut down when we unbind
		startService(new Intent(this, VideoService.class));

		if (mVideoPlayer == null) {
			mVideoPlayer = new VideoExoPlayerImpl(this, this, mHandler, mBackgroundHandler);
		}

		if (mMetadata.getVideoUri().startsWith("http:")) {
			mVideoPlayer.initialize(mMetadata.getVideoUri());
		} else {
			try {
				File file = new File(mMetadata.getVideoUri());
				mInputStream = new FileInputStream(file);
				mVideoPlayer.initialize(mInputStream.getFD());
			} catch (Exception ex) {
				Log.e(this.getClass().getSimpleName(), "unable to load file : ", ex);
				tearDown();
			}

		}

	}

	/** resets the surface aspect ratio - called when the display has changed */
	public void resetSurfaceAspectRatio() {
		Log.d(this.getClass().getSimpleName(), "resetSurfaceAspectRatio");
		mVideoPlayer.resetSurfaceAspectRatio();
	}

	public VideoPlayer.VideoPlaybackState getCurrentState() {
		if (isMediaPlayerActive()) {
			return mVideoPlayer.getPlaybackState();
		} else {
			return VideoPlayer.VideoPlaybackState.STATE_IDLE;
		}
	}

	/** called to send the video into the foreground or the background */
	public void setBackgrounded(boolean backgrounded, VideoSurfaceView surfaceView, Display display) {
		Log.d(this.getClass().getSimpleName(), "setBackgrounded : " + backgrounded);
		if (isMediaPlayerActive()) {
			mVideoPlayer.setBackgrounded(backgrounded);
			setForegroundSurface(surfaceView, display);
		}
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


	/** tries to start the video and the activity - they may not start if the video has not prepared
	 * - or the activity has not been specifically requested.
	 */
	private void beginVideo() {

		Log.d(this.getClass().getSimpleName(), "beginVideo : mActivityRequested = " + mActivityRequested);
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

	private void startVideoActivity(String videoTitle) {
		Log.d(this.getClass().getSimpleName(), "startVideoActivity");
		Intent intent = new Intent(this, VideoPlayerActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, videoTitle);
		startActivity(intent);
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
	private void setForegroundSurface(final VideoSurfaceView surfaceView, final Display display) {
		Log.d(this.getClass().getSimpleName(), "setForegroundSurface : " + surfaceView);

		mSurface = surfaceView != null ? surfaceView.getHolder().getSurface() : null;

		if (isMediaPlayerActive()) {
			mVideoPlayer.attachSurface(surfaceView, display);
		}
	}

	private void tearDown() {
		Log.d(this.getClass().getSimpleName(), "tearDown");

		if (mInputStream != null) {
			try {
				mInputStream.close();
			} catch (Exception ex) {
				// not much we can do - already tearing down
				Log.e(this.getClass().getSimpleName(), "problem closing inputstream : ", ex);
			}
			mInputStream = null;
		}
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

	private void quitLooperSafely(Handler handler) {
		handler.getLooper().quitSafely();
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

}
