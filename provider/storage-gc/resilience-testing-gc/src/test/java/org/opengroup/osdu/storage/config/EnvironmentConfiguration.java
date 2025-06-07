/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.config;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.storage.util.FileLoadUtil;

@Slf4j
public class EnvironmentConfiguration {

  public static final String STORAGE_API = "/api/storage/v2/records";
  public static final String STORAGE_API_BATCH = "/api/storage/v2/query/records:batch";
  public static final String ACL_VIEWERS = "data.default.viewers@%s.%s";
  public static final String ACL_OWNERS = "data.default.owners@%s.%s";

  private final String projectId;
  private final String tenant;
  private final String namespace;
  private final String kind;
  private final String databaseId;
  private final String entitlementsDomain;
  private final String legaTag;
  private final String osduHost;

  public EnvironmentConfiguration() {
    Properties properties = FileLoadUtil.loadPropertiesFromFile("test.properties");

    projectId = properties.getProperty("project.id", System.getenv("PROJECT_ID"));
    osduHost = properties.getProperty("osdu.host", System.getenv("OSDU_HOST"));
    tenant = properties.getProperty("tenant", System.getenv("TENANT"));
    namespace = properties.getProperty("namespace", System.getenv("NAMESPACE"));
    kind = properties.getProperty("kind", System.getenv("KIND"));
    databaseId = properties.getProperty("database.id", System.getenv("DATABASE_ID"));
    entitlementsDomain = properties.getProperty("entitlements.domain", System.getenv("ENTITLEMENTS_DOMAIN"));
    legaTag = properties.getProperty("legal.tag", System.getenv("LEGAL_TAG"));

    String envInfo = """
        The environment is configured with the following values:
        - PROJECT_ID: {}
        - OSDU_HOST: {}
        - TENANT: {}
        - NAMESPACE: {}
        - KIND: {}
        - DATABASE_ID: {}
        - ENTITLEMENTS_DOMAIN: {}
        - LEGAL_TAG: {}
        """;

    log.info(envInfo, projectId, osduHost, tenant, namespace, kind, databaseId, entitlementsDomain, legaTag);
  }

  public String getProjectId() {
    return projectId;
  }

  public String getTenant() {
    return tenant;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getKind() {
    return kind;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String getEntitlementsDomain() {
    return entitlementsDomain;
  }

  public String getLegaTag() {
    return legaTag;
  }

  public String getOsduHost() {
    return osduHost;
  }
}
