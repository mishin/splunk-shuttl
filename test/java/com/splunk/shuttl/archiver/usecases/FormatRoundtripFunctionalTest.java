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
package com.splunk.shuttl.archiver.usecases;

import static com.splunk.shuttl.testutil.TUtilsFile.*;
import static com.splunk.shuttl.testutil.TUtilsFunctional.*;
import static java.util.Arrays.*;
import static org.testng.Assert.*;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.splunk.shuttl.archiver.LocalFileSystemPaths;
import com.splunk.shuttl.archiver.archive.ArchiveConfiguration;
import com.splunk.shuttl.archiver.archive.BucketArchiver;
import com.splunk.shuttl.archiver.archive.BucketFormat;
import com.splunk.shuttl.archiver.archive.BucketShuttlerFactory;
import com.splunk.shuttl.archiver.filesystem.ArchiveFileSystem;
import com.splunk.shuttl.archiver.filesystem.ArchiveFileSystemFactory;
import com.splunk.shuttl.archiver.listers.ListsBucketsFilteredFactory;
import com.splunk.shuttl.archiver.model.Bucket;
import com.splunk.shuttl.archiver.model.IllegalIndexException;
import com.splunk.shuttl.archiver.model.LocalBucket;
import com.splunk.shuttl.archiver.thaw.BucketThawer;
import com.splunk.shuttl.archiver.thaw.BucketThawerFactory;
import com.splunk.shuttl.archiver.thaw.SplunkIndexesLayer;
import com.splunk.shuttl.archiver.usecases.util.FakeSplunkIndexesLayer;
import com.splunk.shuttl.testutil.TUtilsBucket;

public abstract class FormatRoundtripFunctionalTest {
	private ArchiveConfiguration confWithSpecificFormat;
	private BucketArchiver bucketArchiver;
	private File testDirectory;
	private LocalFileSystemPaths localFileSystemPaths;

	@BeforeMethod
	public void setUp() {
		confWithSpecificFormat = getLocalConfigurationThatArchivesFormats(asList(getFormat()));
		testDirectory = createDirectory();
		localFileSystemPaths = new LocalFileSystemPaths(
				testDirectory.getAbsolutePath());
		ArchiveFileSystem localFileSystem = ArchiveFileSystemFactory
				.getWithConfiguration(confWithSpecificFormat);

		bucketArchiver = BucketShuttlerFactory
				.createWithConfFileSystemAndLocalPaths(confWithSpecificFormat,
						localFileSystem,
						localFileSystemPaths);
	}

	protected abstract BucketFormat getFormat();

	@AfterMethod
	public void tearDown() {
		FileUtils.deleteQuietly(testDirectory);
		tearDownLocalConfig(confWithSpecificFormat);
	}

	@Test(groups = { "functional" })
	public void _givenConfigWithSomeFormat_archivesBucketWithTheFormat() {
		Bucket bucket = getBucketWithFormatArchived();

		List<Bucket> buckets = ListsBucketsFilteredFactory.create(
				confWithSpecificFormat)
				.listFilteredBucketsAtIndex(bucket.getIndex(), bucket.getEarliest(),
						bucket.getLatest());

		assertEquals(1, buckets.size());
		assertEquals(getFormat(), buckets.get(0).getFormat());
	}

	private Bucket getBucketWithFormatArchived() {
		LocalBucket bucket = TUtilsBucket.createRealBucket();
		archiveBucket(bucket, bucketArchiver);
		return bucket;
	}

	@Test(groups = { "functional" })
	public void _givenConfigWithSomeFormat_thawsBucketToSplunkBucket()
			throws IllegalIndexException {
		File thawDir = createDirectory();

		Bucket bucket = getBucketWithFormatArchived();
		SplunkIndexesLayer splunkIndexesLayer = new FakeSplunkIndexesLayer(thawDir);

		BucketThawer bucketThawer = BucketThawerFactory
				.createWithConfigAndSplunkSettingsAndLocalFileSystemPaths(
						confWithSpecificFormat,
						splunkIndexesLayer, localFileSystemPaths);

		bucketThawer.thawBuckets(bucket.getIndex(), bucket.getEarliest(),
				bucket.getLatest());

		List<LocalBucket> buckets = bucketThawer.getThawedBuckets();
		assertEquals(1, buckets.size());
		Bucket thawedBucket = buckets.get(0);
		File thawedBucketDir = new File(thawedBucket.getPath());
		assertTrue(thawedBucketDir.isDirectory());
		assertTrue(thawedBucketDir.exists());

		assertEquals(BucketFormat.SPLUNK_BUCKET, thawedBucket.getFormat());
		assertEquals(thawDir.getAbsolutePath(), thawedBucketDir.getParentFile()
				.getAbsolutePath());
		int length = thawedBucketDir.listFiles().length;
		assertTrue(2 < length);
	}
}