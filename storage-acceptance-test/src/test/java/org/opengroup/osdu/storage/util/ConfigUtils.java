/*
 * Copyright 2020  Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigUtils {

    private final Properties properties;

    public ConfigUtils(String propertiesFileName) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(propertiesFileName)) {
            properties = new Properties();
            // load a properties file
            properties.load(input);
        }
    }

    public boolean getBooleanProperty(String propertyName, String defaultValue) {
        String propValue = properties.getProperty(propertyName, defaultValue);
        String envValue = getEnvValue(propertyName);
        return (envValue == null || envValue.isEmpty()) ? Boolean.parseBoolean(propValue)
            : Boolean.parseBoolean(envValue);
    }

    public long getLongProperty(String propertyName, String defaultValue) {
        String propValue = properties.getProperty(propertyName, defaultValue);
        String envValue = getEnvValue(propertyName);
        return (envValue == null || envValue.isEmpty()) ? Long.parseLong(propValue)
            : Long.parseLong(envValue);
    }

    public boolean getIsSchemaEndpointsEnabled() {
        return !getBooleanProperty("schema.endpoints.disabled", "true");
    }
    public boolean getIsCollaborationEnabled() {
        return getBooleanProperty("collaboration.enabled", "false");
    }

    public boolean isTestReplayEnabled(){ return  getBooleanProperty("test.replay.enabled", "false");}

    public boolean getIsTestReplayAllEnabled() { return  getBooleanProperty("test.replayAll.enabled", "false");}

    public long getTimeoutForReplay() { return  getLongProperty("test.replayAll.timeout", "60");}

    private static String getEnvValue(String propertyName) {
        return System.getenv(propertyName.toUpperCase().replaceAll("\\.", "_"));
    }

}
