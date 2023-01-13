/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("Lint")

package com.android.tools.lint.checks

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.lint.client.api.LintTomlDocument
import com.android.tools.lint.client.api.LintTomlLiteralValue
import com.android.tools.lint.client.api.LintTomlMapValue
import com.android.tools.lint.client.api.LintTomlValue
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.google.common.base.CaseFormat
import org.jetbrains.annotations.VisibleForTesting
import java.util.TreeSet

// Various TOML-related utilities used by GradleDetector, but placed in their own
// compilation unit since GradleDetector is getting massive.

/** The libraries node in a Gradle versions catalog file */
const val VC_LIBRARIES = "libraries"
/** The plugins node in a Gradle versions catalog file */
const val VC_PLUGINS = "plugins"
/** The versions node in a Gradle versions catalog file */
const val VC_VERSIONS = "versions"

/**
 * Given a key-value pair in the `\[libraries]` block in a Gradle
 * versions catalog, returns the corresponding group:artifact:version
 * string, along with a reference to the `\[versions]` declaration value
 * node. Or null, if this is not a gradle library declaration or if its
 * coordinate cannot be resolved.
 */
fun getLibraryFromTomlEntry(versions: LintTomlMapValue?, library: LintTomlValue): Pair<String, LintTomlValue>? {
    if (library is LintTomlLiteralValue) {
        val coordinate = library.getActualValue()?.toString()?.trim() ?: return null
        return Pair(coordinate, library)
    }
    if (library !is LintTomlMapValue) return null
    val versionNode = getVersion(library, versions) ?: return null
    val version = versionNode.getActualValue()?.toString()?.trim() ?: return null

    val module = library["module"]
    val artifact = if (module is LintTomlLiteralValue) {
        module.getActualValue()?.toString()?.trim() ?: return null
    } else {
        val group = library["group"]?.getActualValue()?.toString()?.trim() ?: return null
        val name = library["name"]?.getActualValue()?.toString()?.trim() ?: return null
        "$group:$name"
    }
    return Pair("$artifact:$version", versionNode)
}

fun getPluginFromTomlEntry(versions: LintTomlMapValue?, library: LintTomlValue): Pair<String, LintTomlValue>? {
    if (library is LintTomlLiteralValue) {
        val coordinate = library.getActualValue()?.toString()?.trim() ?: return null
        return Pair(coordinate, library)
    }
    if (library !is LintTomlMapValue) return null
    val versionNode = getVersion(library, versions) ?: return null
    val version = versionNode.getActualValue()?.toString()?.trim() ?: return null

    val id = library["id"]
    if (id is LintTomlLiteralValue) {
        val artifact = id.getActualValue()?.toString()?.trim() ?: return null
        return Pair("$artifact:$version", versionNode)
    }
    return null
}

private fun getVersion(library: LintTomlMapValue, versionsMap: LintTomlMapValue?): LintTomlValue? {
    val version = library["version"] ?: return null
    when (version) {
        is LintTomlLiteralValue -> return version

        is LintTomlMapValue -> {
            val ref = version["ref"]
            val richVersion = if (ref != null) {
                versionsMap ?: return null
                val versionVariable = ref.getActualValue()?.toString()?.trim() ?: return null
                val referenced = versionsMap[versionVariable] ?: return null
                if (referenced !is LintTomlMapValue) {
                    return referenced
                }
                referenced
            } else {
                version
            }
            // Rich version; could be specifying require, strictly, prefer, reject, rejectAll.
            return richVersion.first()
        }

        else -> return null
    }
}

private fun CharSequence.findNextLineStart(start: Int): Int {
    val lineEnd = indexOf('\n', start)
    if (lineEnd != -1) {
        return lineEnd + 1
    }
    return -1
}

