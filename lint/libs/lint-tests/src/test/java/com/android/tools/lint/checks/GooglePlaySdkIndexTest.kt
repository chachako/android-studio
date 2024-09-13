/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks

import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.LintFix
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GooglePlaySdkIndexTest {
  private lateinit var proto: Index
  private lateinit var index: GooglePlaySdkIndex

  @Before
  fun prepareIndex() {
    proto =
      Index.newBuilder()
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://index.example.url/")
            .setIndexAvailability(Sdk.IndexAvailability.AVAILABLE_IN_PUBLIC_SDK_INDEX)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("log4j")
                        .setArtifactId("log4j")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.2.18").setIsLatestVersion(true)
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.2.17").setIsLatestVersion(false)
                )
                // Critical (with description)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.16")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setCriticalIssueInfo(
                          LibraryVersionLabels.CriticalIssueInfo.newBuilder()
                            .setDescription("This is a custom message from sdk developer.")
                        )
                    )
                )
                // Outdated
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.15")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                    )
                )
                // Policy (deprecated label)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.14")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(LibraryVersionLabels.PolicyIssuesInfo.newBuilder())
                    )
                )
                // Critical (without description)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.2.13")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                    )
                )
            )
        )
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://another.example.url/")
            .setIndexAvailability(Sdk.IndexAvailability.AVAILABLE_IN_PUBLIC_SDK_INDEX)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("com.example.ads.third.party")
                        .setArtifactId("example")
                        .build()
                    )
                )
                // Ok, latest
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("8.0.0").setIsLatestVersion(true)
                )
                // Ok
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("7.2.2").setIsLatestVersion(false)
                )
                // Ok
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("7.2.1").setIsLatestVersion(false)
                )
                // Outdated & non compliant (user data) & Critical & Vulnerability
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.2.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                        .setOutdatedIssueInfo(
                          LibraryVersionLabels.OutdatedIssueInfo.newBuilder()
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("8.0.0")
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("7.2.1")
                                .setUpperBound("7.3.0")
                            )
                        )
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("7.2.1")
                                .setUpperBound("7.3.0")
                            )
                        )
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_TRUST_MANAGER
                            )
                        )
                    )
                )
                // Non-compliant (Ads, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_ADS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Device and Network Abuse, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.1")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_DEVICE_AND_NETWORK_ABUSE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Deceptive Behavior, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.2")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_DECEPTIVE_BEHAVIOR
                            )
                        )
                    )
                )
                // Non-compliant (User Data, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Permissions, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.4")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Mobile Unwanted Software, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.5")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                .SDK_POLICY_MOBILE_UNWANTED_SOFTWARE
                            )
                        )
                    )
                )
                // Non-compliant (Malware, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.6")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.7")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.8")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("7.1.9")
                                .setUpperBound("7.1.9")
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("7.2.1")
                                .setUpperBound("7.3.0")
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("8.0.0")
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Non-compliant (Multiple violations, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.9")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                            )
                        )
                    )
                )
                // Non-compliant (Unknown violations, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.10")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            // Use very large numbers that will probably never be used by real
                            // violation types
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            .addViolatedSdkPoliciesValue(1234567)
                            .addViolatedSdkPoliciesValue(2345678)
                        )
                    )
                )
                // Vulnerability (UNSAFE_HOSTNAME_VERIFIER, non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.11")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_HOSTNAME_VERIFIER
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (UNSAFE_SSL_ERROR_HANDLER, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.12")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_SSL_ERROR_HANDLER
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (ZIP_PATH_TRAVERSAL, no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.13")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_ZIP_PATH_TRAVERSAL
                            )
                        )
                    )
                )
                // Vulnerability (UNSAFE_WEBVIEW_OAUTH, blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.14")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_WEBVIEW_OAUTH
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (UNSAFE_CIPHER_MODE non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.15")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_CIPHER_MODE
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (UNSAFE_ENCRYPTION no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.16")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_ENCRYPTION
                            )
                        )
                    )
                )
                // Vulnerability (IMPLICIT_PENDING_INTENT blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.17")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_PENDING_INTENT
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (IMPLICIT_INTERNAL_INTENT non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.18")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_INTERNAL_INTENT
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (CROSS_APP_SCRIPTING no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.19")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_CROSS_APP_SCRIPTING
                            )
                        )
                    )
                )
                // Vulnerability (FILE_BASED_XSS blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.20")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_FILE_BASED_XSS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (INTENT_SCHEME_HIJACKING non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.21")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_INTENT_SCHEME_HIJACKING
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (JS_INTERFACE_INJECTION no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.22")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_JS_INTERFACE_INJECTION
                            )
                        )
                    )
                )
                // Vulnerability (INTENT_REDIRECTION blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.23")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_INTENT_REDIRECTION
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (FRAGMENT_INJECTION non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.24")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_FRAGMENT_INJECTION
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (PATH_TRAVERSAL no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.25")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_PATH_TRAVERSAL
                            )
                        )
                    )
                )
                // Vulnerability (SQL_INJECTION blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.26")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_SQL_INJECTION
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (LEAKED_GCP_KEYS non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.27")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_LEAKED_GCP_KEYS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability (VULNERABLE_LIBS no severity)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.28")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_VULNERABLE_LIBS
                            )
                        )
                    )
                )
                // Vulnerability multiple (VULNERABLE_LIBS blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.29")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_FILE_BASED_XSS
                            )
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_SQL_INJECTION
                            )
                            .addVulnerabilities(
                              LibraryVersionLabels.SecurityVulnerabilitiesInfo
                                .SdkSecurityVulnerabilityType
                                .SDK_SECURITY_VULNERABILITY_TYPE_VULNERABLE_LIBS
                            )
                        )
                        .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                    )
                )
                // Vulnerability not specified (non-blocking)
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("7.1.30")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setSecurityVulnerabilitiesInfo(
                          LibraryVersionLabels.SecurityVulnerabilitiesInfo.newBuilder()
                        )
                        .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                    )
                )
            )
        )
        // No URL set (causes blank result for indexUrl)
        .addSdks(
          Sdk.newBuilder()
            .setIndexAvailability(Sdk.IndexAvailability.NOT_AVAILABLE)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("no.url.group")
                        .setArtifactId("no.url.artifact")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("2.0.0").setIsLatestVersion(true)
                )
                // Policy issues
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.0.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                        )
                    )
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.0.2").setIsLatestVersion(false)
                )
                // Ok, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("1.0.1").setIsLatestVersion(false)
                )
                // Outdated
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.0.0")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setOutdatedIssueInfo(
                          LibraryVersionLabels.OutdatedIssueInfo.newBuilder()
                            // A closed range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("1.0.1")
                                .setUpperBound("1.0.2")
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("1.0.4")
                                .setUpperBound("1.0.4")
                            )
                            // An open range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("2.0.0")
                            )
                        )
                    )
                )
            )
        )
        // URL set not in SDK Index
        .addSdks(
          Sdk.newBuilder()
            .setIndexUrl("http://not.in.sdk.index.url/")
            .setIndexAvailability(Sdk.IndexAvailability.NOT_AVAILABLE)
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("not.in.sdk.index.url")
                        .setArtifactId("not.in.sdk")
                        .build()
                    )
                )
                // Ok, latest, no issues
                .addVersions(
                  LibraryVersion.newBuilder().setVersionString("3.0.4").setIsLatestVersion(true)
                )
                // Policy issues with version range
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("3.0.3")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            // A closed range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder()
                                .setLowerBound("2.0.0")
                                .setUpperBound("3.0.1")
                            )
                            // An open range
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("3.0.4")
                            )
                        )
                    )
                )
            )
        )
        // First party libraries
        .addSdks(
          Sdk.newBuilder()
            .setIsGoogleOwned(true)
            .setIndexUrl("http://google.com")
            .addLibraries(
              Library.newBuilder()
                .setLibraryId(
                  LibraryIdentifier.newBuilder()
                    .setMavenId(
                      LibraryIdentifier.MavenIdentifier.newBuilder()
                        .setGroupId("android.arch.core")
                        .setArtifactId("common")
                        .build()
                    )
                )
                .addVersions(
                  LibraryVersion.newBuilder()
                    .setVersionString("1.1.1")
                    .setIsLatestVersion(false)
                    .setVersionLabels(
                      LibraryVersionLabels.newBuilder()
                        .setOutdatedIssueInfo(
                          LibraryVersionLabels.OutdatedIssueInfo.newBuilder()
                            // Add recommended version to make sure the note is not added
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("1.1.2")
                            )
                        )
                        .setPolicyIssuesInfo(
                          LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                            .addViolatedSdkPolicies(
                              LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
                            )
                            .addRecommendedVersions(
                              LibraryVersionRange.newBuilder().setLowerBound("1.1.3")
                            )
                        )
                    )
                )
            )
        )
        .build()
    index =
      object : GooglePlaySdkIndex() {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
          ReadUrlDataResult(null, true).also { fail("Trying to read proto from the network!") }

        override fun readDefaultData(relative: String): InputStream? {
          fail("Trying to read default proto!")
          return null
        }

        override fun error(throwable: Throwable, message: String?) {}
      }
    index.initialize(ByteArrayInputStream(proto.toByteArray()))
    assertThat(index.getLastReadSource()).isEqualTo(NetworkCache.DataSourceType.TEST_DATA)
  }

  @Test
  fun `outdated issues shown`() {
    assertThat(countOutdatedIssues()).isEqualTo(4)
  }

  @Test
  fun `critical issues shown`() {
    assertThat(countCriticalIssues()).isEqualTo(3)
  }

  @Test
  fun `policy issues shown`() {
    assertThat(countPolicyIssues()).isEqualTo(21)
  }

  @Test
  fun `vulnerability issues shown`() {
    assertThat(countVulnerabilityIssues()).isEqualTo(23)
  }

  @Test
  fun `errors and warnings shown correctly`() {
    assertThat(countHasErrorOrWarning()).isEqualTo(25)
  }

  @Test
  fun `links are present when indexUrl is not blank and is available in SDK Index`() {
    for (sdk in proto.sdksList) {
      val isFromIndex = sdk.indexAvailability != Sdk.IndexAvailability.NOT_AVAILABLE
      val expectedUrl = if (isFromIndex) sdk.indexUrl else null
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        val lintLink =
          index.generateSdkLinkLintFix(
            group,
            artifact,
            versionString = "noVersion",
            buildFile = null,
          )
        if (expectedUrl.isNullOrBlank()) {
          assertThat(lintLink).isNull()
        } else {
          assertThat(lintLink).isNotNull()
          assertThat(lintLink).isInstanceOf(LintFix.ShowUrl::class.java)
          assertThat((lintLink as LintFix.ShowUrl).url).isEqualTo(expectedUrl)
        }
      }
    }
  }

  @Test
  fun `offline snapshot can be used correctly`() {
    val offlineIndex =
      object : GooglePlaySdkIndex() {
        override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
          ReadUrlDataResult(null, true)

        override fun error(throwable: Throwable, message: String?) {}
      }
    offlineIndex.initialize(null)
    assertThat(offlineIndex.isReady()).isTrue()
    assertThat(offlineIndex.getLastReadSource()).isEqualTo(NetworkCache.DataSourceType.DEFAULT_DATA)
  }

  @Test
  fun `No violation type policy issue message`() {
    assertThat(index.generatePolicyMessages("logj4", "logj4", "1.2.14"))
      .isEqualTo(
        listOf(
          "logj4:logj4 version 1.2.14 has policy issues that will block publishing of your app to Play Console in the future"
        )
      )
  }

  @Test
  fun `policy with other issues message`() {
    verifyPolicyMessages(
      "7.2.0",
      listOf("User Data policy"),
      recommendedVersions =
        ".\nThe library author recommends using versions:\n" +
          "  - From 7.2.1 to 7.3.0\n" +
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. " +
          "Carefully evaluate any third-party SDKs before integrating them into your app.",
    )
  }

  @Test
  fun `Ads policy issue message`() {
    verifyPolicyMessages("7.1.0", listOf("Ads policy"))
  }

  @Test
  fun `Device and Network Abuse policy issue message`() {
    verifyPolicyMessages("7.1.1", listOf("Device and Network Abuse policy"))
  }

  @Test
  fun `Deceptive Behavior policy issue message`() {
    verifyPolicyMessages("7.1.2", listOf("Deceptive Behavior policy"))
  }

  @Test
  fun `User Data policy issue message`() {
    verifyPolicyMessages("7.1.3", listOf("User Data policy"))
  }

  @Test
  fun `Permissions policy issue message`() {
    verifyPolicyMessages("7.1.4", listOf("Permissions policy"))
  }

  @Test
  fun `Mobile Unwanted Software policy issue message`() {
    verifyPolicyMessages("7.1.5", listOf("Mobile Unwanted Software policy"))
  }

  @Test
  fun `Malware policy issue message`() {
    verifyPolicyMessages("7.1.6", listOf("Malware policy"))
  }

  @Test
  fun `multiple policy types issue message`() {
    verifyPolicyMessages(
      "7.1.7",
      listOf("User Data policy", "Malware policy", "Permissions policy"),
    )
  }

  @Test
  fun `multiple policy types issue message with recommended versions`() {
    index.showRecommendedVersions = true
    verifyPolicyMessages(
      "7.1.8",
      listOf("User Data policy", "Malware policy"),
      recommendedVersions =
        ".\nThe library author recommends using versions:\n" +
          "  - 7.1.9\n" +
          "  - From 7.2.1 to 7.3.0\n" +
          "  - 8.0.0 or higher\n" +
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. " +
          "Carefully evaluate any third-party SDKs before integrating them into your app.",
    )
  }

  @Test
  fun `multiple policy types issue message with recommended versions but flag not set`() {
    index.showRecommendedVersions = false
    verifyPolicyMessages(
      "7.1.8",
      listOf("User Data policy", "Malware policy"),
      recommendedVersions = "",
    )
  }

  @Test
  fun `unknown policy type issue message`() {
    verifyPolicyMessages("7.1.10", listOf("Permissions policy", "policy"))
  }

  @Test
  fun `no quickfix for null indexUrl`() {
    val lintLink =
      index.generateSdkLinkLintFix(
        "not.existing.group",
        "not.existing.artifact",
        versionString = "noVersion",
        buildFile = null,
      )
    assertThat(lintLink).isNull()
  }

  @Test
  fun `There is a note if description is present in blocking critical`() {
    index.showNotesFromDeveloper = true
    val expectedMessage =
      "**[Prevents app release in Google Play Console]** log4j:log4j version 1.2.16 has been reported as problematic by its author and will block publishing of your app to Play Console.\n**Note:** This is a custom message from sdk developer."
    assertThat(index.generateBlockingCriticalMessage("log4j", "log4j", "1.2.16"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `There is a note if description is present in non blocking critical`() {
    index.showNotesFromDeveloper = true
    val expectedMessage =
      "log4j:log4j version 1.2.16 has an associated message from its author.\n**Note:** This is a custom message from sdk developer."
    assertThat(index.generateCriticalMessage("log4j", "log4j", "1.2.16")).isEqualTo(expectedMessage)
  }

  @Test
  fun `There is not a note if description is present in non blocking critical but flag is false`() {
    index.showNotesFromDeveloper = false
    val expectedMessage = "log4j:log4j version 1.2.16 has an associated message from its author"
    assertThat(index.generateCriticalMessage("log4j", "log4j", "1.2.16")).isEqualTo(expectedMessage)
  }

  @Test
  fun `There is not a note if description is present in blocking critical but flag is false`() {
    index.showNotesFromDeveloper = false
    val expectedMessage =
      "**[Prevents app release in Google Play Console]** log4j:log4j version 1.2.16 has been reported as problematic by its author and will block publishing of your app to Play Console"
    assertThat(index.generateBlockingCriticalMessage("log4j", "log4j", "1.2.16"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `Note not present if description is not present in blocking critical`() {
    index.showNotesFromDeveloper = true
    val expectedMessage =
      "**[Prevents app release in Google Play Console]** log4j:log4j version 1.2.13 has been reported as problematic by its author and will block publishing of your app to Play Console"
    assertThat(index.generateBlockingCriticalMessage("log4j", "log4j", "1.2.13"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `Note not present if description is not present in non blocking critical`() {
    index.showNotesFromDeveloper = true
    val expectedMessage = "log4j:log4j version 1.2.13 has an associated message from its author"
    assertThat(index.generateCriticalMessage("log4j", "log4j", "1.2.13")).isEqualTo(expectedMessage)
  }

  @Test
  fun `Outdated issue with recommended versions`() {
    index.showRecommendedVersions = true
    val expectedMessage =
      "no.url.group:no.url.artifact version 1.0.0 has been reported as outdated by its author.\n" +
        "The library author recommends using versions:\n" +
        "  - From 1.0.1 to 1.0.2\n" +
        "  - 1.0.4\n" +
        "  - 2.0.0 or higher\n" +
        "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. " +
        "Carefully evaluate any third-party SDKs before integrating them into your app."
    assertThat(index.generateOutdatedMessage("no.url.group", "no.url.artifact", "1.0.0"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `Outdated issue with recommended versions but flag not set`() {
    index.showRecommendedVersions = false
    val expectedMessage =
      "no.url.group:no.url.artifact version 1.0.0 has been reported as outdated by its author"
    assertThat(index.generateOutdatedMessage("no.url.group", "no.url.artifact", "1.0.0"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `Outdated issue with recommended versions for first party`() {
    val expectedMessage =
      "android.arch.core:common version 1.1.1 has been reported as outdated by its author.\n" +
        "The library author recommends using versions:\n" +
        "  - 1.1.2 or higher\n"
    assertThat(index.generateOutdatedMessage("android.arch.core", "common", "1.1.1"))
      .isEqualTo(expectedMessage)
  }

  @Test
  fun `Policy with recommended versions first party`() {
    val expectedMessages =
      listOf(
        "android.arch.core:common version 1.1.1 has Permissions policy issues that will block publishing of your app to Play Console in the future.\n" +
          "The library author recommends using versions:\n" +
          "  - 1.1.3 or higher\n"
      )
    assertThat(index.generatePolicyMessages("android.arch.core", "common", "1.1.1"))
      .isEqualTo(expectedMessages)
  }

  @Test
  fun `No vulnerability type policy issue message`() {
    assertThat(
        index.generateVulnerabilityMessages("logj4", "logj4", "1.2.14").map { it.description }
      )
      .isEqualTo(listOf("logj4:logj4 version 1.2.14 has unspecified vulnerability issues."))
  }

  @Test
  fun `vulnerability with other issues message`() {
    verifyVulnerabilityMessages(
      "7.2.0",
      listOf("contains an unsafe implementation of the X509TrustManager interface"),
    )
  }

  @Test
  fun `UNSAFE_HOSTNAME_VERIFIER vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.11",
      listOf(
        "contains an unsafe implementation of the interfaces HostnameVerifier or X509HostnameVerifier"
      ),
    )
  }

  @Test
  fun `SSL_ERROR_HANDLER vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.12",
      listOf("contains an unsafe implementation of the onReceivedSslError handler"),
    )
  }

  @Test
  fun `ZIP_PATH_TRAVERSAL vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.13", listOf("contains unsafe unzipping patterns"))
  }

  @Test
  fun `UNSAFE_WEBVIEW_OAUTH vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.14",
      listOf("uses WebView for authentication, which is not recommended"),
    )
  }

  @Test
  fun `UNSAFE_CIPHER_MODE vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.15",
      listOf("contains encryption employing the less secure mode AES/ECB"),
    )
  }

  @Test
  fun `UNSAFE_ENCRYPTION vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.16", listOf("contains unsafe encryption patterns"))
  }

  @Test
  fun `IMPLICIT_PENDING_INTENT vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.17", listOf("contains an Implicit PendingIntent issue"))
  }

  @Test
  fun `IMPLICIT_INTERNAL_INTENT vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.18", listOf("contains an Implicit Internal Intent issue"))
  }

  @Test
  fun `CROSS_APP_SCRIPTING vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.19",
      listOf("may be vulnerable to WebView Cross-App Scripting"),
    )
  }

  @Test
  fun `FILE_BASED_XSS vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.20",
      listOf("may be vulnerable to File-based Cross-Site Scripting"),
    )
  }

  @Test
  fun `INTENT_SCHEME_HIJACKING vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.21", listOf("may be vulnerable to Intent-Scheme Hijacking"))
  }

  @Test
  fun `JS_INTERFACE_INJECTION vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.22",
      listOf("may be vulnerable to JavaScript Interface Injection"),
    )
  }

  @Test
  fun `INTENT_REDIRECTION vulnerability issue message`() {
    verifyVulnerabilityMessages("7.1.23", listOf("may be vulnerable to Intent Redirection"))
  }

  @Test
  fun `FRAGMENT_INJECTION vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.24",
      listOf(
        "contains an unsafe PreferenceActivity implementation that may be vulnerable to Fragment Injection"
      ),
    )
  }

  @Test
  fun `PATH_TRAVERSAL vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.25",
      listOf("may be vulnerable to ContentProvider Path Traversal"),
    )
  }

  @Test
  fun `SQL_INJECTION vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.26",
      listOf("may be vulnerable to ContentProvider SQL Injection"),
    )
  }

  @Test
  fun `LEAKED_GCP_KEYS vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.27",
      listOf("contains exposed Google Cloud Platform (GCP) API key(s)"),
    )
  }

  @Test
  fun `VULNERABLE_LIBS vulnerability issue message`() {
    verifyVulnerabilityMessages(
      "7.1.28",
      listOf("contains one or more JavaScript libraries with known security issues"),
    )
  }

  @Test
  fun `multiple vulnerabilities issue message`() {
    verifyVulnerabilityMessages(
      "7.1.29",
      listOf(
        "may be vulnerable to File-based Cross-Site Scripting",
        "may be vulnerable to ContentProvider SQL Injection",
        "contains one or more JavaScript libraries with known security issues",
      ),
    )
  }

  @Test
  fun `vulnerability not specified issue message`() {
    verifyVulnerabilityMessages("7.1.30", listOf("has unspecified vulnerability issues"))
  }

  @Test
  fun `No recommended versions generates empty list`() {
    val recommendedVersions =
      index.recommendedVersions("com.example.ads.third.party", "example", "8.0.0")
    assertThat(recommendedVersions).isNotNull()
    assertThat(recommendedVersions).isEmpty()
  }

  @Test
  fun `Recommended versions generated from all types without repeated ranges`() {
    val expectedVersions = listOf("7.2.1 to 7.3.0", "8.0.0 to <null>")
    val recommendedVersions =
      index.recommendedVersions("com.example.ads.third.party", "example", "7.2.0")
    assertThat(recommendedVersions).isNotNull()
    val asText =
      recommendedVersions.map {
        "${it.lowerBound} to ${if (it.upperBound.isNullOrBlank()) "<null>" else it.upperBound}"
      }
    assertThat(asText).containsAllIn(expectedVersions)
  }

  private fun countOutdatedIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.isLibraryOutdated(group, artifact, version.versionString, null)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun countPolicyIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.isLibraryNonCompliant(group, artifact, version.versionString, null)) {
            result += index.generatePolicyMessages(group, artifact, version.versionString).size
          }
        }
      }
    }
    return result
  }

  private fun countCriticalIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.hasLibraryCriticalIssues(group, artifact, version.versionString, null)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun countVulnerabilityIssues(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.hasLibraryVulnerabilityIssues(group, artifact, version.versionString, null)) {
            result +=
              index.generateVulnerabilityMessages(group, artifact, version.versionString).size
          }
        }
      }
    }
    return result
  }

  private fun countHasErrorOrWarning(): Int {
    var result = 0
    for (sdk in proto.sdksList) {
      for (library in sdk.librariesList) {
        val group = library.libraryId.mavenId.groupId
        val artifact = library.libraryId.mavenId.artifactId
        for (version in library.versionsList) {
          if (index.hasLibraryErrorOrWarning(group, artifact, version.versionString)) {
            result++
          }
        }
      }
    }
    return result
  }

  private fun verifyPolicyMessages(
    version: String,
    policyTypes: List<String>,
    recommendedVersions: String = "",
  ) {
    val expectedBlockingMessages =
      policyTypes.map { policyType ->
        "**[Prevents app release in Google Play Console]** com.example.ads.third.party:example version $version has $policyType issues that will block publishing of your app to Play Console$recommendedVersions"
      }
    assertThat(
        index.generateBlockingPolicyMessages("com.example.ads.third.party", "example", version)
      )
      .isEqualTo(expectedBlockingMessages)
    val expectedNonBlockingMessages =
      policyTypes.map { policyType ->
        "com.example.ads.third.party:example version $version has $policyType issues that will block publishing of your app to Play Console in the future$recommendedVersions"
      }
    assertThat(index.generatePolicyMessages("com.example.ads.third.party", "example", version))
      .isEqualTo(expectedNonBlockingMessages)
  }

  private fun verifyVulnerabilityMessages(
    version: String,
    vulnerabilityDescriptions: List<String>,
  ) {
    val expectedNonBlockingMessages =
      vulnerabilityDescriptions.map { description ->
        "com.example.ads.third.party:example version $version $description."
      }
    assertThat(
        index.generateVulnerabilityMessages("com.example.ads.third.party", "example", version).map {
          it.description
        }
      )
      .isEqualTo(expectedNonBlockingMessages)
  }
}
