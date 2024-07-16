/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.lint

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_XML

/** Strips line and block comments from the given Java, Kotlin or Groovy source file. */
fun stripComments(source: String, extension: String, stripLineComments: Boolean = true): String {
  return when (extension) {
    DOT_KT,
    DOT_KTS,
    DOT_JAVA,
    DOT_GRADLE -> stripJavaLikeComments(source, extension, stripLineComments)
    DOT_XML -> stripXmlComments(source)
    else -> source
  }
}

@Suppress("LocalVariableName")
private fun stripJavaLikeComments(
  source: String,
  extension: String,
  stripLineComments: Boolean = true,
): String {
  val sb = StringBuilder(source.length)
  var state = 0
  val INIT = 0
  val INIT_SLASH = 1
  val LINE_COMMENT = 2
  val BLOCK_COMMENT = 3
  val BLOCK_COMMENT_ASTERISK = 4
  val BLOCK_COMMENT_SLASH = 5
  val IN_STRING = 6
  val IN_STRING_ESCAPE = 7
  val IN_CHAR = 8
  val AFTER_CHAR = 9
  var blockCommentDepth = 0
  for (c in source) {
    when (state) {
      INIT -> {
        when (c) {
          '/' -> state = INIT_SLASH
          '"' -> {
            state = IN_STRING
            sb.append(c)
          }
          '\'' -> {
            state = IN_CHAR
            sb.append(c)
          }
          else -> sb.append(c)
        }
      }
      INIT_SLASH -> {
        when {
          c == '*' -> {
            blockCommentDepth++
            state = BLOCK_COMMENT
          }
          c == '/' && stripLineComments -> state = LINE_COMMENT
          else -> {
            state = INIT
            sb.append('/') // because we skipped it in init
            sb.append(c)
          }
        }
      }
      LINE_COMMENT -> {
        when (c) {
          '\n' -> {
            state = INIT
            // Don't consume the \n unless it's a blank only line
            var i = sb.length - 1
            while (i >= 0) {
              val ch = sb[i]
              if (ch == '\n') {
                sb.setLength(i + 1)
                break
              } else if (!ch.isWhitespace()) {
                sb.setLength(i + 1)
                sb.append('\n')
                break
              }
              i--
            }
          }
        }
      }
      BLOCK_COMMENT -> {
        when (c) {
          '*' -> state = BLOCK_COMMENT_ASTERISK
          '/' -> state = BLOCK_COMMENT_SLASH
        }
      }
      BLOCK_COMMENT_ASTERISK -> {
        state =
          when (c) {
            '/' -> {
              blockCommentDepth--
              if (blockCommentDepth == 0) {
                INIT
              } else {
                BLOCK_COMMENT
              }
            }
            '*' -> BLOCK_COMMENT_ASTERISK
            else -> BLOCK_COMMENT
          }
      }
      BLOCK_COMMENT_SLASH -> {
        if (c == '*' && (extension == DOT_KT || extension == DOT_KTS)) {
          blockCommentDepth++
        }
        if (c != '/') {
          state = BLOCK_COMMENT
        }
      }
      IN_STRING -> {
        when (c) {
          '\\' -> state = IN_STRING_ESCAPE
          '"' -> state = INIT
        }
        sb.append(c)
      }
      IN_STRING_ESCAPE -> {
        sb.append(c)
        state = IN_STRING
      }
      IN_CHAR -> {
        if (c != '\\') {
          state = AFTER_CHAR
        }
        sb.append(c)
      }
      AFTER_CHAR -> {
        sb.append(c)
        if (c == '\'') {
          state = INIT
        }
      }
    }
  }

  return sb.toString()
}

/** Strips comments from the XML source */
fun stripXmlComments(source: String): String {
  val sb = StringBuilder()
  var i = 0
  while (i < source.length) {
    var commentStart = source.indexOf("<!--", i)
    if (commentStart == -1) {
      break
    }
    val commentEnd = source.indexOf("-->", i + 4)
    if (commentEnd == -1) {
      break
    }

    var foundLineStart = false
    while (commentStart > 0) {
      val ch = source[commentStart - 1]
      if (ch == '\n') {
        foundLineStart = true
        break
      } else if (!ch.isWhitespace()) {
        break
      } else {
        commentStart--
      }
    }

    sb.append(source.substring(i, commentStart))
    i = commentEnd + 3
    if (foundLineStart) {
      // See if this line ends with whitespace; if so, strip the whole line
      var j = i
      while (j < source.length) {
        val ch = source[j]
        if (ch == '\n') {
          i = j + 1
          break
        } else if (!ch.isWhitespace()) {
          break
        }
        j++
      }
    }
  }
  sb.append(source.substring(i, source.length))
  return sb.toString()
}