fun createMoveToTomlFix(
    context: GradleContext,
    librariesMap: LintTomlValue,
    gc: GradleCoordinate,
    valueCookie: Any
): Pair<String, LintFix>? {
    if (librariesMap !is LintTomlMapValue) return null
    val document = librariesMap.getDocument()
    val versionsMap = document.getValue(VC_VERSIONS) as? LintTomlMapValue

    var artifactLibrary: LintTomlValue? = null
    var artifactVersionNode: LintTomlValue? = null
    var artifactVersion: Version? = null

    for ((key, library) in librariesMap.getMappedValues()) {
        val (coordinate, versionNode) = getLibraryFromTomlEntry(versionsMap, library) ?: continue
        val c = GradleCoordinate.parseCoordinateString(coordinate) ?: continue
        if (c.groupId == gc.groupId && c.artifactId == gc.artifactId) {
            // We already have this dependency in the TOML file!
            if (c.revision == gc.revision) {
                // It even matches by version! Just switch the dependency over to it!
                val fix = createSwitchToLibraryFix(document, library, key, context, valueCookie, safe = true) ?: return null
                return "Use the existing version catalog reference (`${fix.replacement}`) instead" to fix
            } else if (artifactLibrary == null || artifactVersion != null && c.lowerBoundVersion > artifactVersion) {
                // There could be multiple declaration of this library (for different versions); pick the highest one
                artifactLibrary = library
                artifactVersionNode = versionNode
                artifactVersion = c.lowerBoundVersion
            }
        }
    }

    if (artifactLibrary != null) {
        // We don't have an exact match in the version catalog, but we *do* have the artifact there.
        // This means we can offer some options:

        // (1) Insert new library definition for this exact version
        val addNew = createAddNewCatalogLibrary(
            gc, versionsMap, librariesMap, document, context, valueCookie,
            includeVersionInKey = true
        )
        // (2) Change the version catalog's artifact variable to point to this new version (if higher)
        val higher = artifactVersion != null && gc.lowerBoundVersion > artifactVersion
        val changeVersionFix = if (higher) createChangeVersionFix(gc.revision, artifactVersionNode!!) else null
        // (3) Change this gradle dependency reference to instead point to the version catalog
        val key = artifactLibrary.getKey()!!
        val switchToVersion = createSwitchToLibraryFix(document, artifactLibrary, key, context, valueCookie, safe = false)
        val fix = LintFix.create().alternatives(
            addNew,
            changeVersionFix,
            switchToVersion
        )
        val artifact = "${gc.groupId}:${gc.artifactId}"
        val message = "Use version catalog instead ($artifact is already available as `$key`, but using version $artifactVersion instead)"
        return message to fix
    }

    // We didn't find this artifact in the version catalog at all; offer to add it.
    val fix = createAddNewCatalogLibrary(gc, versionsMap, librariesMap, document, context, valueCookie) ?: return null
    return "Use version catalog instead" to fix
}

/**
 * Creates fix which changes the version variable in [versionNode] to
 * [version]
 */
private fun createChangeVersionFix(version: String, versionNode: LintTomlValue): LintFix {
    return LintFix.create()
        .name("Change ${versionNode.getKey()} to $version")
        .replace()
        .range(versionNode.getLocation())
        .all()
        .with("\"$version\"")
        .build()
}

/**
 * Creates fix which creates a new version catalog entry (library and
 * version name) for the given [gc] library
 */
private fun createAddNewCatalogLibrary(
    gc: GradleCoordinate,
    versionsMap: LintTomlMapValue?,
    librariesMap: LintTomlMapValue,
    document: LintTomlDocument,
    context: GradleContext,
    valueCookie: Any,
    includeVersionInKey: Boolean = false
): LintFix? {
    // TODO: Be smarter about version variables:
    //   (1) use the same naming convention
    //   (2) for related libraries, offer to "reuse" it? (e.g. for related kotlin libraries. Be careful here.)

    val versionVariable = pickVersionVariableName(gc, versionsMap?.getMappedValues())
    val libraryVariable = pickLibraryVariableName(gc, librariesMap.getMappedValues(), includeVersionInKey)

    val source = document.getSource()
    val versionVariableFix = createAddVersionFix(versionsMap, source, document, versionVariable, gc)
    val usedVariable = if (versionVariableFix == null) null else versionVariable
    val insertLibraryFix = createInsertLibraryFix(librariesMap, source, libraryVariable, gc, usedVariable, document) ?: return null
    val gradleFix = createReplaceWithLibraryReferenceFix(document, context, valueCookie, libraryVariable, true)

    return LintFix.create()
        .name("Replace with new library catalog declaration for $libraryVariable")
        .composite(
            // Insert versions variable declaration
            versionVariableFix,
            // Insert library declaration
            insertLibraryFix,
            // Update build.gradle to link to it
            gradleFix
        )
        .autoFix()
}

