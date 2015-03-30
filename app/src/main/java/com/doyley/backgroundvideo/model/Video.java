package com.doyley.backgroundvideo.model;

import java.util.List;
import java.util.Set;


public class Video {

	private String mDashManifestId;
	private List<VideoFlatFile> mFlatFiles;

	public Video(String dashManifestId, List<VideoFlatFile> flatFiles) {
		mDashManifestId = dashManifestId;
		mFlatFiles = flatFiles;
	}

	public String getDashManifestId() {
		return mDashManifestId;
	}

	/**
	 * Returns the most suited flat file video given the specified arguments. May return null
	 * if no suitable flat file representation was found.
	 * @param validMimeTypes valid mime types
	 * @param targetBitrate the desired bitrate
	 * @return the most suited flat file representation given the specified arguments. May be
	 * null.
	 */
	public VideoFlatFile getMostSuitableFlatFile(Set<String> validMimeTypes, int targetBitrate) {
		VideoFlatFile closestVideo = null;
		int closestValue = Integer.MAX_VALUE;
		// right now find the flat file that is closest to the target bitrate
		for (VideoFlatFile flatFile : mFlatFiles) {
			if (validMimeTypes.contains(flatFile.getMimetype())) {
				int distanceFromTarget = Math.abs(targetBitrate - flatFile.getBitrate());
				if (distanceFromTarget <= closestValue) {
					closestValue = distanceFromTarget;
					closestVideo = flatFile;
				}
			}
		}
		return closestVideo;
	}

	public boolean isAdaptiveStreamingSupported() {
		return mDashManifestId != null;
	}
}
