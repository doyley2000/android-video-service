package com.doyley.backgroundvideo.player;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.VideoSurfaceView;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.source.DefaultSampleSource;
import com.google.android.exoplayer.source.FrameworkSampleExtractor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class VideoExoPlayerImpl implements VideoPlayer, ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener, MediaCodecAudioTrackRenderer.EventListener {


	public static final int RENDERER_COUNT = 2;
	public static final int TYPE_VIDEO = 0;
	public static final int TYPE_AUDIO = 1;

	public static final SparseArray<VideoPlaybackState> PLAYBACK_STATES;

	static {
		PLAYBACK_STATES = new SparseArray<>(5);
		PLAYBACK_STATES.put(ExoPlayer.STATE_IDLE, VideoPlaybackState.STATE_IDLE);
		PLAYBACK_STATES.put(ExoPlayer.STATE_PREPARING, VideoPlaybackState.STATE_PREPARING);
		PLAYBACK_STATES.put(ExoPlayer.STATE_BUFFERING, VideoPlaybackState.STATE_BUFFERING);
		PLAYBACK_STATES.put(ExoPlayer.STATE_READY, VideoPlaybackState.STATE_READY);
		PLAYBACK_STATES.put(ExoPlayer.STATE_ENDED, VideoPlaybackState.STATE_ENDED);
	}

	private final Handler mMainHandler;
	private final Handler mBackgroundHandler;
	private ExoPlayer mExoPlayer;
	private final VideoPlayerListener mVideoPlayerListener;
	private MediaCodecAudioTrackRenderer mAudioTrackRenderer;
	private MediaCodecVideoTrackRenderer mVideoTrackRenderer;
	private Context mContext;
	private SurfaceView mSurfaceView;
	private boolean mPlayerPrepared;

	private int mWidth;
	private int mHeight;
	private float mPixelWidthHeightRatio;
	private FileInputStream mInputStream;

	public VideoExoPlayerImpl(Context context, VideoPlayerListener videoPlayerListener, Handler mainHandler, Handler backgroundHandler) {
		mContext = context;
		mVideoPlayerListener = videoPlayerListener;
		mMainHandler = mainHandler;
		mBackgroundHandler = backgroundHandler;
	}

	@Override
	public void initialize(final FileDescriptor fileDescriptor) {
		Log.d(this.getClass().getSimpleName(), "initialize : " + fileDescriptor);
		initialize(new FrameworkSampleExtractor(fileDescriptor, 0, 0x7ffffffffffffffL));
	}

	@Override
	public void initialize(final String videoUri) {
		Log.d(this.getClass().getSimpleName(), "initialize : " + videoUri);
		initialize(new FrameworkSampleExtractor(mContext, Uri.parse(videoUri), null));

	}

	private void initialize(FrameworkSampleExtractor frameworkSampleExtractor) {

		if (mExoPlayer != null) {
			mExoPlayer.release();
			mExoPlayer = null;
		}
		mPlayerPrepared = false;
		// ...initialize the MediaPlayer here...
		mExoPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
		mExoPlayer.addListener(this);
		DefaultSampleSource videoSource = new DefaultSampleSource(frameworkSampleExtractor, 2);
		mVideoTrackRenderer = new MediaCodecVideoTrackRenderer(videoSource, null, true,
				MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, null, mBackgroundHandler, this, 50);
		mAudioTrackRenderer = new MediaCodecAudioTrackRenderer(videoSource, mBackgroundHandler, this);

		mExoPlayer.prepare(mVideoTrackRenderer, mAudioTrackRenderer);

		mExoPlayer.setRendererEnabled(TYPE_VIDEO, false);
		mExoPlayer.setRendererEnabled(TYPE_AUDIO, true);

	}

	@Override
	public void resetSurfaceAspectRatio() {

		final VideoSurfaceView view = (VideoSurfaceView) mSurfaceView;
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				view.setVideoWidthHeightRatio(
						mWidth == 0 ? 1 : (mPixelWidthHeightRatio * mWidth) / mHeight);
				mVideoPlayerListener.onVideoSizeChanged();
			}
		});

	}

	public void setBackgrounded(boolean backgrounded) {
		if (isMediaPlayerActive()) {
			if (backgrounded) {
				mExoPlayer.setRendererEnabled(TYPE_VIDEO, false);
			} else {
				mExoPlayer.setRendererEnabled(TYPE_VIDEO, true);
			}
		}
	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		mVideoPlayerListener.onMediaPlaybackInfo(PLAYBACK_STATES.get(playbackState));
		switch (playbackState) {
			case ExoPlayer.STATE_BUFFERING:
				if (!mPlayerPrepared) {
					mPlayerPrepared = true;
					mVideoPlayerListener.onMediaPrepared(mExoPlayer.getDuration());
				}
				break;
			case ExoPlayer.STATE_PREPARING:
				break;
			case ExoPlayer.STATE_IDLE:
				break;
			case ExoPlayer.STATE_ENDED:
				mVideoPlayerListener.onMediaPlaybackCompleted();
				break;
			case ExoPlayer.STATE_READY:
				if (!mPlayerPrepared) {
					mPlayerPrepared = true;
					mVideoPlayerListener.onMediaPrepared(mExoPlayer.getDuration());
				}
				break;
		}
	}

	@Override
	public void onPlayWhenReadyCommitted() {

	}

	@Override
	public void onPlayerError(ExoPlaybackException error) {
		mVideoPlayerListener.onMediaError(error);
	}

	@Override
	public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {

	}

	@Override
	public void onAudioTrackWriteError(AudioTrack.WriteException e) {

	}

	@Override
	public void onDroppedFrames(int i, long l) {

	}

	@Override
	public void onVideoSizeChanged(final int width, final int height, final float pixelWidthHeightRatio) {

		mWidth = width;
		mHeight = height;
		mPixelWidthHeightRatio = pixelWidthHeightRatio;

		resetSurfaceAspectRatio();
	}


	@Override
	public void onDrawnToSurface(Surface surface) {
		mVideoPlayerListener.onMediaDrawnToSurface();
	}

	@Override
	public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
		mVideoPlayerListener.onMediaError(e);
	}

	@Override
	public void onCryptoError(MediaCodec.CryptoException e) {
		mVideoPlayerListener.onMediaError(e);
	}

	@Override
	public boolean isMediaPlayerActive() {
		return mExoPlayer != null;
	}

	@Override
	public void attachSurface(SurfaceView surfaceView, Display display) {
		mSurfaceView = surfaceView;
		Surface surface = surfaceView != null ? surfaceView.getHolder().getSurface() : null;
		if (surface != null) {
			mExoPlayer.sendMessage(mVideoTrackRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
		} else {
			mExoPlayer.blockingSendMessage(mVideoTrackRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, null);
		}
	}

	@Override
	public void tearDown() {
		if (mExoPlayer != null) {
			mExoPlayer.release();
			mExoPlayer = null;
		}
	}

	@Override
	public long getDuration() {
		return mExoPlayer.getDuration();
	}

	@Override
	public long getCurrentPosition() {
		return mExoPlayer.getCurrentPosition();
	}

	@Override
	public int getBufferedPercentage() {
		return mExoPlayer.getBufferedPercentage();
	}

	@Override
	public boolean isPlaying() {
		return mExoPlayer.getPlayWhenReady();
	}

	@Override
	public VideoPlaybackState getPlaybackState() {
		return PLAYBACK_STATES.get(mExoPlayer.getPlaybackState());
	}

	@Override
	public void seekTo(long position) {
		mExoPlayer.seekTo(position);
	}

	@Override
	public void pause() {
		mExoPlayer.setPlayWhenReady(false);
	}

	@Override
	public void start() {
		mExoPlayer.setPlayWhenReady(true);
	}

	@Override
	public void stop() {
		mExoPlayer.stop();
		tearDown();
	}


}