/**
 * Creates a fix which replaces the build.gradle dependency (at
 * [valueCookie] with the given [library] reference in the version
 * catalog
 */
private fun createSwitchToLibraryFix(
    document: LintTomlDocument,
    library: LintTomlValue,
    key: String,
    context: GradleContext,
    valueCookie: Any,
    safe: Boolean
): LintFix.ReplaceString? {
    val variableName = library.getFullKey()?.removePrefix("libraries.") ?: return null
    return createReplaceWithLibraryReferenceFix(
        document, context, valueCookie, variableName, safe,
        "Replace with existing version catalog reference `$key`"
    )
}

private fun createReplaceWithLibraryReferenceFix(
    document: LintTomlDocument,
    context: GradleContext,
    valueCookie: Any,
    libraryVariable: String,
    safe: Boolean,
    name: String? = null
): LintFix.ReplaceString {
    val catalogName = getCatalogName(document)
    // https://docs.gradle.org/current/userguide/platforms.html#sub:mapping-aliases-to-accessors
    val gradleKey = StringBuilder()
    for (i in libraryVariable.indices) {
        val c = libraryVariable[i]
        if (c == '-' ||
            c == '_' && (i == 0 || !libraryVariable[i].isDigit() && (i == libraryVariable.length - 1 || !libraryVariable[i + 1].isDigit()))
        ) {
            gradleKey.append('.')
        } else {
            gradleKey.append(c)
        }
    }
    val replacement = "$catalogName.$gradleKey"
    return LintFix.create()
        .replace()
        .range(context.getLocation(valueCookie))
        .all()
        .with(replacement)
        .autoFix(safe, safe).apply { if (name != null) name(name) }
        .build() as LintFix.ReplaceString
}

/**
 * Attempts to return the name of the Gradle version catalog for this
 * TOML file.
 *
 * For now, we're just guessing based on the TOML file name. But there's
 * no guarantee that the filename in `gradle/` corresponds to the
 * catalogName in gradle build files: as an example, see
 * https://docs.gradle.org/current/userguide/platforms.html#sec:importing-catalog-from-file
 * This is intractable in general; settings files can contain arbitrary
 * code.
 */
private fun getCatalogName(document: LintTomlDocument) =
    document.getFile().name.substringBefore('.')

/**
 * Checks whether this map is in **mostly** alphabetical order.
 * Initially, we were only using the alphabetical insert location if the
 * list was in a completely correct alphabetical order, but in reality
 * there are many cases where the list looks mostly alphabetical but one
 * or two elements are inserted slightly incorrectly, and in this case
 * we'd fall back to appending to the end. Here we return true if the
 * list is *mostly* alphabetical, and then we'll pick the best effort
 * insertion point in that case.
 */
private fun LintTomlMapValue.isInAlphabeticalOrder(): Boolean {
    var lastKey = ""
    var correctCount = 0
    val map = getMappedValues()
    val keyCount = map.size
    if (keyCount <= 3) { // tiny lists -- let's try to insert in alphabetical order.
        return true
    }
    for ((key, _) in map) {
        if (key.compareTo(lastKey, ignoreCase = true) >= 0) {
            correctCount++
        }
        lastKey = key
    }
    // If 90% of the keys are in the right place, let's call that alphabetical order.
    return (correctCount - keyCount) <= 3 || correctCount * 100 / keyCount >= 90
}

/**
 * For the given ordered [map] of key/values, *if* the list of keys is
 * alphabetical, then return the first value which comes *after* the
 * [name] in alphabetical order.
 */
