package com.doyley.backgroundvideo.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.Locale;
import com.doyley.backgroundvideo.R;

/**
 * This is an adaptation of the android MediaController class - it is not customizable so we had to
 * create a new class with our own customizations.  Please consult that class if looking to add new
 * functionality as it may have what you need.
 */
public class MediaController extends FrameLayout {

	public static final int PROGRESS_BAR_MAX = 1000;
	private final Context mContext;
	private MediaPlayerControl mPlayer;
	private ViewGroup mAnchor;
	private ProgressBar mProgress;
	private TextView mEndTime;
	private TextView mCurrentTime;
	private boolean mControlsVisible;
	public static final int DEFAULT_TIMEOUT = 2500;
	private static final int HIDE_CONTROLS = 1;
	private static final int SHOW_PROGRESS = 2;
	private static final int SHUTDOWN = 3;
	@SuppressWarnings("PMD.AvoidStringBufferField") // it's cleared in between usages
			StringBuilder mFormatBuilder;
	Formatter mFormatter;
	private CheckableImageButton mPlayPauseButton;
	private ImageButton mNextButton;
	private ImageButton mPrevButton;
	private final Handler mHandler = new MessageHandler(this);
	private View mVideoController;
	private TextView mTitleView;

	public MediaController(Context context) {
		super(context);

		mContext = context;
		inflate(mContext, R.layout.media_controller, this);
		setLayoutTransition(new LayoutTransition());
		initControllerView();
	}

	public void setMediaPlayer(MediaPlayerControl player) {
		mPlayer = player;
		if (mPlayer != null) {
			mEndTime.setText(stringForTime(mPlayer.getDuration()));
			updatePausePlay();
		}
	}

	/**
	 * Set the view that acts as the anchor for the control view.
	 * This can for example be a VideoView, or your Activity's main view.
	 *
	 * @param view The view to which to anchor the controller when it is visible.
	 */
	public void setAnchorView(FrameLayout view) {
		mAnchor = view;

		int margin = (int) mContext.getResources().getDimension(R.dimen.player_controls_margin);
		MarginLayoutParams marginParams = new MarginLayoutParams(view.getLayoutParams());
		marginParams.setMargins(0, 0, 0, margin);

		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(marginParams);
		layoutParams.gravity = Gravity.BOTTOM;

		view.addView(this, layoutParams);
	}

