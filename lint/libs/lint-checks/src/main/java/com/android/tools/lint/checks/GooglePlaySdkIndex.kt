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

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.LintFix
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import org.jetbrains.annotations.VisibleForTesting

/** Provides information about libraries from the Google Play SDK Index. */
abstract class GooglePlaySdkIndex(cacheDir: Path? = null) :
  NetworkCache(
    GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL,
    GOOGLE_PLAY_SDK_INDEX_KEY,
    cacheDir,
    cacheExpiryHours = TimeUnit.DAYS.toHours(GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS).toInt(),
  ) {
  companion object {
    const val SDK_INDEX_SNAPSHOT_TEST_BASE_URL_ENV_VAR = "SDK_INDEX_TEST_BASE_URL"
    private const val DEFAULT_SDK_INDEX_SNAPSHOT_BASE_URL = "https://dl.google.com/play-sdk/index/"
    const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE = "snapshot.gz"
    const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE = "sdk-index-offline-snapshot.proto.gz"
    val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL =
      System.getenv(SDK_INDEX_SNAPSHOT_TEST_BASE_URL_ENV_VAR) ?: DEFAULT_SDK_INDEX_SNAPSHOT_BASE_URL
    const val GOOGLE_PLAY_SDK_INDEX_KEY = "sdk_index"
    const val GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS = 7L
    const val GOOGLE_PLAY_SDK_INDEX_URL = "https://developer.android.com/distribute/sdk-index"
    const val VIEW_DETAILS_MESSAGE = "View details in Google Play SDK Index"
    val POLICY_TYPE_TO_TEXT =
      mapOf(
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_UNKNOWN to "unknown",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_ADS to "Ads",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_DEVICE_AND_NETWORK_ABUSE to
          "Device and Network Abuse",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_DECEPTIVE_BEHAVIOR to
          "Deceptive Behavior",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA to "User Data",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS to "Permissions",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MOBILE_UNWANTED_SOFTWARE to
          "Mobile Unwanted Software",
        LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE to "Malware",
      )
  }

  private lateinit var lastReadResult: ReadDataResult
  private var initialized: Boolean = false
  private var status: GooglePlaySdkIndexStatus = GooglePlaySdkIndexStatus.NOT_READY
  private val libraryToSdk = HashMap<String, LibraryToSdk>()
  private var googleMaven: GoogleMavenRepository? = null

  /**
   * Read Index snapshot (locally if it is not old and remotely if old and network is available) and
   * store results in maps for later consumption.
   */
  fun initialize(googleMaven: GoogleMavenRepository?) {
    initialize(null, googleMaven)
  }

  private fun readIndexData(readFunction: () -> InputStream?): ReadDataResult {
    var readDataErrorType = ReadDataErrorType.DATA_FUNCTION_EXCEPTION
    try {
      val rawData = readFunction.invoke()
      if (rawData != null) {
        readDataErrorType = ReadDataErrorType.GZIP_EXCEPTION
        val gzipData = GZIPInputStream(rawData)
        readDataErrorType = ReadDataErrorType.INDEX_PARSE_EXCEPTION
        val index = Index.parseFrom(gzipData)
        readDataErrorType =
          if (index != null) ReadDataErrorType.NO_ERROR
          else ReadDataErrorType.INDEX_PARSE_NULL_ERROR
        return ReadDataResult(index, readDataErrorType, exception = null)
      }
    } catch (exception: Exception) {
      return ReadDataResult(index = null, readDataErrorType, exception)
    }
    return ReadDataResult(
      index = null,
      readDataErrorType = ReadDataErrorType.DATA_FUNCTION_NULL_ERROR,
      exception = null,
    )
  }

  @VisibleForTesting
  fun initialize(overriddenData: InputStream? = null, googleMaven: GoogleMavenRepository?) {
    synchronized(this) {
      if (initialized) {
        return
      }
      initialized = true
      this.googleMaven = googleMaven
      status = GooglePlaySdkIndexStatus.NOT_READY
    }
    var index: Index? = null
    val indexDataSource: DataSourceType
    // Read from cache/network
    if (overriddenData != null) {
      // Do not check for exceptions, calling from a test
      lastReadSourceType = DataSourceType.TEST_DATA
      indexDataSource = lastReadSourceType
      index = Index.parseFrom(overriddenData)
    } else {
      lastReadResult = readIndexData { findData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE) }
      if (lastReadResult.index != null) {
        // Using data from cache
        indexDataSource = lastReadSourceType
        index = lastReadResult.index
      } else {
        if (lastReadSourceType != DataSourceType.DEFAULT_DATA) {
          lastReadSourceType = DataSourceType.UNKNOWN_SOURCE
          val offlineResult = readIndexData {
            readDefaultData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE)
          }
          if (offlineResult.index != null) {
            indexDataSource = DataSourceType.DEFAULT_DATA
            index = offlineResult.index
          } else {
            indexDataSource = DataSourceType.UNKNOWN_SOURCE
            logErrorInDefaultData(offlineResult)
          }
        } else {
          indexDataSource = DataSourceType.UNKNOWN_SOURCE
          logErrorInDefaultData(lastReadResult)
        }
      }
    }
    if (index != null) {
      setMaps(index)
      status = GooglePlaySdkIndexStatus.READY
      logIndexLoadedCorrectly(indexDataSource)
    }
  }

  /** Tells if the Index is ready to be used (loaded and maps generated) */
  fun isReady() = status == GooglePlaySdkIndexStatus.READY

  /**
   * Does this library have policy issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has compliant
   *   issues.
   */
  fun isLibraryNonCompliant(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val isNonCompliant =
      getLabels(groupId, artifactId, versionString)?.hasPolicyIssuesInfo() ?: false
    if (isNonCompliant) {
      logNonCompliant(groupId, artifactId, versionString, buildFile)
    }
    return isNonCompliant
  }

  /**
   * Is this library marked as outdated?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has been marked
   *   as outdated.
   */
  fun isLibraryOutdated(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val isOutdated = getLabels(groupId, artifactId, versionString)?.hasOutdatedIssueInfo() ?: false
    if (isOutdated) {
      logOutdated(groupId, artifactId, versionString, buildFile)
    }
    return isOutdated
  }

  /**
   * Does this library have critical issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has critical
   *   issues reported by its authors.
   */
  fun hasLibraryCriticalIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val hasCriticalIssues =
      getLabels(groupId, artifactId, versionString)?.hasCriticalIssueInfo() ?: false
    if (hasCriticalIssues) {
      logHasCriticalIssues(groupId, artifactId, versionString, buildFile)
    }
    return hasCriticalIssues
  }

  /**
   * Does this library have blocking issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @return true if the index has information about this particular version, and it has been
   *   labeled with blocking severity.
   */
  fun hasLibraryBlockingIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Boolean {
    val labels = getLabels(groupId, artifactId, versionString) ?: return false
    val severity = labels.severity
    return severity == LibraryVersionLabels.Severity.BLOCKING_SEVERITY
  }

  /**
   * Does this library have any issues that would result in an error or warning?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @return true if the index has information about this particular version, and it has issues that
   *   will cause an error or warning. (Any blocking issue is an error, non blocking outdated or
   *   policy issues are warnings)
   */
  fun hasLibraryErrorOrWarning(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Boolean {
    val labels = getLabels(groupId, artifactId, versionString) ?: return false
    return labels.severity == LibraryVersionLabels.Severity.BLOCKING_SEVERITY ||
      labels.hasOutdatedIssueInfo() ||
      labels.hasPolicyIssuesInfo()
  }

  /**
   * Get URL for the SDK associated to this library
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @return the associated URL or null if there is none or flag for links to SDK is not enabled
   */
  fun getSdkUrl(groupId: String, artifactId: String): String? {
    if (!isReady()) {
      return null
    }
    val sdk = getSdk(groupId, artifactId) ?: return null
    if (sdk.sdk.indexAvailability == Sdk.IndexAvailability.NOT_AVAILABLE) return null
    return sdk.sdk.indexUrl
  }

  private fun getLabels(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): LibraryVersionLabels? {
    if (!isReady()) {
      return null
    }
    val libraryVersion = getLibraryVersion(groupId, artifactId, versionString) ?: return null
    return libraryVersion.versionLabels
  }

  private fun getLibraryVersion(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): LibraryVersion? {
    val coordinate = createCoordinateString(groupId, artifactId)
    val sdk = libraryToSdk[coordinate] ?: return null
    return sdk.getVersion(versionString)
  }

  private fun getSdk(groupId: String, artifactId: String): LibraryToSdk? {
    val coordinate = createCoordinateString(groupId, artifactId)
    return libraryToSdk[coordinate]
  }

  private fun setMaps(index: Index) {
    libraryToSdk.clear()
    val sdkList = index.sdksList
    for (sdk in sdkList) {
      for (library in sdk.librariesList) {
        val coordinate =
          createCoordinateString(
            library.libraryId.mavenId.groupId,
            library.libraryId.mavenId.artifactId,
          )
        val currentLibrary = LibraryToSdk(coordinate, sdk)
        for (version in library.versionsList) {
          currentLibrary.addLibraryVersion(version.versionString, version)
        }
        libraryToSdk[currentLibrary.libraryId] = currentLibrary
      }
    }
  }

  private fun createCoordinateString(groupId: String, artifactId: String) = "$groupId:$artifactId"

  private enum class GooglePlaySdkIndexStatus {
    NOT_READY,
    READY,
  }

  private class LibraryToSdk(val libraryId: String, val sdk: Sdk) {
    private val versionToLibraryVersion = HashMap<String, LibraryVersion>()
    private var latestVersion: String? = null

    fun addLibraryVersion(versionString: String, libraryVersion: LibraryVersion) {
      versionToLibraryVersion[versionString] = libraryVersion
      if (libraryVersion.isLatestVersion) {
        latestVersion = versionString
      }
    }

    fun getVersion(versionString: String): LibraryVersion? {
      return versionToLibraryVersion[versionString]
    }
  }

  override fun readDefaultData(relative: String): InputStream? {
    // This function is called only if there were errors while reading the cached data, log that
    // error if the previous source was known
    if (lastReadSourceType != DataSourceType.UNKNOWN_SOURCE) {
      // Log reason for not being able to use cached file
      logCachingError(lastReadResult, lastReadSourceType)
    }
    // We only have a single file, return it no matter what relative string is used
    return GooglePlaySdkIndex::class
      .java
      .getResourceAsStream("/$GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE")
  }

  /**
   * Generates a LintFix that opens a browser to the given library.
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version of the library (only used for logging)
   * @param buildFile: build file where this library is being used
   * @return a link to the SDK url this library belongs to if the index has information about it
   */
  open fun generateSdkLinkLintFix(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): LintFix? {
    val url = getSdkUrl(groupId, artifactId)
    return if (url.isNullOrBlank()) null else LintFix.ShowUrl(VIEW_DETAILS_MESSAGE, null, url)
  }

  /** Generate a message for a library that has blocking policy issues */
  fun generateBlockingPolicyMessages(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): List<String> {
    val recommendedVersions = getPolicyRecommendedVersions(groupId, artifactId, versionString)
    return getPolicyLabels(getLabels(groupId, artifactId, versionString)).map { label ->
      "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has $label issues that will block publishing of your app to Play Console$recommendedVersions"
    }
  }

  /** Generate a message for a library that has policy issues */
  fun generatePolicyMessages(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): List<String> {
    val recommendedVersions = getPolicyRecommendedVersions(groupId, artifactId, versionString)
    return getPolicyLabels(getLabels(groupId, artifactId, versionString)).map { label ->
      "$groupId:$artifactId version $versionString has $label issues that will block publishing of your app to Play Console in the future$recommendedVersions"
    }
  }

  /** Generate a message for a library that has blocking critical issues */
  fun generateBlockingCriticalMessage(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val note = getNoteFromDeveloper(groupId, artifactId, versionString)
    return "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has been reported as problematic by its author and will block publishing of your app to Play Console$note"
  }

  /** Generate a message for a library that has non-blocking critical issues */
  fun generateCriticalMessage(groupId: String, artifactId: String, versionString: String): String {
    val note = getNoteFromDeveloper(groupId, artifactId, versionString)
    return "$groupId:$artifactId version $versionString has an associated message from its author$note"
  }

  /** Generate a message for a library that has blocking outdated issues */
  fun generateBlockingOutdatedMessage(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val recommendedVersions = getOutdatedRecommendedVersions(groupId, artifactId, versionString)
    return "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has been reported as outdated by its author and will block publishing of your app to Play Console$recommendedVersions"
  }

  /** Generate a message for a library that has non-blocking outdated issues */
  fun generateOutdatedMessage(groupId: String, artifactId: String, versionString: String): String {
    val recommendedVersions = getOutdatedRecommendedVersions(groupId, artifactId, versionString)
    return "$groupId:$artifactId version $versionString has been reported as outdated by its author$recommendedVersions"
  }

  /** Generate a message for a library that has blocking issues */
  fun generateBlockingGenericIssueMessage(
    groupId: String,
    artifactId: String,
    versionString: String,
  ) =
    "$groupId:$artifactId version $versionString has one or more issues that will block publishing of your app to Play Console"

  /** Generate a message for a library that has non-blocking issues */
  fun generateGenericIssueMessage(groupId: String, artifactId: String, versionString: String) =
    "$groupId:$artifactId version $versionString has one or more issues that could block publishing of your app to Play Console in the future"

  /**
   * Generate a list of versions that the library owner has recommended to use instead of the passed
   * version.
   */
  fun recommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Collection<LibraryVersionRange> {
    val recommendations = LinkedHashSet<LibraryVersionRange>()
    val labels = getLabels(groupId, artifactId, versionString)
    if (labels != null) {
      labels.policyIssuesInfo.recommendedVersionsList?.filterNotNull()?.forEach {
        recommendations.add(it)
      }
      labels.outdatedIssueInfo.recommendedVersionsList?.filterNotNull()?.forEach {
        recommendations.add(it)
      }
    }
    return recommendations
  }

  protected open fun logHasCriticalIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logNonCompliant(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logOutdated(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logCachingError(readResult: ReadDataResult, dataSourceType: DataSourceType) {}

  protected open fun logErrorInDefaultData(readResult: ReadDataResult) {}

  protected open fun logIndexLoadedCorrectly(dataSourceType: DataSourceType) {}

  protected enum class ReadDataErrorType {
    NO_ERROR,
    // Function used to get the data caused an exception
    DATA_FUNCTION_EXCEPTION,
    // Function used to get the data returned null
    DATA_FUNCTION_NULL_ERROR,
    // There was an exception while decompressing the raw data
    GZIP_EXCEPTION,
    // Exception while parsing decompressed data
    INDEX_PARSE_EXCEPTION,
    // Resulted Index was null after parsing
    INDEX_PARSE_NULL_ERROR,
  }

  protected class ReadDataResult(
    val index: Index?,
    val readDataErrorType: ReadDataErrorType,
    val exception: Exception?,
  )

  @VisibleForTesting fun getLastReadSource() = lastReadSourceType

  private fun getPolicyLabels(labels: LibraryVersionLabels?): List<String> {
    val defaultLabel = "policy"
    val policyViolations = extractPolicyViolations(labels)
    val result = mutableListOf<String>()
    var hasUnknown = false
    for (violation in policyViolations) {
      if (POLICY_TYPE_TO_TEXT.containsKey(violation)) {
        result.add("${POLICY_TYPE_TO_TEXT[violation]} policy")
      } else {
        hasUnknown = true
      }
    }
    if (hasUnknown || result.isEmpty()) {
      result.add(defaultLabel)
    }
    return result
  }

  private fun extractPolicyViolations(
    labels: LibraryVersionLabels?
  ): Set<LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy> {
    val result = mutableSetOf<LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy>()
    if (labels == null || !labels.hasPolicyIssuesInfo()) {
      return result
    }
    val types = labels.policyIssuesInfo.violatedSdkPoliciesList
    if (types != null) {
      result.addAll(types)
    }
    return result
  }

  private fun getNoteFromDeveloper(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val criticalIssue = labels.criticalIssueInfo ?: return ""
    val message = criticalIssue.description
    if (message.isNullOrBlank()) return ""
    return ".\n**Note:** $message"
  }

  private fun getOutdatedRecommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val outdatedIssue = labels.outdatedIssueInfo ?: return ""
    return generateRecommendedList(
      outdatedIssue.recommendedVersionsList,
      isThirdPartyLibrary(groupId),
    )
  }

  private fun getPolicyRecommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val policyIssue = labels.policyIssuesInfo ?: return ""
    return generateRecommendedList(
      policyIssue.recommendedVersionsList,
      isThirdPartyLibrary(groupId),
    )
  }

  private fun isThirdPartyLibrary(groupId: String): Boolean {
    // TODO(b/339237338): Currently looking into Google Maven repository but this information could
    // be included in future snapshots
    return if (googleMaven == null) {
      // Google Maven is not defined, assume everything is 3rd party
      true
    } else {
      !googleMaven!!.hasGroupId(groupId)
    }
  }

  private fun generateRecommendedList(
    listOfVersions: List<LibraryVersionRange?>?,
    isThirdParty: Boolean,
  ): String {
    val ranges =
      (listOfVersions ?: return "").filterNotNull().joinToString("\n") { range ->
        if (range.upperBound.isNullOrBlank()) {
          "  - ${range.lowerBound} or higher"
        } else if (range.upperBound != range.lowerBound) {
          "  - From ${range.lowerBound} to ${range.upperBound}"
        } else {
          "  - ${range.lowerBound}"
        }
      }
    if (ranges.isEmpty()) return ""
    return ".\nThe library author recommends using versions:\n$ranges\n${
      if (isThirdParty) "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app."
      else ""
    }"
  }
}
