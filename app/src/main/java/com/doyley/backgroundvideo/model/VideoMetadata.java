package com.doyley.backgroundvideo.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class VideoMetadata implements Parcelable {

	public static final Creator<VideoMetadata> CREATOR = new Creator<VideoMetadata>() {
		public VideoMetadata createFromParcel(Parcel in) {
			return new VideoMetadata(in);
		}

		public VideoMetadata[] newArray(int size) {
			return new VideoMetadata[size];
		}
	};

	private String mVideoUri;
	private String mTitle;
	private String mArtist;
	private long mDuration;
	private String mImageUrl;
	private String mClickUrl;
	private boolean mNextEnabled;
	private boolean mPrevEnabled;
	private boolean mIsPaused;

	public VideoMetadata(final String videoUri, final String title, final String artist, final long duration,
	                     final String imageUrl, final String clickUrl, boolean nextEnabled,
	                     boolean prevEnabled, boolean isPaused) {
		mVideoUri = videoUri;
		mTitle = title;
		mArtist = artist;
		mDuration = duration;
		mImageUrl = imageUrl;
		mClickUrl = clickUrl;
		mNextEnabled = nextEnabled;
		mPrevEnabled = prevEnabled;
		mIsPaused = isPaused;
	}

	private VideoMetadata(Parcel in) {
		mVideoUri = in.readString();
		mTitle = in.readString();
		mArtist = in.readString();
		mDuration = in.readLong();
		mImageUrl = in.readString();
		mClickUrl = in.readString();
		mNextEnabled = in.readByte() != 0;
		mPrevEnabled = in.readByte() != 0;
		mIsPaused = in.readByte() != 0;
	}

	public String getVideoUri() {
		return mVideoUri;
	}

	public String getTitle() {
		return mTitle;
	}

	public String getArtist() {
		return mArtist;
	}

	public long getDuration() {
		return mDuration;
	}

	public String getImageUrl() {
		return mImageUrl;
	}

	public String getClickUrl() {
		return mClickUrl;
	}

	public boolean isNextEnabled() {
		return mNextEnabled;
	}

	public boolean isPrevEnabled() {
		return mPrevEnabled;
	}

	public boolean isPaused() {
		return mIsPaused;
	}


	public void setTitle(String title) {
		mTitle = title;
	}

	public void setArtist(String artist) {
		mArtist = artist;
	}

	public void setDuration(long duration) {
		mDuration = duration;
	}

	public void setImageUrl(String imageUrl) {
		mImageUrl = imageUrl;
	}

	public void setClickUrl(String clickUrl) {
		mClickUrl = clickUrl;
	}

	public void setNextEnabled(boolean nextEnabled) {
		mNextEnabled = nextEnabled;
	}

	public void setPrevEnabled(boolean prevEnabled) {
		mPrevEnabled = prevEnabled;
	}

	public void setPaused(boolean isPaused) {
		mIsPaused = isPaused;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mVideoUri);
		dest.writeString(mTitle);
		dest.writeString(mArtist);
		dest.writeLong(mDuration);
		dest.writeString(mImageUrl);
		dest.writeString(mClickUrl);
		dest.writeByte((byte) (mNextEnabled ? 1 : 0));
		dest.writeByte((byte) (mPrevEnabled ? 1 : 0));
		dest.writeByte((byte) (mIsPaused ? 1 : 0));
	}

	@Override
	public String toString() {
		return  VideoMetadata.class.getSimpleName() + "{" +
				", mVideoUri=" + mVideoUri +
				", mTitle=" + mTitle +
				", mArtist=" + mArtist +
				", mDuration=" + mDuration +
				", mImageUrl=" + mImageUrl +
				", mClickUrl=" + mClickUrl +
				", mNextEnabled=" + mNextEnabled +
				", mNextEnabled=" + mPrevEnabled +
				", mIsPaused=" + mIsPaused +
				'}';
	}

}

