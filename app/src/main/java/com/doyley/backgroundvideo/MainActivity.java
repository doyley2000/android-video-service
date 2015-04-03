package com.doyley.backgroundvideo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.doyley.backgroundvideo.model.VideoMetadata;
import com.doyley.backgroundvideo.player.VideoPlayer;
import com.doyley.backgroundvideo.service.VideoService;

public class MainActivity extends ActionBarActivity {

	private Button mPrepareVideoButton;
	private Button mStartVideoButton;
	private Button mStartVideoActivityButton;
	private Button mStopVideoButton;
	private Button mResumeViewingButton;
	private TextView mStatus;
	private VideoService mVideoService;

	private boolean mLoadRequested;
	private boolean mVideoPreparing;


	private ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(MainActivity.this.getClass().getSimpleName(), "onServiceConnected (VideoService");
			mVideoService = ((VideoService.LocalBinder) service).getVideoService();

			mVideoService.registerListener(mVideoServiceListener);

			if (mLoadRequested) {
				loadVideo();
				mLoadRequested = false;
			}

			updateButtons();

		}

		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	};

	private VideoService.VideoServiceListener mVideoServiceListener = new VideoService.VideoServiceListener() {

		@Override
		public void onCompletion() {
			addToLog("Video Complete");
			updateButtons();

		}

		@Override
		public void onPrepared() {
			addToLog("Video Prepared");
			mVideoPreparing = false;
			updateButtons();
		}

		@Override
		public void onMediaPlayerInfo(VideoPlayer.VideoPlaybackState playbackState) {
			addToLog("State : " + playbackState);
			updateButtons();
		}

		@Override
		public void onPlaying(boolean isPlaying) {
			addToLog("Video Playing : " + isPlaying);
			updateButtons();
		}

		@Override
		public void onError() {
			addToLog("Video Error");
		}

		@Override
		public void notifyVideoSizeChange() {

		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		VideoService.bindToService(this, mServiceConnection);

		mPrepareVideoButton = (Button)findViewById(R.id.prepare_video);
		mPrepareVideoButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				prepareVideo();
			}
		});
		mResumeViewingButton = (Button)findViewById(R.id.resume_viewing);
		mResumeViewingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startVideo(true, false);
			}
		});
		mStartVideoButton = (Button)findViewById(R.id.start_video);
		mStartVideoButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startVideo(false, true);
			}
		});
		mStartVideoActivityButton = (Button)findViewById(R.id.start_video_activity);
		mStartVideoActivityButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startVideo(true, true);
			}
		});
		mStopVideoButton = (Button)findViewById(R.id.stop_video);
		mStopVideoButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stopVideoService();
			}
		});

		mStatus = (TextView)findViewById(R.id.status);
		mStatus.setMovementMethod(new ScrollingMovementMethod());
		addToLog("Video Not Started");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mVideoService != null) {
			updateButtons();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mVideoService != null) {
			mVideoService.unregisterListener(mVideoServiceListener);
			unbindService(mServiceConnection);
			mVideoService = null;
		}
	}

	private void updateButtons() {
		VideoPlayer.VideoPlaybackState state = VideoPlayer.VideoPlaybackState.STATE_IDLE;
		if (mVideoService != null) {
			state = mVideoService.getCurrentState();
		}

		switch (state) {
			case STATE_IDLE:
				mPrepareVideoButton.setEnabled(true);
				mResumeViewingButton.setEnabled(false);
				mStartVideoButton.setEnabled(true);
				mStartVideoActivityButton.setEnabled(true);
				mStopVideoButton.setEnabled(false);
				break;
			case STATE_PREPARING:
				mPrepareVideoButton.setEnabled(false);
				mResumeViewingButton.setEnabled(false);
				mStartVideoButton.setEnabled(true);
				mStartVideoActivityButton.setEnabled(true);
				mStopVideoButton.setEnabled(false);
				break;
			case STATE_BUFFERING:
				mPrepareVideoButton.setEnabled(false);
				mResumeViewingButton.setEnabled(true);
				mStartVideoButton.setEnabled(false);
				mStartVideoActivityButton.setEnabled(false);
				mStopVideoButton.setEnabled(true);
				break;
			case STATE_READY:
				if (mVideoService.getCurrentPosition() == 0 && !mVideoService.isPlaying()) {
					mPrepareVideoButton.setEnabled(false);
					mResumeViewingButton.setEnabled(true);
					mStartVideoButton.setEnabled(true);
					mStartVideoActivityButton.setEnabled(true);
					mStopVideoButton.setEnabled(true);
				} else {
					mPrepareVideoButton.setEnabled(false);
					mResumeViewingButton.setEnabled(true);
					mStartVideoButton.setEnabled(false);
					mStartVideoActivityButton.setEnabled(false);
					mStopVideoButton.setEnabled(true);
				}
				break;
			case STATE_ENDED:
				mPrepareVideoButton.setEnabled(true);
				mResumeViewingButton.setEnabled(false);
				mStartVideoButton.setEnabled(true);
				mStartVideoActivityButton.setEnabled(true);
				mStopVideoButton.setEnabled(false);
				break;
		}
	}

	private void addToLog(String s) {
		Log.d(this.getClass().getSimpleName(), s + " (VideoService)");
	}


	private void stopVideoService() {
		Intent intent = VideoService.getIntent(this, VideoService.ACTION_DISCARD_VIDEO);
		startService(intent);
		addToLog("Video Stopped");
		unbindService(mServiceConnection);
		mVideoService = null;
	}

	private void startVideo(boolean withActivity, boolean startPlayback) {
		addToLog("Video Starting : withActivity = " + withActivity);
		if (!mVideoPreparing) {
			if (mVideoService == null || !mVideoService.isPlayerPrepared()) {
				addToLog(" - need to prepare first");
				prepareVideo();
			}
		}
		Intent videoServiceIntent = VideoService.getIntent(this, VideoService.ACTION_START_VIDEO);
		videoServiceIntent.putExtra(VideoService.EXTRA_WITH_ACTIVITY, withActivity);
		videoServiceIntent.putExtra(VideoService.EXTRA_START_PLAYBACK, startPlayback);
		startService(videoServiceIntent);
	}

	private void prepareVideo() {
		addToLog("Video Preparing");

		if (mVideoService != null) {
			loadVideo();
		} else {
			VideoService.bindToService(this, mServiceConnection);
			mLoadRequested = true;
		}
	}

	private void loadVideo() {
		Log.d(MainActivity.this.getClass().getSimpleName(), "loadVideo (VideoService)");
		mVideoPreparing = true;

		VideoMetadata metadata = new VideoMetadata("http://www.quirksmode.org/html5/videos/big_buck_bunny.mp4", "Big Buck Bunny", "Blender Foundation", 12345,
				"http://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
				"http://en.wikipedia.org/wiki/Big_Buck_Bunny", true, false, true);

		mVideoService.loadVideo(metadata);

	}
}
