package com.doyley.backgroundvideo.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.doyley.backgroundvideo.R;
import com.doyley.backgroundvideo.player.VideoPlayer;
import com.doyley.backgroundvideo.service.VideoService;
import com.doyley.backgroundvideo.service.VideoServiceListener;
import com.doyley.backgroundvideo.view.MediaController;
import com.google.android.exoplayer.VideoSurfaceView;

public class VideoPlayerActivity extends Activity {

	public static final int CONTROLLER_TIMEOUT_MS = 2500;

	public static final String ACTIVITY_PREFIX = "com.doyley.backgroundvideo.activity.VideoPlayerActivity";
	public static final String EXTRA_TITLE = ACTIVITY_PREFIX + "EXTRA_TITLE";

	private VideoSurfaceView mVideoSurfaceView;
	private VideoService mVideoService;
	private MediaController mMediaController;
	private boolean mSurfaceCreated;
	private View mThrobberView;
	private View mShutterView;

	private VideoServiceListener mVideoServiceListener = new VideoServiceListener() {

		@Override
		public void onPrepared() {
			mMediaController.show(CONTROLLER_TIMEOUT_MS);
		}

		@Override
		public void onMediaPlayerInfo(VideoPlayer.VideoPlaybackState playbackState) {

			switch (playbackState) {
				case STATE_BUFFERING:
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mThrobberView.setVisibility(View.VISIBLE);
						}
					});
					break;
				default:
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mThrobberView.setVisibility(View.GONE);
						}
					});
					break;
			}
		}

		@Override
		public void onPlaying(boolean isPlaying) {
			if (isPlaying) {
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			} else {
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		}

		@Override
		public void onCompletion() {
			shutDown();
		}

		@Override
		public void onError() {
			shutDown();
		}

		@Override
		public void notifyVideoSizeChange() {
			if (mShutterView != null) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mShutterView.setVisibility(View.GONE);
					}
				});
			}
		}
	};

	private ServiceConnection mVideoServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mVideoService = ((VideoService.LocalBinder) service).getVideoService();
			mVideoService.registerListener(mVideoServiceListener);

			mMediaController.setMediaPlayer(mVideoService);

			String title = getIntent().getStringExtra(EXTRA_TITLE);
			mMediaController.setTitleText(title);
			if (mVideoService.isPlayerPrepared()) {
				mThrobberView.setVisibility(View.GONE);
			}

			trySetVideoSurfaceView();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mVideoService = null;
		}
	};

	private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			mSurfaceCreated = true;
			trySetVideoSurfaceView();
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			mSurfaceCreated = false;
			if (mVideoService != null) {
				mVideoService.setBackgrounded(true, null, getWindowManager().getDefaultDisplay());
			}
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_player);
		mVideoSurfaceView = (VideoSurfaceView) findViewById(R.id.video_surface);
		mMediaController = new MediaController(this);
		FrameLayout videoContainer = (FrameLayout) findViewById(R.id.video_container);
		mMediaController.setAnchorView(videoContainer);
		mShutterView = findViewById(R.id.shutter);

		mThrobberView = findViewById(R.id.throbber);
		mThrobberView.setVisibility(View.GONE);

		mVideoSurfaceView.getHolder().addCallback(mSurfaceCallback);

		videoContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mMediaController.show();
			}
		});
		VideoService.bindToService(VideoPlayerActivity.this, mVideoServiceConnection);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (mVideoService != null && mVideoSurfaceView != null) {
			if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				hideSystemUI();
			} else {
				showSystemUI();
			}
			mVideoService.resetSurfaceAspectRatio();
		}
	}

	@Override
	protected void onDestroy() {

		if (mVideoService != null) {
			mVideoService.unregisterListener(mVideoServiceListener);
			unbindService(mVideoServiceConnection);
		}
		super.onDestroy();
	}

	/** STOP Activity lifecycle methods */

	private void trySetVideoSurfaceView() {
		if (mVideoService != null && mSurfaceCreated) {
			mVideoService.setBackgrounded(false, mVideoSurfaceView, getWindowManager().getDefaultDisplay());
		}
	}

	private void shutDown() {
		mMediaController.shutdown();
		finish();
	}

	private void hideSystemUI() {
		int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
				| View.SYSTEM_UI_FLAG_FULLSCREEN; // hide status bar
		uiFlags = makeImmersive(uiFlags);
		getWindow().getDecorView().setSystemUiVisibility(uiFlags);
	}

	private int makeImmersive(int uiFlags) {
		return uiFlags | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
	}

	private void showSystemUI() {
		int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
		getWindow().getDecorView().setSystemUiVisibility(uiFlags);
	}


}

