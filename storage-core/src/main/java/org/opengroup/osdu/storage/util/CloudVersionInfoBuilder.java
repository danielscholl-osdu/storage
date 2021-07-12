/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CloudVersionInfoBuilder implements VersionInfoBuilder {

  @Autowired
  private VersionInfoProperties versionInfoProperties;

  private final Properties buildInfoProperties = new Properties();
  private final Properties gitProperties = new Properties();

  public VersionInfo buildVersionInfo() throws IOException {
    loadBuildInfoProperties();
    loadGitProperties();
    return VersionInfo.builder()
        .groupId(buildInfoProperties.getProperty("build.group"))
        .artifactId(buildInfoProperties.getProperty("build.artifact"))
        .version(buildInfoProperties.getProperty("build.version"))
        .buildTime(buildInfoProperties.getProperty("build.time"))
        .branch(gitProperties.getProperty("git.branch"))
        .commitId(gitProperties.getProperty("git.commit.id"))
        .commitMessage(gitProperties.getProperty("git.commit.message.short"))
        .build();
  }

  private void loadBuildInfoProperties() throws IOException {
    InputStream buildInfoStream =
        ClassLoader.getSystemClassLoader()
            .getResourceAsStream(versionInfoProperties.getBuildPropertiesPath());
    if (buildInfoStream != null) {
      buildInfoProperties.load(buildInfoStream);
    } else {
      log.error("Build-info properties file not found by path: {}",
          versionInfoProperties.getBuildPropertiesPath());
    }
  }

  private void loadGitProperties() throws IOException {
    InputStream gitStream = ClassLoader.getSystemClassLoader()
        .getResourceAsStream(versionInfoProperties.getGitPropertiesPath());
    if (gitStream != null) {
      gitProperties.load(gitStream);
    } else {
      log.error("Git properties file not found by path: {}",
          versionInfoProperties.getGitPropertiesPath());
    }
  }
}
