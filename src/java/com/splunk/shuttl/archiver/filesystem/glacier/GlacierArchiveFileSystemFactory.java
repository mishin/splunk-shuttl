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
package com.splunk.shuttl.archiver.filesystem.glacier;

import java.net.URI;

import org.apache.log4j.Logger;

import com.splunk.shuttl.archiver.LocalFileSystemPaths;
import com.splunk.shuttl.archiver.archive.BucketDeleter;
import com.splunk.shuttl.archiver.filesystem.ArchiveFileSystemFactory;
import com.splunk.shuttl.archiver.filesystem.hadoop.HadoopArchiveFileSystem;
import com.splunk.shuttl.archiver.importexport.tgz.CreatesBucketTgz;
import com.splunk.shuttl.archiver.importexport.tgz.TgzFormatExporter;
import com.splunk.shuttl.archiver.util.GroupRegex;
import com.splunk.shuttl.archiver.util.IllegalRegexGroupException;

/**
 * Construction logic for creating {@link GlacierArchiveFileSystem}
 */
public class GlacierArchiveFileSystemFactory {

	/**
	 * @param uri
	 *          - Has the format of glacier://ID:SECRET@ENDPOINT:BUCKET/VAULT
	 * 
	 * @throws UnsupportedGlacierUriException
	 *           - if the format is not valid.
	 */
	public static GlacierArchiveFileSystem create(URI uri,
			LocalFileSystemPaths localFileSystemPaths) {
		AWSCredentialsImpl credentials = getCredentials(uri);
		GlacierClient glacierClient = GlacierClient.create(credentials);

		URI s3n = URI.create("s3n://" + credentials.getAWSAccessKeyId() + ":"
				+ credentials.getAWSSecretKey() + "@" + credentials.getBucket() + "/"
				+ credentials.getVault());
		HadoopArchiveFileSystem hadoop = (HadoopArchiveFileSystem) ArchiveFileSystemFactory
				.getWithUriAndLocalFileSystemPaths(s3n, localFileSystemPaths);
		TgzFormatExporter tgzFormatExporter = TgzFormatExporter
				.create(CreatesBucketTgz.create(localFileSystemPaths.getTgzDirectory()));
		Logger logger = Logger.getLogger(GlacierArchiveFileSystem.class);
		BucketDeleter bucketDeleter = BucketDeleter.create();

		return new GlacierArchiveFileSystem(hadoop, glacierClient,
				tgzFormatExporter, logger, bucketDeleter);
	}

	/**
	 * @param validUri
	 * @return
	 */
	public static AWSCredentialsImpl getCredentials(URI uri) {
		CredentialsParser parser = new CredentialsParser(uri).parse();
		return new AWSCredentialsImpl(parser.id, parser.secret, parser.endpoint,
				parser.bucket, parser.vault);
	}

	private static class CredentialsParser {

		public static final String LEGAL_URI_REGEX = "glacier://(.+?):(.+?)@(.+?):(.+?)/(.+)";

		private final URI uri;
		public String id = "";
		public String secret = "";
		public String endpoint = "";
		public String bucket = "";
		public String vault = "";

		public CredentialsParser(URI uri) {
			this.uri = uri;
		}

		public CredentialsParser parse() {
			try {
				return doParse();
			} catch (IllegalRegexGroupException e) {
				throw new InvalidGlacierUriException(uri);
			}
		}

		private CredentialsParser doParse() {
			GroupRegex regex = new GroupRegex(LEGAL_URI_REGEX, uri.toString());
			id = regex.getValue(1);
			secret = regex.getValue(2);
			endpoint = regex.getValue(3);
			bucket = regex.getValue(4);
			vault = regex.getValue(5);
			return this;
		}

	}

}