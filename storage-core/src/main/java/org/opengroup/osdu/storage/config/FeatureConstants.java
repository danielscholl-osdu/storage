// Copyright 2025, Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.config;

/**
 * Constants for feature flag configuration properties.
 */
public final class FeatureConstants {

    /**
     * Configuration property key for controlling feature flag exposure in version info API.
     * When set to true (default), feature flag states are included in the /info endpoint response.
     * When set to false, feature flag states are omitted from the response.
     * When it is missing, default will be true.
     */
    public static final String EXPOSE_FEATUREFLAG_ENABLED_PROPERTY = "expose_featureflag.enabled";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FeatureConstants() {
    }
}