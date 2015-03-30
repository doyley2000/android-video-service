package com.doyley.backgroundvideo.model;

public class VideoFlatFile {

	private final String mUrl;
	private final int mBitrate;
	private final String mMimetype;

	public VideoFlatFile(String url, int bitrate, String mimetype) {
		mUrl = url;
		mBitrate = bitrate;
		mMimetype = mimetype;
	}

	public String getUrl() {
		return mUrl;
	}

	public int getBitrate() {
		return mBitrate;
	}

	public String getMimetype() {
		return mMimetype;
	}

	@Override
	public String toString() {
		return "VideoFlatFile{" +
				"mUrl='" + mUrl + '\'' +
				", mBitrate=" + mBitrate +
				", mMimetype='" + mMimetype + '\'' +
				'}';
	}
}