private fun getBeforeIfAlphabeticOrder(mapOwner: LintTomlMapValue?, name: String): LintTomlValue? {
    mapOwner ?: return null
    if (!mapOwner.isInAlphabeticalOrder()) {
        return null
    }
    for ((key, value) in mapOwner.getMappedValues()) {
        if (key.compareTo(name, ignoreCase = true) >= 0) {
            return value
        }
    }
    return null
}

private fun createAddVersionFix(
    versionsMap: LintTomlMapValue?,
    source: CharSequence,
    document: LintTomlDocument,
    versionVariable: String,
    gc: GradleCoordinate
): LintFix? {
    val separator = if (spaceAroundEquals(versionsMap)) " = " else "="
    var variableDeclaration = "$versionVariable$separator\"${gc.revision}\"\n"

    // Is the list already in alphabetical order?
    var independent = true
    val before = getBeforeIfAlphabeticOrder(versionsMap, versionVariable)
    val versionInsertOffset: Int = if (before != null) {
        before.getKeyStartOffset()
    } else {
        // No, place it last
        val lastVersion = versionsMap?.last()
        if (lastVersion != null) {
            source.findNextLineStart(lastVersion.getEndOffset()).let {
                if (it == -1) source.length else it
            }
        } else {
            val versionsIndex = source.indexOf("[versions]")
            if (versionsIndex != -1) {
                // There's only a [versions] entry in the document, but no version variables; insert the first one
                source.findNextLineStart(versionsIndex)
            } else {
                // We *could* insert a new [versions] block here. But in batch mode
                // this is problematic (the quickfixes would all insert the same thing),
                // and more importantly, if the user *does* have a [libraries] block but
                // no [versions] block, they may prefer to just use explicit version=
                // instead of version.ref=, so we'll just do that.
                //    variableDeclaration = "[versions]\n$variableDeclaration"
                //    independent = false
                //    // Insert at the top
                //    0
                return null
            }
        }
    }
    return LintFix.create().replace()
        .range(Location.create(document.getFile(), source, versionInsertOffset, versionInsertOffset))
        .beginning()
        .with(variableDeclaration)
        .autoFix(true, independent)
        .build()
}

private fun createInsertLibraryFix(
    librariesMap: LintTomlMapValue,
    source: CharSequence,
    libraryVariable: String,
    gc: GradleCoordinate,
    versionVariable: String?,
    document: LintTomlDocument
): LintFix? {
    val before = getBeforeIfAlphabeticOrder(librariesMap, libraryVariable)
    var libraryInsertOffset: Int = if (before != null) {
        before.getKeyStartOffset()
    } else {
        val lastLibrary = librariesMap.last()
        if (lastLibrary != null) {
            source.findNextLineStart(lastLibrary.getEndOffset())
        } else {
            val librariesIndex = source.indexOf("[libraries]")
            if (librariesIndex != -1) {
                // There's only a [libraries] entry in the document, but no version variables; insert the first one
                source.findNextLineStart(librariesIndex)
            } else {
                // There's no existing [libraries] entry in the version catalog.
                // This shouldn't happen; we shouldn't have considered the project having version
                // catalogs if there is no [libraries] node in the TOML model
                return null
            }
        }
    }

    var prefix = ""
    var suffix = ""

    if (libraryInsertOffset == -1) {
        // Not found in the [libraries] section; put it before the next section
        val nextSection = librariesMap.next()
        if (nextSection != null) {
            libraryInsertOffset = nextSection.getKeyStartOffset()
            if (libraryInsertOffset == -1) {
                libraryInsertOffset = nextSection.getStartOffset()
            }
            source[libraryInsertOffset]
            // Back up over blank lines between the sections
            while (libraryInsertOffset > 0 && source[libraryInsertOffset - 1] != '\n') {
                libraryInsertOffset--
            }
            prefix = ""
            suffix = "\n"
        } else {
            // Nothing left in the document; place it at the end
            libraryInsertOffset = source.length
        }
    }

    if (libraryInsertOffset > 0 && source[libraryInsertOffset - 1] != '\n') {
        prefix = "\n"
    } else {
        suffix = "\n"
    }

    val version = if (versionVariable != null) "version.ref = \"$versionVariable\"" else "version = \"${gc.revision}\""
    val moduleDeclaration = "$prefix$libraryVariable = { module = \"${gc.groupId}:${gc.artifactId}\", $version }$suffix"
    return LintFix.create().replace()
        .range(Location.create(document.getFile(), source, libraryInsertOffset, libraryInsertOffset))
        .beginning()
        .with(moduleDeclaration)
        .autoFix()
        .build()
}

