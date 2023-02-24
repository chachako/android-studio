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

package com.android.tools.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashSet;
import java.util.Set;

/** Utility for reading gradle module metadata file */
public final class GradleMetadataJsonReader {

    private GradleMetadataJsonReader() {}

    /** Parse gradle module file json content into a set of url for each variant */
    public static Set<String> readVariantUrls(String input) throws Exception {
        Set<String> urls = new HashSet<>();
        JsonNode rootNode = getMapper().readTree(input);
        ArrayNode gradleVariants = (ArrayNode) rootNode.get("variants");
        if (gradleVariants != null && gradleVariants.isArray()) {
            for (final JsonNode variant : gradleVariants) {
                ArrayNode files = (ArrayNode) variant.get("files");
                if (files != null && files.isArray()) {
                    for (final JsonNode file : files) {
                        urls.add(file.get("url").asText());
                    }
                }
            }
        }
        return urls;
    }

    private static ObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper;
    }
}