	private void initControllerView() {

		this.setVisibility(View.GONE);

		findViewById(R.id.controls).setVisibility(VISIBLE);

		mVideoController = findViewById(R.id.player_controller);

		mPlayPauseButton = (CheckableImageButton) findViewById(R.id.btn_play);
		if (mPlayPauseButton != null) {
			mPlayPauseButton.requestFocus();
			mPlayPauseButton.setOnClickListener(mPauseListener);
			Drawable pauseDrawable = mContext.getDrawable(R.drawable.ic_media_pause);
			Drawable playDrawable = mContext.getDrawable(R.drawable.ic_media_play);

			StateListDrawable drawable = new StateListDrawable();
			drawable.addState(new int[]{-android.R.attr.state_checked}, pauseDrawable);
			drawable.addState(new int[]{android.R.attr.state_checked}, playDrawable);

			mPlayPauseButton.setImageDrawable(drawable);
		}

		mNextButton = (ImageButton) findViewById(R.id.btn_next);
		mNextButton.setImageResource(R.drawable.ic_media_next);
		mPrevButton = (ImageButton) findViewById(R.id.btn_prev);
		mPrevButton.setImageResource(R.drawable.ic_media_previous);
		setupPrevNextButtons();

		mProgress = (SeekBar) findViewById(R.id.seekbar);
		mProgress.setMax(PROGRESS_BAR_MAX);
		mProgress.setEnabled(false);

		mEndTime = (TextView) findViewById(R.id.time_length);
		mCurrentTime = (TextView) findViewById(R.id.time_position);
		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

		mTitleView = (TextView) findViewById(R.id.video_title);

		setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mControlsVisible) {
					hide();
				} else {
					show(DEFAULT_TIMEOUT);
				}
			}
		});

		setupPrevNextButtons();
	}

	/**
	 * Show the controller on screen. It will go away
	 * automatically after 3 seconds of inactivity.
	 */
	public void show() {
		show(DEFAULT_TIMEOUT);
	}

	/**
	 * Show the controller on screen. It will go away
	 * automatically after 'timeout' milliseconds of inactivity.
	 *
	 * @param timeout The timeout in milliseconds. Use 0 to show
	 *                the controller until hide() is called.
	 */
	public void show(int timeout) {

		if (!mControlsVisible && mAnchor != null) {
			updateProgress();
			if (mPlayPauseButton != null) {
				mPlayPauseButton.requestFocus();
			}

			this.setVisibility(View.VISIBLE);

			mVideoController.setVisibility(View.VISIBLE);
			mTitleView.setVisibility(View.VISIBLE);
			mControlsVisible = true;
		}

		updatePausePlay();

		// cause the progress bar to be updated even if mControlsVisible
		// was already true.  This happens, for example, if we're
		// paused with the progress bar showing the user hits play.
		mHandler.sendEmptyMessage(SHOW_PROGRESS);

		Message msg = mHandler.obtainMessage(HIDE_CONTROLS);
		if (timeout != 0) {
			mHandler.removeMessages(HIDE_CONTROLS);
			mHandler.sendMessageDelayed(msg, timeout);
		}
	}

	public void shutdown() {
		mHandler.removeMessages(HIDE_CONTROLS);
		mHandler.removeMessages(SHOW_PROGRESS);
		Message msg = Message.obtain();
		msg.what = SHUTDOWN;
		mHandler.sendMessageAtFrontOfQueue(msg);
	}

	public void setTitleText(String text) {
		mTitleView.setText(text);
	}

	private void setSkipEnabled(boolean enable) {
		mNextButton.setEnabled(enable);
	}

	/**
	 * Remove the controller from the screen.
	 */
	public void hide() {
		if (mAnchor == null) {
			return;
		}

		mVideoController.setVisibility(View.GONE);
		mTitleView.setVisibility(View.GONE);

		// actual hide is down at the end of the animation
		mControlsVisible = false;
	}

	private String stringForTime(long timeMs) {
		int totalSeconds = (int)(timeMs / 1000);

		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;

		mFormatBuilder.setLength(0);
		if (hours > 0) {
			return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
		} else {
			return mFormatter.format("%d:%02d", minutes, seconds).toString();
		}
	}

	private long updateProgress() {
		if (mPlayer == null) {
			return 0;
		}

		long position = mPlayer.getCurrentPosition();
		long duration = mPlayer.getDuration();
		if (mProgress != null) {
			if (duration > 0) {
				// use long to avoid overflow
				long pos = PROGRESS_BAR_MAX * position / duration;
				mProgress.setProgress((int) pos);
			}
			int percent = mPlayer.getBufferPercentage();
			mProgress.setSecondaryProgress(percent * 10);
		}

		mCurrentTime.setText(stringForTime(position));

		if (mPlayer.isNextEnabled()) {
			setSkipEnabled(true);
		}


		return position;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent ev) {
		show(DEFAULT_TIMEOUT);
		return false;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {

		int keyCode = event.getKeyCode();
		final boolean uniqueDown = event.getRepeatCount() == 0
				&& event.getAction() == KeyEvent.ACTION_DOWN;

		switch (keyCode) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_SPACE:
				if (uniqueDown) {
					doPauseResume();
					show(DEFAULT_TIMEOUT);
					if (mPlayPauseButton != null) {
						mPlayPauseButton.requestFocus();
					}
				}
				return true;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				if (uniqueDown && !mPlayer.isPlaying()) {
					mPlayer.start();
					updatePausePlay();
					show(DEFAULT_TIMEOUT);
				}
				return true;
			case KeyEvent.KEYCODE_MEDIA_STOP:
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				if (uniqueDown && mPlayer.isPlaying()) {
					mPlayer.pause();
					updatePausePlay();
					show(DEFAULT_TIMEOUT);
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_MUTE:
				return super.dispatchKeyEvent(event);
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
				if (uniqueDown) {
					hide();
				}
				return true;
		}

		show(DEFAULT_TIMEOUT);
		return super.dispatchKeyEvent(event);
	}

	private View.OnClickListener mPauseListener = new View.OnClickListener() {
		public void onClick(View v) {
			doPauseResume();
			show(DEFAULT_TIMEOUT);
		}
	};

	private View.OnClickListener mNextListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayer != null) {
				mPlayer.next();
			}
			show(DEFAULT_TIMEOUT);
		}
	};

	private View.OnClickListener mPrevListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (mPlayer != null) {
				mPlayer.prev();
			}
			show(DEFAULT_TIMEOUT);
		}
	};

	public void updatePausePlay() {
		if (mPlayPauseButton == null || mPlayer == null) {
			return;
		}

		if (mPlayer.isPlaying())
			mPlayPauseButton.setImageResource(R.drawable.ic_media_pause);
		else {
			mPlayPauseButton.setImageResource(R.drawable.ic_media_play);
		}
	}

	private void doPauseResume() {
		if (mPlayer == null) {
			return;
		}

		if (mPlayer.isPlaying()) {
			mPlayer.pause();
		} else {
			mPlayer.start();
		}
		updatePausePlay();
	}

	private void setupPrevNextButtons() {
		if (mNextButton != null) {
			mNextButton.setVisibility(View.VISIBLE);
			mNextButton.setOnClickListener(mNextListener);
			mNextButton.setEnabled(mNextListener != null && (mPlayer != null && mPlayer.isNextEnabled()));
		}

		if (mPrevButton != null) {
			mPrevButton.setVisibility(View.VISIBLE);
			mPrevButton.setOnClickListener(mPrevListener);
			mPrevButton.setEnabled(mPrevListener != null && (mPlayer != null && mPlayer.isPrevEnabled()));
		}
	}

	public interface MediaPlayerControl {

		void start();

		void pause();

		long getDuration();

		long getCurrentPosition();

		void seekTo(long pos);

		boolean isPlaying();

		int getBufferPercentage();

		void next();

		void prev();

		boolean isNextEnabled();

		boolean isPrevEnabled();
	}

	private static class MessageHandler extends Handler {
		private final WeakReference<MediaController> mView;

		MessageHandler(MediaController view) {
			mView = new WeakReference<MediaController>(view);
		}

		@Override
		public void handleMessage(Message msg) {
			MediaController view = mView.get();
			if (view == null || view.mPlayer == null) {
				return;
			}

			long pos;
			switch (msg.what) {
				case HIDE_CONTROLS:
					if (view.mPlayer.isPlaying()) {
						view.hide();
					}
					break;
				case SHOW_PROGRESS:
					pos = view.updateProgress();
					msg = obtainMessage(SHOW_PROGRESS);
					sendMessageDelayed(msg, PROGRESS_BAR_MAX - (pos % PROGRESS_BAR_MAX));
					break;
				case SHUTDOWN:
					view.mPlayer = null;
					break;
			}
		}
	}


}