private fun spaceAroundEquals(versionsMap: LintTomlMapValue?): Boolean {
    versionsMap ?: return true
    val first = versionsMap.first()
    val keyStart = first?.getKeyStartOffset() ?: return true
    val valueStart = first.getStartOffset()
    val indexOf = first.getDocument().getSource().indexOf(" =", keyStart)
    return indexOf != -1 && indexOf < valueStart
}

private fun GradleCoordinate.isAndroidX(): Boolean = groupId.startsWith("androidx.")

/**
 * For the given `\[libraries]` table, pick a new/unique library name
 * key which represents the given [gc] coordinate, and is unique, and
 * ideally matches the existing naming style.
 */
@VisibleForTesting
fun pickLibraryVariableName(gc: GradleCoordinate, libraries: Map<String, LintTomlValue>, includeVersionInKey: Boolean): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
    for ((key, _) in libraries) {
        reserved.add(key)
    }

    val reservedQuickfixNames = getReservedQuickfixNames(VC_LIBRARIES)
    for (key in reservedQuickfixNames) {
        reserved.add(key)
    }

    val suggestion = pickLibraryVariableName(gc, includeVersionInKey, reserved)
    reservedQuickfixNames.add(suggestion)

    return suggestion
}

private fun pickLibraryVariableName(
    gc: GradleCoordinate,
    includeVersionInKey: Boolean,
    reserved: Set<String>
): String {
    val versionSuffix = if (includeVersionInKey) "-" + gc.revision.replace('.', '_').toSafeKey() else ""

    if (gc.isAndroidX() && (reserved.isEmpty() || reserved.any { it.startsWith("androidx-") })) {
        val key = "androidx-${gc.artifactId.toSafeKey()}$versionSuffix"
        if (!reserved.contains(key)) {
            return key
        }
    }

    // Try a few combinations: just the artifact name, just the group-suffix and the artifact name,
    // just the group-prefix and the artifact name, etc.
    val artifactId = gc.artifactId.toSafeKey()
    val artifactKey = artifactId + versionSuffix
    if (!reserved.contains(artifactKey)) {
        return artifactKey
    }

    // Normally the groupId suffix plus artifact is used, e.g.
    //  "org.jetbrains.kotlin:kotlin-reflect" => "kotlin-kotlin-reflect"
    val groupSuffix = gc.groupId.substringAfterLast('.').toSafeKey()
    if (!(artifactId.startsWith(groupSuffix))) {
        val withGroupSuffix = "$groupSuffix-$artifactId$versionSuffix"
        if (!reserved.contains(withGroupSuffix)) {
            return withGroupSuffix
        }
    }

    val groupPrefix = getGroupPrefix(gc)
    val withGroupPrefix = "$groupPrefix-$artifactId$versionSuffix"
    if (!reserved.contains(withGroupPrefix)) {
        return withGroupPrefix
    }

    val groupId = gc.groupId.toSafeKey()
    val full = "$groupId-$artifactId$versionSuffix"
    if (!reserved.contains(full)) {
        return full
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    var id = 2
    while (true) {
        val name = "${full}${if (versionSuffix.isNotEmpty()) "-" else ""}${id++}"
        // Will eventually succeed
        if (!reserved.contains(name)) {
            return name
        }
    }
}

/**
 * Picks a suitable name for the version variable to use for [gc] which
 * is unique and ideally follows the existing naming style.
 */
@VisibleForTesting
fun pickVersionVariableName(gc: GradleCoordinate, versionsMap: Map<String, LintTomlValue>?): String {
    val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)

    if (versionsMap != null) {
        for ((key, _) in versionsMap) {
            reserved.add(key)
        }
    }

    val reservedQuickfixNames = getReservedQuickfixNames(VC_VERSIONS)
    for (key in reservedQuickfixNames) {
        reserved.add(key)
    }

    val suggestion = pickVersionVariableName(gc, reserved)
    reservedQuickfixNames.add(suggestion)

    return suggestion
}

