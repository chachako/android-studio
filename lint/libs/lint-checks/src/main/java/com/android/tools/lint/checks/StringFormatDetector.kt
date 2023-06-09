/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_OBJECT
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Location.Companion.create
import com.android.tools.lint.detector.api.Location.ResourceItemHandle
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getFileNameWithParent
import com.android.tools.lint.detector.api.isEnglishResource
import com.android.tools.lint.detector.api.isKotlin
import com.android.utils.CharSequences
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isNewArray
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Collections
import java.util.EnumSet
import java.util.Locale
import java.util.regex.Pattern

/**
 * Check which looks for problems with formatting strings such as inconsistencies between
 * translations or between string declaration and string usage in Java.
 *
 *
 * TODO
 *
 *
 *  * Handle Resources.getQuantityString as well
 *  * Remove all the batch mode handling here; instead of accumulating all strings we can now
 * limit the analysis directly to resolving strings from String#format calls, so there's no
 * longer any ambiguity about what is a formatting string and what is not. One small challenge
 * is what to do about formatted= attributes which we can't look up later; maybe only flag
 * these in batch mode. (It's also unlikely to happen; these strings tend not to be used from
 * String#format).
 *  * Add support for Kotlin strings
 *
 */
class StringFormatDetector
/** Constructs a new [StringFormatDetector] check  */
  : ResourceXmlDetector(), SourceCodeScanner {
  /**
   * Map from a format string name to a list of declaration file and actual formatting string
   * content. We're using a list since a format string can be defined multiple times, usually for
   * different translations.
   */
  private var mFormatStrings: MutableMap<String, MutableList<Pair<Location.Handle, String>>>? = null

  /** Map of strings that do not contain any formatting.  */
  private val mNotFormatStrings: MutableMap<String, Location.Handle> = LinkedHashMap()

  /**
   * Set of strings that have an unknown format such as date formatting; we should not flag these
   * as invalid when used from a String#format call
   */
  private var mIgnoreStrings: MutableSet<String>? = null
  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(SdkConstants.TAG_STRING)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val childNodes = element.childNodes
    if (childNodes.length > 0) {
      if (childNodes.length == 1) {
        val child = childNodes.item(0)
        val type = child.nodeType
        if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
          checkTextNode(context, element, stripQuotes(child.nodeValue))
        }
      } else {
        // Concatenate children and build up a plain string.
        // This is needed to handle xliff localization documents,
        // but this needs more work so ignore compound XML documents as
        // string values for now:
        val sb = StringBuilder()
        addText(sb, element)
        if (sb.isNotEmpty()) {
          checkTextNode(context, element, sb.toString())
        }
      }
    }
  }

  private fun checkTextNode(context: XmlContext, element: Element, text: String) {
    val name = element.getAttribute(SdkConstants.ATTR_NAME)
    var found = false
    var foundPlural = false

    // Look at the String and see if it's a format string (contains
    // positional %'s)
    var j = 0
    val m = text.length
    while (j < m) {
      val c = text[j]
      if (c == '\\') {
        j++
      }
      if (c == '%') {
        // Also make sure this String isn't an unformatted String
        val formatted = element.getAttribute("formatted")
        if (!formatted.isEmpty() && !java.lang.Boolean.parseBoolean(formatted)) {
          if (!mNotFormatStrings.containsKey(name)) {
            val handle = context.createLocationHandle(element)
            handle.clientData = element
            mNotFormatStrings[name] = handle
          }
          return
        }

        // See if it's not a format string, e.g. "Battery charge is 100%!".
        // If so we want to record this name in a special list such that we can
        // make sure you don't attempt to reference this string from a String.format
        // call.
        val matcher = FORMAT.matcher(text)
        if (!matcher.find(j)) {
          if (!mNotFormatStrings.containsKey(name)) {
            val handle = context.createLocationHandle(element)
            handle.clientData = element
            mNotFormatStrings[name] = handle
          }
          return
        }
        val conversion = matcher.group(6)
        val conversionClass = getConversionClass(conversion[0])
        if (conversionClass == CONVERSION_CLASS_UNKNOWN || matcher.group(5) != null) {
          if (mIgnoreStrings == null) {
            mIgnoreStrings = HashSet()
          }
          mIgnoreStrings!!.add(name)

          // Don't process any other strings here; some of them could
          // accidentally look like a string, e.g. "%H" is a hash code conversion
          // in String.format (and hour in Time formatting).
          return
        }
        if (conversionClass == CONVERSION_CLASS_INTEGER && !foundPlural) {
          // See if there appears to be further text content here.
          // Look for whitespace followed by a letter, with no punctuation in between
          for (k in matcher.end() until m) {
            val nc = text[k]
            if (!Character.isWhitespace(nc)) {
              if (Character.isLetter(nc)) {
                foundPlural = checkPotentialPlural(context, element, text, k)
              }
              break
            }
          }
        }
        found = true
        j++ // Ensure that when we process a "%%" we don't separately check the second %
      }
      j++
    }
    if (!context.project.reportIssues) {
      // If this is a library project not being analyzed, ignore it
      return
    }
    if (name != null) {
      val handle = context.createLocationHandle(element)
      handle.clientData = element
      if (found) {
        // Record it for analysis when seen in Java code
        if (mFormatStrings == null) {
          mFormatStrings = LinkedHashMap()
        }
        var list = mFormatStrings!![name]
        if (list == null) {
          list = ArrayList()
          mFormatStrings!![name] = list
        }
        list.add(handle to text)
      } else {
        if (!isReference(text)) {
          mNotFormatStrings[name] = handle
        }
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (mFormatStrings != null) {
      val checkCount = context.isEnabled(ARG_COUNT)
      val checkValid = context.isEnabled(INVALID)
      val checkTypes = context.isEnabled(ARG_TYPES)

      // Ensure that all the format strings are consistent with respect to each other;
      // e.g. they all have the same number of arguments, they all use all the
      // arguments, and they all use the same types for all the numbered arguments
      for (entry in mFormatStrings!!.entries) {
        val name = entry.key
        var list = entry.value

        // Check argument counts
        if (checkCount) {
          val notFormatted = mNotFormatStrings[name]
          if (notFormatted != null) {
            list = ImmutableList.builder<Pair<Location.Handle, String>>()
              .add(notFormatted to name)
              .addAll(list)
              .build()
          }
          checkArity(context, name, list)
        }

        // Check argument types (and also make sure that the formatting strings are valid)
        if (checkValid || checkTypes) {
          checkTypes(context, checkValid, checkTypes, name, list)
        }
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(SdkConstants.FORMAT_METHOD, SdkConstants.GET_STRING_METHOD)
  }

  override fun visitMethodCall(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
  ) {
    val evaluator = context.evaluator
    val methodName = method.name
    if (methodName == SdkConstants.FORMAT_METHOD) {
      if (evaluator.isMemberInClass(method, TYPE_STRING)) {
        // Check formatting parameters for
        //   java.lang.String#format(String format, Object... formatArgs)
        //   java.lang.String#format(Locale locale, String format, Object... formatArgs)
        checkStringFormatCall(
          context, method, node, method.parameterList.parametersCount == 3
        )

        // TODO: Consider also enforcing
        // java.util.Formatter#format(String string, Object... formatArgs)
      }
    } else {
      // Look up any of these string formatting methods:
      // android.content.res.Resources#getString(@StringRes int resId, Object... formatArgs)
      // android.content.Context#getString(@StringRes int resId, Object... formatArgs)
      // android.app.Fragment#getString(@StringRes int resId, Object... formatArgs)
      // android.support.v4.app.Fragment#getString(@StringRes int resId, Object... formatArgs)

      // Many of these also define a plain getString method:
      // android.content.res.Resources#getString(@StringRes int resId)
      // However, while it's possible that these contain formatting strings) it's
      // also possible that they're looking up strings that are not intended to be used
      // for formatting so while we may want to warn about this it's not necessarily
      // an error.
      if (method.parameterList.parametersCount < 2) {
        return
      }
      if (evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_RESOURCES, false)
        || evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_CONTEXT, false)
        || evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_FRAGMENT, false)
        || evaluator.isMemberInSubClassOf(method, AndroidXConstants.CLASS_V4_FRAGMENT.oldName(), false)
        || evaluator.isMemberInSubClassOf(method, AndroidXConstants.CLASS_V4_FRAGMENT.newName(), false)
      ) {
        checkStringFormatCall(context, method, node, false)
      }

      // TODO: Consider also looking up
      // android.content.res.Resources#getQuantityString(@PluralsRes int id, int quantity,
      //              Object... formatArgs)
      // though this will require being smarter about cross referencing formatting
      // strings since we'll need to go via the quantity string definitions
    }
  }

  /**
   * Check the given String.format call (with the given arguments) to see if the string format is
   * being used correctly
   *
   * @param context the context to report errors to
   * @param calledMethod the method being called
   * @param call the AST node for the [String.format]
   * @param specifiesLocale whether the first parameter is a locale string, shifting the
   */
  private fun checkStringFormatCall(
    context: JavaContext,
    calledMethod: PsiMethod,
    call: UCallExpression,
    specifiesLocale: Boolean
  ) {
    val argIndex = if (specifiesLocale) 1 else 0
    val args = call.valueArguments
    if (args.size <= argIndex) {
      return
    }
    val argument = args[argIndex]
    val resource = ResourceEvaluator.getResource(context.evaluator, argument)
    if (resource == null || resource.isFramework || resource.type != ResourceType.STRING) {
      checkTrivialString(context, calledMethod, call, args, specifiesLocale)
      return
    }
    val name = resource.name
    if (mIgnoreStrings != null && mIgnoreStrings!!.contains(name)) {
      return
    }
    var passingVarArgsArray = false
    var callCount = args.size - 1 - argIndex
    if (callCount == 1) {
      // If instead of a varargs call like
      //    getString(R.string.foo, arg1, arg2, arg3)
      // the code is calling the varargs method with a packed Object array, as in
      //    getString(R.string.foo, new Object[] { arg1, arg2, arg3 })
      // we'll need to handle that such that we don't think this is a single
      // argument
      var lastArg: UExpression? = args[args.size - 1].skipParenthesizedExprDown()
      val parameterList = calledMethod.parameterList
      val parameterCount = parameterList.parametersCount
      if (parameterCount > 0
        && parameterList.parameters[parameterCount - 1].isVarArgs
      ) {
        var knownArity = false
        var argWasReference = false
        if (lastArg is UReferenceExpression) {
          val resolved = lastArg.resolve()
          if (resolved is PsiVariable) {
            var initializer = UastFacade.getInitializerBody((resolved as PsiVariable?)!!)
            if (initializer != null) {
              initializer = initializer.skipParenthesizedExprDown()
            }
            if (initializer != null
              && (initializer.isNewArray()
                || initializer.isArrayInitializer())
            ) {
              argWasReference = true
              // Now handled by check below
              lastArg = initializer
            }
          }
        }
        if (lastArg != null
          && (lastArg.isNewArray()
            || lastArg.isArrayInitializer())
        ) {
          val arrayInitializer = lastArg as UCallExpression
          if (lastArg.isNewArrayWithInitializer()
            || lastArg.isArrayInitializer()
          ) {
            callCount = arrayInitializer.valueArgumentCount
            knownArity = true
          } else if (lastArg.isNewArrayWithDimensions()) {
            val arrayDimensions = arrayInitializer.valueArguments
            if (arrayDimensions.size == 1) {
              val first = arrayDimensions[0].skipParenthesizedExprDown()
              if (first is ULiteralExpression) {
                val o = first.value
                if (o is Int) {
                  callCount = o
                  knownArity = true
                }
              }
            }
          }
          if (!knownArity) {
            if (!argWasReference) {
              return
            }
          } else {
            passingVarArgsArray = true
          }
        }
      }
    }
    if (callCount > 0 && mNotFormatStrings.containsKey(name)) {
      checkNotFormattedHandle(context, call, name, mNotFormatStrings[name])
      return
    }
    var list = if (mFormatStrings != null) mFormatStrings!![name] else null
    if (list == null) {
      val client = context.client
      val full = context.isGlobalAnalysis()
      val project = if (full) context.mainProject else context.project
      val resources = client.getResources(project, ResourceRepositoryScope.LOCAL_DEPENDENCIES)
      val items: List<ResourceItem>
      items = resources.getResources(ResourceNamespace.TODO(), ResourceType.STRING, name)
      for (item in items) {
        var v : ResourceValue? = item.resourceValue ?: continue
        var value : String? = v?.rawXmlValue ?: continue
        // Attempt to resolve indirection
        if (isReference(value!!)) {
          // Only resolve a few indirections
          for (i in 0..2) {
            val url = ResourceUrl.parse(value!!)
            if (url == null || url.isFramework) {
              break
            }
            val l = resources.getResources(
              ResourceNamespace.TODO(), url.type, url.name
            )
            if (!l.isEmpty()) {
              v = l[0].resourceValue
              if (v != null) {
                value = v.value
                if (value == null || !isReference(value)) {
                  break
                }
              } else {
                break
              }
            } else {
              break
            }
          }
        }
        if (value != null && !isReference(value)) {
          // Make sure it's really a formatting string,
          // not for example "Battery remaining: 90%"
          var isFormattingString = value.indexOf('%') != -1
          var j = 0
          val m = value.length
          while (j < m && isFormattingString) {
            val c = value[j]
            if (c == '\\') {
              j++
            } else if (c == '%') {
              val matcher = FORMAT.matcher(value)
              if (!matcher.find(j)) {
                isFormattingString = false
              } else {
                val conversion = matcher.group(6)
                val conversionClass = getConversionClass(conversion[0])
                if (conversionClass == CONVERSION_CLASS_UNKNOWN
                  || matcher.group(5) != null
                ) {
                  // Some date format etc - don't process
                  return
                }
              }
              j++ // Don't process second % in a %%
            }
            j++
          }
          val handle: Location.Handle = client.createResourceItemHandle(item, false, true)
          if (isFormattingString) {
            if (list == null) {
              list = Lists.newArrayList()
              if (mFormatStrings == null) {
                mFormatStrings = LinkedHashMap()
              }
              mFormatStrings!![name] = list
            }
            list!!.add(handle to value)
          } else if (callCount > 0) {
            checkNotFormattedHandle(context, call, name, handle)
          }
        }
      }
    }
    if (list != null && !list.isEmpty()) {
      list.sortWith(
        java.util.Comparator<Pair<Location.Handle, String>> { o1: Pair<Location.Handle, String>, o2: Pair<Location.Handle, String> ->
          val h1 = o1.first
          val h2 = o2.first
          if (h1 is ResourceItemHandle && h2 is ResourceItemHandle) {
            val item1 = h1.item
            val item2 = h2.item
            val f1 = item1.configuration
            val f2 = item2.configuration
            val delta = f1.compareTo(f2)
            if (delta != 0) {
              delta
            }
            else item1.toString().compareTo(item2.toString())
          }
          else o1.toString().compareTo(o2.toString())
        })
      var reported: MutableSet<String>? = null
      for (pair in list) {
        val s = pair.second
        if (reported != null && reported.contains(s)) {
          continue
        }
        val count = getFormatArgumentCount(s, null)
        val handle = pair.first
        if (count != callCount) {
          val location = context.getLocation(call)
          val secondary = handle.resolve()
          secondary.message = String.format(
            Locale.US,
            "This definition requires %1\$d argument%2\$s",
            count,
            if (count != 1) "s" else ""
          )
          location.secondary = secondary
          val message = String.format(
            Locale.US, "Wrong argument count, format string `%1\$s` requires `%2\$d` but format "
              + "call supplies `%3\$d`",
            name,
            count,
            callCount
          )
          context.report(ARG_TYPES, call, location, message)
          if (reported == null) {
            reported = Sets.newHashSet()
          }
          reported!!.add(s)
        } else {
          if (passingVarArgsArray) {
            // Can't currently check these: make sure we don't incorrectly
            // flag parameters on the Object[] instead of the wrapped parameters
            return
          }
          for (i in 1..count) {
            val argumentIndex = i + argIndex
            val expression = args[argumentIndex]
            var type = expression.getExpressionType()
            if (isInStringExpression(call, expression)) {
              type = getStringType(context, expression)
            }
            if (type != null && type !is UastErrorType) {
              var valid = true
              val formatType = getFormatArgumentType(s, i) ?: continue
              val last = formatType[formatType.length - 1]
              if (formatType.length >= 2
                &&
                formatType[formatType.length - 2].lowercaseChar() == 't'
              ) {
                // Date time conversion.
                // TODO
                continue
              }
              when (last) {
                'b', 'B' -> valid = isBooleanType(type)
                'x', 'X', 'd', 'o', 'e', 'E', 'f', 'g', 'G', 'a', 'A' -> valid = isNumericType(type, true)
                'c', 'C' ->                                     // Unicode character
                  valid = isCharacterType(type)

                'h', 'H' ->                                     // From
                  // https://developer.android.com/reference/java/util/Formatter.html
                  // """The following general conversions may be applied to any
                  // argument type: 'b', 'B', 'h', 'H', 's', 'S' """
                  // We'll still warn about %s since you may have intended
                  // numeric formatting, but hex printing seems pretty well
                  // intended.
                  continue

                's', 'S' ->                                     // String. Can pass anything, but warn about
                  // numbers since you may have meant more
                  // specific formatting. Use special issue
                  // explanation for this?
                  valid = !isBooleanType(type) && !isNumericType(type, false)
              }
              if (!valid) {
                val location = context.getLocation(args[argumentIndex])
                val secondary = handle.resolve()
                secondary.message = "Conflicting argument declaration here"
                location.secondary = secondary
                if (isSuppressed(context, ARG_TYPES, secondary)) {
                  continue
                }
                var suggestion: String? = null
                if (isBooleanType(type)) {
                  suggestion = "`b`"
                } else if (isCharacterType(type)) {
                  suggestion = "'c'"
                } else if (PsiTypes.intType() == type || PsiTypes.longType() == type || PsiTypes.byteType() == type || PsiTypes.shortType() == type) {
                  suggestion = "`d`, 'o' or `x`"
                } else if (PsiTypes.floatType() == type || PsiTypes.doubleType() == type) {
                  suggestion = "`e`, 'f', 'g' or `a`"
                } else if (type is PsiClassType) {
                  val fqn = type.getCanonicalText()
                  if (TYPE_INTEGER_WRAPPER == fqn || TYPE_LONG_WRAPPER == fqn || TYPE_BYTE_WRAPPER == fqn || TYPE_SHORT_WRAPPER == fqn) {
                    suggestion = "`d`, 'o' or `x`"
                  } else if (TYPE_FLOAT_WRAPPER == fqn || TYPE_DOUBLE_WRAPPER == fqn) {
                    suggestion = "`d`, 'o' or `x`"
                  } else if (TYPE_OBJECT == fqn) {
                    suggestion = "'s' or 'h'"
                  }
                }
                suggestion = if (suggestion != null) {
                  (" (Did you mean formatting character "
                    + suggestion
                    + "?)")
                } else {
                  ""
                }
                var canonicalText = type.canonicalText
                canonicalText = canonicalText.substring(canonicalText.lastIndexOf('.') + 1)
                var message = String.format(
                  Locale.US,
                  "Wrong argument type for formatting argument '#%1\$d' "
                    + "in `%2\$s`: conversion is '`%3\$s`', received `%4\$s` "
                    + "(argument #%5\$d in method call)%6\$s",
                  i,
                  name,
                  formatType,
                  canonicalText,
                  argumentIndex + 1,
                  suggestion
                )
                if ((last == 's' || last == 'S') && isNumericType(type, false)) {
                  message = String.format(
                    Locale.US,
                    "Suspicious argument type for formatting argument #%1\$d "
                      + "in `%2\$s`: conversion is `%3\$s`, received `%4\$s` "
                      + "(argument #%5\$d in method call)%6\$s",
                    i,
                    name,
                    formatType,
                    canonicalText,
                    argumentIndex + 1,
                    suggestion
                  )
                }
                context.report(ARG_TYPES, call, location, message)
                if (reported == null) {
                  reported = Sets.newHashSet()
                }
                reported!!.add(s)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Returns true if the given expression in UAST is really a String inside a template expression.
   * This works around a bug in UAST, described in
   * https://issuetracker.google.com/217570491#comment2.
   */
  private fun isInStringExpression(
    call: UCallExpression, expression: UExpression
  ): Boolean {
    val sourcePsi = expression.sourcePsi ?: return false
    val parent = sourcePsi.parent as? KtStringTemplateEntry ?: return false
    val stringElement = parent.parent
    val callPsi = call.sourcePsi
    if (callPsi is KtCallExpression) {
      for (argument in callPsi.valueArguments) {
        val valueExpression = argument.getArgumentExpression()
        if (valueExpression === stringElement) {
          return true
        }
      }
    }
    return false
  }

  private fun getStringType(context: JavaContext, expression: UExpression): PsiType? {
    val element = expression.sourcePsi ?: return null
    val facade = JavaPsiFacade.getInstance(element.project)
    val javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_STRING, element.resolveScope)
    return if (javaLangClass != null) {
      context.evaluator.getClassType(javaLangClass)
    } else {
      null
    }
  }

  private fun checkTrivialString(
    context: JavaContext,
    calledMethod: PsiMethod,
    call: UCallExpression,
    args: List<UExpression>,
    specifiesLocale: Boolean
  ) {
    val stringIndex = if (specifiesLocale) 1 else 0
    val s = ConstantEvaluator.evaluateString(context, args[stringIndex], false) ?: return
    var uppercase = false
    val count = getFormatArgumentCount(s, null)
    for (i in 1..count) {
      val argumentIndex = i + stringIndex
      if (argumentIndex >= args.size) {
        return
      }
      val type = args[argumentIndex].getExpressionType()
      if (type != null) {
        val formatType = getFormatArgumentType(s, i) ?: return
        val last = formatType[formatType.length - 1]
        if (formatType.length >= 2
          && formatType[formatType.length - 2].lowercaseChar() == 't'
        ) {
          return
        }
        when (last) {
          'x', 'X', 'd', 'o', 'e', 'E', 'f', 'g', 'G', 'a', 'A', 'h', 'H' -> return
          'b', 'B' ->                         // '+' concatenation of Booleans does not exist in Kotlin,
            // so "%b" should not be flagged as a trivial conversion.
            if (isKotlin(calledMethod)) {
              return
            }
        }

        // Strings with formatting arguments that contain modifiers (precision,
        // justification, etc.) will not be flagged as trivial.
        if (hasFormatArgumentModifiers(s, i)) {
          return
        }

        // If uppercase formatting is used but all format types are trivial,
        // String.toUpperCase() will be suggested.
        if (Character.isUpperCase(last)) {
          uppercase = true
        }
      }
    }

    // Creates the lint check message based on the conversions in the format string.
    var message = ("This formatting string is trivial. Rather than using "
      + "`String.format` to create your String, it will be more "
      + "performant to concatenate your arguments with `+`. ")
    if (uppercase) {
      message += "If uppercase formatting is necessary, use `String.toUpperCase()`."
    }
    context.report(TRIVIAL, call, context.getLocation(args[stringIndex]), message)
  }

  companion object {
    private val IMPLEMENTATION_XML = Implementation(
      StringFormatDetector::class.java, Scope.ALL_RESOURCES_SCOPE
    )
    private val IMPLEMENTATION_XML_AND_JAVA = Implementation(
      StringFormatDetector::class.java,
      EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
      Scope.JAVA_FILE_SCOPE
    )

    /** Whether formatting strings are invalid  */
    @JvmField
    val INVALID = create(
      "StringFormatInvalid",
      "Invalid format string",
      """
          If a string contains a '%' character, then the string may be a formatting string which will be passed to `String.format` from Java code to replace each '%' occurrence with specific values.

          This lint warning checks for two related problems:
          (1) Formatting strings that are invalid, meaning that `String.format` will throw exceptions at runtime when attempting to use the format string.
          (2) Strings containing '%' that are not formatting strings getting passed to a `String.format` call. In this case the '%' will need to be escaped as '%%'.

          NOTE: Not all Strings which look like formatting strings are intended for use by `String.format`; for example, they may contain date formats intended for `android.text.format.Time#format()`. Lint cannot always figure out that a String is a date format, so you may get false warnings in those scenarios. See the suppress help topic for information on how to suppress errors in that case.
          """,
      Category.MESSAGES,
      9,
      Severity.ERROR,
      IMPLEMENTATION_XML
    )

    /** Whether formatting argument types are consistent across translations  */
    @JvmField
    val ARG_COUNT = create(
      "StringFormatCount",
      "Formatting argument types incomplete or inconsistent",
      """
          When a formatted string takes arguments, it usually needs to reference the same arguments in all translations (or all arguments if there are no translations.

          There are cases where this is not the case, so this issue is a warning rather than an error by default. However, this usually happens when a language is not translated or updated correctly.
          """,
      Category.MESSAGES,
      5,
      Severity.WARNING,
      IMPLEMENTATION_XML
    )

    /** Whether the string format used in a String.format call is trivial  */
    val TRIVIAL = create(
      "StringFormatTrivial",
      "`String.format` string only contains trivial conversions",
      "Every call to `String.format` creates a new `Formatter` instance, which will "
        + "decrease the performance of your app. `String.format` should only be used when "
        + "necessary--if the formatted string contains only trivial conversions "
        + "(e.g. `b`, `s`, `c`) and there are no translation concerns, it will be "
        + "more efficient to replace them and concatenate with `+`.",
      Category.PERFORMANCE,
      5,
      Severity.WARNING,
      IMPLEMENTATION_XML_AND_JAVA
    )
      .setAndroidSpecific(true)
      .setEnabledByDefault(false)

    /** Whether the string format supplied in a call to String.format matches the format string  */
    @JvmField
    val ARG_TYPES = create(
      "StringFormatMatches",
      "`String.format` string doesn't match the XML format string",
      """
          This lint check ensures the following:
          (1) If there are multiple translations of the format string, then all translations use the same type for the same numbered arguments
          (2) The usage of the format string in Java is consistent with the format string, meaning that the parameter types passed to String.format matches those in the format string.
          """,
      Category.MESSAGES,
      9,
      Severity.ERROR,
      IMPLEMENTATION_XML_AND_JAVA
    )

    /** This plural does not use the quantity value  */
    @JvmField
    val POTENTIAL_PLURAL = create(
      "PluralsCandidate",
      "Potential Plurals",
      """This lint check looks for potential errors in internationalization where you have translated a message which involves a quantity and it looks like other parts of the string may need grammatical changes.

For example, rather than something like this:
```xml
  <string name="try_again">Try again in %d seconds.</string>
```
you should be using a plural:
```xml
   <plurals name="try_again">
        <item quantity="one">Try again in %d second</item>
        <item quantity="other">Try again in %d seconds</item>
    </plurals>
```
This will ensure that in other languages the right set of translations are provided for the different quantity classes.

(This check depends on some heuristics, so it may not accurately determine whether a string really should be a quantity. You can use tools:ignore to filter out false positives.""",
      Category.MESSAGES,
      5,
      Severity.WARNING,
      IMPLEMENTATION_XML
    )
      .addMoreInfo(
        "https://developer.android.com/guide/topics/resources/string-resource.html#Plurals"
      )

    fun addText(sb: StringBuilder, node: Node) {
      val nodeType = node.nodeType
      if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
        sb.append(stripQuotes(node.nodeValue.trim { it <= ' ' }))
      } else {
        val childNodes = node.childNodes
        var i = 0
        val n = childNodes.length
        while (i < n) {
          addText(sb, childNodes.item(i))
          i++
        }
      }
    }

    /**
     * Removes all the unescaped quotes. See [Escaping
 * apostrophes and quotes](http://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling)
     */
    @JvmStatic
    fun stripQuotes(s: String): String {
      val sb = StringBuilder()
      var isEscaped = false
      var isQuotedBlock = false
      var i = 0
      val len = s.length
      while (i < len) {
        val current = s[i]
        if (isEscaped) {
          sb.append(current)
          isEscaped = false
        } else {
          isEscaped = current == '\\' // Next char will be escaped so we will just copy it
          if (current == '"') {
            isQuotedBlock = !isQuotedBlock
          } else if (current == '\'') {
            if (isQuotedBlock) {
              // We only add single quotes when they are within a quoted block
              sb.append(current)
            }
          } else {
            sb.append(current)
          }
        }
        i++
      }
      return sb.toString()
    }

    private fun isReference(text: String): Boolean {
      var i = 0
      val n = text.length
      while (i < n) {
        val c = text[i]
        if (!Character.isWhitespace(c)) {
          return c == '@' || c == '?'
        }
        i++
      }
      return false
    }

    /**
     * Checks whether the text begins with a non-unit word, pointing to a string that should
     * probably be a plural instead. This
     */
    private fun checkPotentialPlural(
      context: XmlContext, element: Element, text: String, wordBegin: Int
    ): Boolean {
      // This method should only be called if the text is known to start with a word
      assert(Character.isLetter(text[wordBegin]))
      var wordEnd = wordBegin
      while (wordEnd < text.length) {
        if (!Character.isLetter(text[wordEnd])) {
          break
        }
        wordEnd++
      }

      // Eliminate units, since those are not sentences you need to use plurals for, e.g.
      //   "Elevation gain: %1$d m (%2$d ft)"
      // We'll determine whether something is a unit by looking for
      // (1) Multiple uppercase characters (e.g. KB, or MiB), or better yet, uppercase characters
      //     anywhere but as the first letter
      // (2) No vowels (e.g. ft)
      // (3) Adjacent consonants (e.g. ft); this one can eliminate some legitimate
      //     English words as well (e.g. "the") so we should really limit this to
      //     letter pairs that are not common in English. This is probably overkill
      //     so not handled yet. Instead we use a simpler heuristic:
      // (4) Very short "words" (1-2 letters)
      if (wordEnd - wordBegin <= 2) {
        // Very short word (1-2 chars): possible unit, e.g. "m", "ft", "kb", etc
        return false
      }
      var hasVowel = false
      for (i in wordBegin until wordEnd) {
        // Uppercase character anywhere but first character: probably a unit (e.g. KB)
        val c = text[i]
        if (i > wordBegin && Character.isUpperCase(c)) {
          return false
        }
        if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y') {
          hasVowel = true
        }
      }
      if (!hasVowel) {
        // No vowels: likely unit
        return false
      }
      val word = text.substring(wordBegin, wordEnd)

      // Some other known abbreviations that we don't want to count:
      if (word == "min") {
        return false
      }

      // This heuristic only works in English!
      if (isEnglishResource(context, true)) {
        val message = String.format(
          "Formatting %%d followed by words (\"%1\$s\"): "
            + "This should probably be a plural rather than a string",
          word
        )
        context.report(POTENTIAL_PLURAL, element, context.getLocation(element), message)
        // Avoid reporting multiple errors on the same string
        // (if it contains more than one %d)
        return true
      }
      return false
    }

    private fun isSuppressed(context: Context, issue: Issue, source: Any?): Boolean {
      return if (source is Node) {
        context.driver.isSuppressed(null, issue, source as Node?)
      } else false
    }

    private fun isSuppressed(context: Context, issue: Issue, location: Location): Boolean {
      val source = location.source
      return isSuppressed(context, issue, source)
    }

    private fun isSuppressed(context: Context, issue: Issue, handle: Location.Handle?): Boolean {
      val source = handle!!.clientData
      return if (isSuppressed(context, issue, source)) {
        true
      } else isSuppressed(
        context,
        issue,
        handle.resolve()
      )
    }

    private fun checkTypes(
      context: Context,
      checkValid: Boolean,
      checkTypes: Boolean,
      name: String,
      list: List<Pair<Location.Handle, String>>?
    ) {
      val types: MutableMap<Int, String> = HashMap()
      val typeDefinition: MutableMap<Int, Location.Handle> = HashMap()
      for (pair in list!!) {
        val handle = pair.first
        val formatString = pair.second

        // boolean warned = false;
        val matcher = FORMAT.matcher(formatString)
        var index = 0
        var prevIndex = 0
        var nextNumber = 1
        while (true) {
          if (matcher.find(index)) {
            val matchStart = matcher.start()
            // Make sure this is not an escaped '%'
            while (prevIndex < matchStart) {
              val c = formatString[prevIndex]
              if (c == '\\') {
                prevIndex++
              }
              prevIndex++
            }
            if (prevIndex > matchStart) {
              // We're in an escape, ignore this result
              index = prevIndex
              continue
            }
            index = matcher.end() // Ensure loop proceeds
            val str = formatString.substring(matchStart, matcher.end())
            if (str == "%%" || str == "%n") {
              // Just an escaped %
              continue
            }
            if (checkValid) {
              // Make sure it's a valid format string
              if (str.length > 2 && str[str.length - 2] == ' ') {
                val last = str[str.length - 1]
                // If you forget to include the conversion character, e.g.
                //   "Weight=%1$ g" instead of "Weight=%1$d g", then
                // you're going to end up with a format string interpreted as
                // "%1$ g". This means that the space character is interpreted
                // as a flag character, but it can only be a flag character
                // when used in conjunction with the numeric conversion
                // formats (d, o, x, X). If that's not the case, make a
                // dedicated error message
                if (last != 'd' && last != 'o' && last != 'x' && last != 'X') {
                  if (isSuppressed(context, INVALID, handle)) {
                    return
                  }
                  val location = handle.resolve()
                  val message = String.format(
                    "Incorrect formatting string `%1\$s`; missing conversion "
                      + "character in '`%2\$s`'?",
                    name, str
                  )
                  context.report(INVALID, location, message)
                  // warned = true;
                  continue
                }
              }
            }
            if (!checkTypes) {
              continue
            }

            // Shouldn't throw a number format exception since we've already
            // matched the pattern in the regexp
            var number: Int
            var numberString = matcher.group(1)
            if (numberString != null) {
              // Strip off trailing $
              numberString = numberString.substring(0, numberString.length - 1)
              number = numberString.toInt()
              nextNumber = number + 1
            } else {
              number = nextNumber++
            }
            val format = matcher.group(6)
            val currentFormat = types[number]
            if (currentFormat == null) {
              types[number] = format
              typeDefinition[number] = handle
            } else if (currentFormat != format
              && isIncompatible(currentFormat[0], format[0])
            ) {
              if (isSuppressed(context, ARG_TYPES, handle)) {
                return
              }
              var location = handle.resolve()
              if (isSuppressed(context, ARG_TYPES, location)) {
                return
              }

              // Attempt to limit the location range to just the formatting
              // string in question
              location = refineLocation(
                context,
                location,
                formatString,
                matcher.start(),
                matcher.end()
              )
              val otherLocation = typeDefinition[number]!!.resolve()
              otherLocation.message = "Conflicting argument type (`$currentFormat') here"
              location.secondary = otherLocation
              val f = otherLocation.file
              val message = String.format(
                Locale.US,
                "Inconsistent formatting types for argument #%1\$d in "
                  + "format string `%2\$s` ('%3\$s'): Found both '`%4\$s`' here and '`%5\$s`' "
                  + "in %6\$s",
                number,
                name,
                str,
                format,
                currentFormat,
                getFileNameWithParent(context.client, f)
              )
              // warned = true;
              context.report(ARG_TYPES, location, message)
              break
            }
          } else {
            break
          }
        }
      }
    }

    /**
     * Returns true if two String.format conversions are "incompatible" (meaning that using these
     * two for the same argument across different translations is more likely an error than
     * intentional. Some conversions are incompatible, e.g. "d" and "s" where one is a number and
     * string, whereas others may work (e.g. float versus integer) but are probably not intentional.
     */
    private fun isIncompatible(conversion1: Char, conversion2: Char): Boolean {
      val class1 = getConversionClass(conversion1)
      val class2 = getConversionClass(conversion2)
      return class1 != class2 && class1 != CONVERSION_CLASS_UNKNOWN && class2 != CONVERSION_CLASS_UNKNOWN
    }

    private const val CONVERSION_CLASS_UNKNOWN = 0
    private const val CONVERSION_CLASS_STRING = 1
    private const val CONVERSION_CLASS_CHARACTER = 2
    private const val CONVERSION_CLASS_INTEGER = 3
    private const val CONVERSION_CLASS_FLOAT = 4
    private const val CONVERSION_CLASS_BOOLEAN = 5
    private const val CONVERSION_CLASS_HASHCODE = 6
    private const val CONVERSION_CLASS_PERCENT = 7
    private const val CONVERSION_CLASS_NEWLINE = 8
    private const val CONVERSION_CLASS_DATETIME = 9
    private fun getConversionClass(conversion: Char): Int {
      // See http://developer.android.com/reference/java/util/Formatter.html
      when (conversion) {
        't', 'T' -> return CONVERSION_CLASS_DATETIME
        's', 'S' -> return CONVERSION_CLASS_STRING
        'c', 'C' -> return CONVERSION_CLASS_CHARACTER
        'd', 'o', 'x', 'X' -> return CONVERSION_CLASS_INTEGER
        'f', 'e', 'E', 'g', 'G', 'a', 'A' -> return CONVERSION_CLASS_FLOAT
        'b', 'B' -> return CONVERSION_CLASS_BOOLEAN
        'h', 'H' -> return CONVERSION_CLASS_HASHCODE
        '%' -> return CONVERSION_CLASS_PERCENT
        'n' -> return CONVERSION_CLASS_NEWLINE
      }
      return CONVERSION_CLASS_UNKNOWN
    }

    private fun refineLocation(
      context: Context,
      location: Location,
      formatString: String,
      substringStart: Int,
      substringEnd: Int
    ): Location {
      val startLocation = location.start
      val endLocation = location.end
      if (startLocation != null && endLocation != null) {
        val startOffset = startLocation.offset
        val endOffset = endLocation.offset
        if (startOffset >= 0) {
          val contents = context.client.readFile(location.file)
          if (endOffset <= contents.length && startOffset < endOffset) {
            val formatOffset = CharSequences.indexOf(contents, formatString, startOffset)
            if (formatOffset != -1 && formatOffset <= endOffset) {
              return create(
                location.file,
                contents,
                formatOffset + substringStart,
                formatOffset + substringEnd
              )
            }
          }
        }
      }
      return location
    }

    /**
     * Check that the number of arguments in the format string is consistent across translations,
     * and that all arguments are used
     */
    private fun checkArity(context: Context, name: String, list: List<Pair<Location.Handle, String>>?) {
      // Check to make sure that the argument counts and types are consistent
      var prevCount = -1
      for (pair in list!!) {
        val indices: MutableSet<Int> = HashSet()
        val count = getFormatArgumentCount(pair.second, indices)
        val handle = pair.first
        if (prevCount != -1 && prevCount != count) {
          if (isSuppressed(context, ARG_COUNT, handle)) {
            return
          }
          val location = handle.resolve()
          if (isSuppressed(context, ARG_COUNT, location)) {
            return
          }
          val secondary = list[0].first.resolve()
          if (isSuppressed(context, ARG_COUNT, secondary)) {
            return
          }
          secondary.message = "Conflicting number of arguments ($prevCount) here"
          location.secondary = secondary
          val path = getFileNameWithParent(context.client, secondary.file)
          val message = String.format(
            Locale.US, "Inconsistent number of arguments in formatting string `%1\$s`; "
              + "found both %2\$d here and %3\$d in %4\$s",
            name,
            count,
            prevCount,
            path
          )
          context.report(ARG_COUNT, location, message)
          break
        }
        for (i in 1..count) {
          if (!indices.contains(i)) {
            if (isSuppressed(context, ARG_COUNT, handle)) {
              return
            }
            val all: MutableSet<Int> = HashSet()
            for (j in 1 until count) {
              all.add(j)
            }
            all.removeAll(indices)
            val sorted: List<Int> = ArrayList(all)
            Collections.sort(sorted)
            val location = handle.resolve()
            val message = String.format(
              "Formatting string '`%1\$s`' is not referencing numbered arguments %2\$s",
              name, sorted
            )
            context.report(ARG_COUNT, location, message)
            break
          }
        }
        prevCount = count
      }
    }

    // See java.util.Formatter docs
    @JvmField
    val FORMAT = Pattern.compile( // Generic format:
      //   %[argument_index$][flags][width][.precision]conversion
      //
      "%"
        +  // Argument Index
        "(\\d+\\$)?"
        +  // Flags
        "([-+#, 0(<]*)?"
        +  // Width
        "(\\d+)?"
        +  // Precision
        "(\\.\\d+)?"
        +  // Conversion. These are all a single character, except date/time
        // conversions
        // which take a prefix of t/T:
        "([tT])?"
        +  // The current set of conversion characters are
        // b,h,s,c,d,o,x,e,f,g,a,t (as well as all those as upper-case
        // characters), plus
        // n for newlines and % as a literal %. And then there are all the
        // time/date
        // characters: HIKLm etc. Just match on all characters here since there
        // should
        // be at least one.
        "([a-zA-Z%])"
    )

    /** Given a format string returns the format type of the given argument  */
    @JvmStatic
    @VisibleForTesting
    fun getFormatArgumentType(s: String, argument: Int): String? {
      val matcher = FORMAT.matcher(s)
      var index = 0
      var prevIndex = 0
      var nextNumber = 1
      while (true) {
        if (matcher.find(index)) {
          val value = matcher.group(6)
          if ("%" == value || "n" == value) {
            index = matcher.end()
            continue
          }
          val matchStart = matcher.start()
          // Make sure this is not an escaped '%'
          while (prevIndex < matchStart) {
            val c = s[prevIndex]
            if (c == '\\') {
              prevIndex++
            }
            prevIndex++
          }
          if (prevIndex > matchStart) {
            // We're in an escape, ignore this result
            index = prevIndex
            continue
          }

          // Shouldn't throw a number format exception since we've already
          // matched the pattern in the regexp
          var number: Int
          var numberString = matcher.group(1)
          if (numberString != null) {
            // Strip off trailing $
            numberString = numberString.substring(0, numberString.length - 1)
            number = numberString.toInt()
            nextNumber = number + 1
          } else {
            number = nextNumber++
          }
          if (number == argument) {
            return matcher.group(6)
          }
          index = matcher.end()
        } else {
          break
        }
      }
      return null
    }

    /**
     * Given a format string returns the number of required arguments. If the `seenArguments`
     * parameter is not null, put the indices of any observed arguments into it.
     */
    @JvmStatic
    fun getFormatArgumentCount(s: String, seenArguments: MutableSet<Int>?): Int {
      val matcher = FORMAT.matcher(s)
      var index = 0
      var prevIndex = 0
      var nextNumber = 1
      var max = 0
      while (true) {
        if (matcher.find(index)) {
          val value = matcher.group(6)
          if ("%" == value || "n" == value) {
            index = matcher.end()
            continue
          }
          val matchStart = matcher.start()
          // Make sure this is not an escaped '%'
          while (prevIndex < matchStart) {
            val c = s[prevIndex]
            if (c == '\\') {
              prevIndex++
            }
            prevIndex++
          }
          if (prevIndex > matchStart) {
            // We're in an escape, ignore this result
            index = prevIndex
            continue
          }

          // Shouldn't throw a number format exception since we've already
          // matched the pattern in the regexp
          var number: Int
          var numberString = matcher.group(1)
          if (numberString != null) {
            // Strip off trailing $
            numberString = numberString.substring(0, numberString.length - 1)
            number = numberString.toInt()
            nextNumber = number + 1
          } else {
            number = nextNumber++
          }
          if (number > max) {
            max = number
          }
          seenArguments?.add(number)
          index = matcher.end()
        } else {
          break
        }
      }
      return max
    }

    /** Given a format string returns whether it has any flags/width/precision modifiers.  */
    fun hasFormatArgumentModifiers(s: String, argument: Int): Boolean {
      val matcher = FORMAT.matcher(s)
      var index = 0
      var prevIndex = 0
      var nextNumber = 1
      while (true) {
        if (matcher.find(index)) {
          val value = matcher.group(6)
          if ("%" == value || "n" == value) {
            index = matcher.end()
            continue
          }
          val matchStart = matcher.start()
          // Make sure this is not an escaped '%'
          while (prevIndex < matchStart) {
            val c = s[prevIndex]
            if (c == '\\') {
              prevIndex++
            }
            prevIndex++
          }
          if (prevIndex > matchStart) {
            // We're in an escape, ignore this result
            index = prevIndex
            continue
          }

          // Shouldn't throw a number format exception since we've already
          // matched the pattern in the regexp
          var number: Int
          var numberString = matcher.group(1)
          if (numberString != null) {
            // Strip off trailing $
            numberString = numberString.substring(0, numberString.length - 1)
            number = numberString.toInt()
            nextNumber = number + 1
          } else {
            number = nextNumber++
          }
          if (number == argument) {
            // The regex for matching flags uses '*', so a format argument with no flags
            // returns "".
            val flags = matcher.group(2)
            val width = matcher.group(3)
            val precision = matcher.group(4)
            return flags != null && flags.length > 0 || width != null && width.length > 0 || precision != null && precision.length > 0
          }
          index = matcher.end()
        } else {
          break
        }
      }
      return false
    }

    /**
     * Determines whether the given [String.format] formatting string is
     * "locale dependent", meaning that its output depends on the locale. This is the case if it for
     * example references decimal numbers of dates and times.
     *
     * @param format the format string
     * @return true if the format is locale sensitive, false otherwise
     */
    @JvmStatic
    fun isLocaleSpecific(format: String): Boolean {
      if (format.indexOf('%') == -1) {
        return false
      }
      val matcher = FORMAT.matcher(format)
      var index = 0
      var prevIndex = 0
      while (true) {
        if (matcher.find(index)) {
          val matchStart = matcher.start()
          // Make sure this is not an escaped '%'
          while (prevIndex < matchStart) {
            val c = format[prevIndex]
            if (c == '\\') {
              prevIndex++
            }
            prevIndex++
          }
          if (prevIndex > matchStart) {
            // We're in an escape, ignore this result
            index = prevIndex
            continue
          }
          val type = matcher.group(6)
          if (!type.isEmpty()) {
            val t = type[0]
            when (t) {
              'd', 'e', 'E', 'f', 'g', 'G', 't', 'T' -> return true
            }
          }
          index = matcher.end()
        } else {
          break
        }
      }
      return false
    }

    /**
     * Checks a String.format call that is using a string that doesn't contain format placeholders.
     *
     * @param context the context to report errors to
     * @param call the AST node for the [String.format]
     * @param name the string name
     * @param handle the string location
     */
    private fun checkNotFormattedHandle(
      context: JavaContext, call: UCallExpression, name: String, handle: Location.Handle?
    ) {
      if (isSuppressed(context, INVALID, handle)) {
        return
      }
      val location = context.getLocation(call)
      val secondary = handle!!.resolve()
      secondary.message = "This definition does not require arguments"
      location.secondary = secondary
      val message = String.format(
        "Format string '`%1\$s`' is not a valid format string so it should not be "
          + "passed to `String.format`",
        name
      )
      context.report(INVALID, call, location, message)
    }

    private fun isCharacterType(type: PsiType): Boolean {
      // return PsiType.CHAR.isAssignableFrom(type);
      if (PsiTypes.charType() == type) {
        return true
      }
      if (type is PsiClassType) {
        val fqn = type.getCanonicalText()
        return TYPE_CHARACTER_WRAPPER == fqn
      }
      return false
    }

    private fun isBooleanType(type: PsiType): Boolean {
      // return PsiType.BOOLEAN.isAssignableFrom(type);
      if (PsiTypes.booleanType() == type) {
        return true
      }
      if (type is PsiClassType) {
        val fqn = type.getCanonicalText()
        return TYPE_BOOLEAN_WRAPPER == fqn
      }
      return false
    }

    // PsiType:java.lang.Boolean
    private fun isNumericType(type: PsiType, allowBigNumbers: Boolean): Boolean {
      if (PsiTypes.intType() == type || PsiTypes.floatType() == type || PsiTypes.doubleType() == type || PsiTypes.longType() == type || PsiTypes.byteType() == type || PsiTypes.shortType() == type) {
        return true
      }
      if (type is PsiClassType) {
        val fqn = type.getCanonicalText()
        if (TYPE_INTEGER_WRAPPER == fqn ||
          TYPE_FLOAT_WRAPPER == fqn ||
          TYPE_DOUBLE_WRAPPER == fqn ||
          TYPE_LONG_WRAPPER == fqn ||
          TYPE_BYTE_WRAPPER == fqn ||
          TYPE_SHORT_WRAPPER == fqn) {
          return true
        }
        if (allowBigNumbers) {
          if ("java.math.BigInteger" == fqn || "java.math.BigDecimal" == fqn) {
            return true
          }
        }
      }
      return false
    }
  }
}
