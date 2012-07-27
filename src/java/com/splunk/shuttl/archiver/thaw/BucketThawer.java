// Copyright (C) 2011 Splunk Inc.
//
// Splunk Inc. licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.splunk.shuttl.archiver.thaw;

import static com.splunk.shuttl.archiver.LogFormatter.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.splunk.shuttl.archiver.bucketlock.BucketLocker;
import com.splunk.shuttl.archiver.bucketlock.BucketLocker.SharedLockBucketHandler;
import com.splunk.shuttl.archiver.listers.ListsBucketsFiltered;
import com.splunk.shuttl.archiver.model.Bucket;

/**
 * Interacts with the archive to thaw buckets within the users needs, which is
 * currently only a time range.
 */
public class BucketThawer {

	private final ListsBucketsFiltered listsBucketsFiltered;
	private final GetsBucketsFromArchive getsBucketsFromArchive;
	private final ThawLocationProvider thawLocationProvider;
	private final List<Bucket> successfulThawedBuckets;
	private final List<FailedBucket> failedBuckets;
	private final BucketLocker thawBucketLocker;

	public static class FailedBucket {

		public final Bucket bucket;
		public final Exception exception;

		public FailedBucket(Bucket bucket, Exception exception) {
			this.bucket = bucket;
			this.exception = exception;
		}

	}

	/**
	 * @param listsBucketsFiltered
	 *          for listing buckets in the archive.
	 * @param getsBucketsFromArchive
	 *          for getting buckets to thaw from the archive.
	 * @param thawLocationProvider
	 *          for getting the location on local disk for the thawed bucket.
	 * @param thawBucketLocker
	 *          to handle parallel thawing synchronization.
	 */
	public BucketThawer(ListsBucketsFiltered listsBucketsFiltered,
			GetsBucketsFromArchive getsBucketsFromArchive,
			ThawLocationProvider thawLocationProvider, BucketLocker thawBucketLocker) {
		this.listsBucketsFiltered = listsBucketsFiltered;
		this.getsBucketsFromArchive = getsBucketsFromArchive;
		this.thawLocationProvider = thawLocationProvider;
		this.thawBucketLocker = thawBucketLocker;

		this.successfulThawedBuckets = new ArrayList<Bucket>();
		this.failedBuckets = new ArrayList<FailedBucket>();
	}

	/**
	 * Thaws buckets within a time range from one or all indexes.
	 * 
	 * @param index
	 *          to thaw buckets from. if {@code null}, thaw from all indexes.
	 * @param earliestTime
	 *          to filter buckets.
	 * @param latestTime
	 *          to filter buckets.
	 */
	public void thawBuckets(String index, Date earliestTime, Date latestTime) {
		List<Bucket> bucketsToThaw = getFilteredBuckets(index, earliestTime,
				latestTime);
		for (Bucket bucket : bucketsToThaw)
			if (!isBucketAlreadyThawed(bucket)) {
				thawBucketLocker.callBucketHandlerUnderSharedLock(bucket,
						new ThawBucketFromArchive());
			} else {
				failedBuckets.add(new FailedBucket(bucket,
						new BucketAlreadyThawedException(bucket)));
			}
	}

	private List<Bucket> getFilteredBuckets(String index, Date earliestTime,
			Date latestTime) {
		if (index == null) {
			return listsBucketsFiltered.listFilteredBuckets(earliestTime,
					latestTime);
		} else {
			return listsBucketsFiltered.listFilteredBucketsAtIndex(index,
					earliestTime, latestTime);
		}
	}

	private boolean isBucketAlreadyThawed(Bucket bucket) {
		try {
			File thawLocation = thawLocationProvider
					.getLocationInThawForBucket(bucket);
			return thawLocation != null && thawLocation.exists();
		} catch (Exception e) {
			failedBuckets.add(new FailedBucket(bucket, e));
			Logger.getLogger(getClass()).error(
					happened("Couldn't get bucket's thaw location", "Exception", e));
			return false;
		}
	}

	/**
	 * Class to call from the {@link BucketLocker}. Thaws bucket from archive
	 * during bucket lock. It simply calls a method in this class. It is all
	 * synchronous and not asynchronous as it might seem.
	 */
	private class ThawBucketFromArchive implements SharedLockBucketHandler {

		@Override
		public void handleSharedLockedBucket(Bucket bucket) {
			BucketThawer.this.thawBucketFromArchive(bucket);
		}

	}

	private void thawBucketFromArchive(Bucket bucket) {
		try {
			Bucket thawedBucket = getsBucketsFromArchive.getBucketFromArchive(bucket);
			successfulThawedBuckets.add(thawedBucket);
		} catch (ThawTransferFailException e) {
			failedBuckets.add(new FailedBucket(bucket, e));
		} catch (ImportThawedBucketFailException e) {
			failedBuckets.add(new FailedBucket(bucket, e));
		}
	}

	/**
	 * @return buckets that succeeded to be thawed.
	 */
	public List<Bucket> getThawedBuckets() {
		return successfulThawedBuckets;
	}

	/**
	 * @return buckets that failed to be thawed.
	 */
	public List<FailedBucket> getFailedBuckets() {
		return failedBuckets;
	}

}