private fun pickVersionVariableName(gc: GradleCoordinate, reserved: Set<String>): String {
    // If using the artifactVersion convention, follow that
    val artifact = gc.artifactId.toSafeKey()

    if (reserved.isEmpty()) {
        return artifact
    }

    // Use a case-insensitive set when checking for clashes, such that
    // we don't for example pick a new variable named "appcompat" if "appCompat"
    // is already in the map.
    var haveCamelCase = false
    var haveHyphen = false
    for (name in reserved) {
        for (i in name.indices) {
            val c = name[i]
            if (c == '-') {
                haveHyphen = true
            } else if (i > 0 && c.isUpperCase() && name[i - 1].isLowerCase() && name[0].isLowerCase()) {
                haveCamelCase = true
            }
        }
    }

    val artifactCamel = if (artifact.contains('-')) CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, artifact) else artifact
    val artifactName = if (haveCamelCase)
        artifactCamel
    else
        artifact

    if (reserved.isNotEmpty() && reserved.first().endsWith("Version")) {
        val withVersion = "${artifactCamel}Version"
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    // Default convention listed in https://docs.gradle.org/current/userguide/platforms.html seems to be to just
    // use the artifact name
    if (!reserved.contains(artifactName)) {
        return artifactName
    }

    if (!haveHyphen) {
        val withVersion = "${artifactCamel}Version"
        if (!reserved.contains(withVersion)) {
            return withVersion
        }
    }

    val groupPrefix = getGroupPrefix(gc)
    val withGroupIdPrefix = "$groupPrefix-$artifactName"
    if (!reserved.contains(withGroupIdPrefix)) {
        return withGroupIdPrefix
    }

    if (!haveHyphen) {
        val withGroupIdPrefixVersion = "$groupPrefix-${artifactCamel}Version"
        if (!reserved.contains(withGroupIdPrefixVersion)) {
            return withGroupIdPrefixVersion
        }
    }

    // With full group
    val groupId = gc.groupId.toSafeKey()
    val withGroupId = "$groupId-$artifactName"
    if (!reserved.contains(withGroupId)) {
        return withGroupId
    }

    // Final fallback; this is unlikely but JUST to be sure we get a unique version
    var id = 2
    while (true) {
        val name = "${withGroupId}${id++}"
        // Will eventually succeed
        if (!reserved.contains(name)) {
            return name
        }
    }
}

private fun getReservedQuickfixNames(key: String): MutableSet<String> {
    val reservedQuickfixNames = GradleDetector.reservedQuickfixNames
        ?: mutableMapOf<String, MutableSet<String>>().also { GradleDetector.reservedQuickfixNames = it }
    return reservedQuickfixNames[key]
        ?: mutableSetOf<String>().also { reservedQuickfixNames[key] = it }
}

private fun getGroupPrefix(gc: GradleCoordinate): String {
    // For com.google etc., use "google" instead of "com"
    val groupPrefix = gc.groupId.substringBefore('.').toSafeKey()
    if (groupPrefix == "com" || groupPrefix == "org" || groupPrefix == "io") {
        return gc.groupId.substringAfter('.').substringBefore('.').toSafeKey()
    }
    return groupPrefix.toSafeKey()
}

private fun String.toSafeKey(): String {
    // Should filter to set of valid characters in an unquoted key; see `unquoted-key-char` in
    // https://github.com/toml-lang/toml/blob/main/toml.abnf .
    // In practice this seems close enough to Java's isLetterOrDigit definition.
    if (all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        sb.append(if (c.isLetterOrDigit() || c == '-') c else if (c == '.') '-' else '_')
    }
    return sb.toString()
}
