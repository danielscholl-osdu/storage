/*
 *  Copyright 2020-2026 Google LLC
 *  Copyright 2020-2026 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordHeadersDTO {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordHeadersDTO.class);

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneOffset.UTC);

  public static final String ATTRIBUTE_VERSION = "version";
  public static final String ATTRIBUTE_KIND = "kind";
  public static final String ATTRIBUTE_ACL = "acl";
  public static final String ATTRIBUTE_LEGAL = "legal";
  public static final String ATTRIBUTE_ANCESTRY = "ancestry";
  public static final String ATTRIBUTE_TAGS = "tags";
  public static final String ATTRIBUTE_CREATE_USER = "createUser";
  public static final String ATTRIBUTE_CREATE_TIME = "createTime";
  public static final String ATTRIBUTE_MODIFY_USER = "modifyUser";
  public static final String ATTRIBUTE_MODIFY_TIME = "modifyTime";

  public static final List<String> ALL_ATTRIBUTES = List.of(
      ATTRIBUTE_VERSION,
      ATTRIBUTE_KIND,
      ATTRIBUTE_ACL,
      ATTRIBUTE_LEGAL,
      ATTRIBUTE_ANCESTRY,
      ATTRIBUTE_TAGS,
      ATTRIBUTE_CREATE_USER,
      ATTRIBUTE_CREATE_TIME,
      ATTRIBUTE_MODIFY_USER,
      ATTRIBUTE_MODIFY_TIME
  );

  private String id;
  private Long version;
  private String kind;
  private Acl acl;
  private Legal legal;
  private RecordAncestry ancestry;
  private Map<String, String> tags;
  private String createUser;
  private String createTime;
  private String modifyUser;
  private String modifyTime;

  /**
   * Maps and projects an OSDU core-common RecordMetadata instance into RecordHeadersDTO. Dynamic filtering is applied
   * according to the requested attributes list.
   *
   * @param metadata standard core RecordMetadata from DB
   * @param reqAttrs list of requested fields to project (id is always included)
   * @return a mapped and projected RecordHeadersDTO
   */
  public static RecordHeadersDTO from(RecordMetadata metadata, List<String> reqAttrs) {
    if (metadata == null) {
      return null;
    }
    return builderWithMetadata(metadata, reqAttrs).build();
  }

  /**
   * Custom builder method that maps and projects fields from RecordMetadata into RecordHeadersDTOBuilder.
   *
   * @param metadata standard core RecordMetadata from DB
   * @param reqAttrs list of requested fields to project (id is always included)
   * @return a RecordHeadersDTOBuilder pre-populated with projected fields
   */
  public static RecordHeadersDTOBuilder builderWithMetadata(RecordMetadata metadata, List<String> reqAttrs) {
    RecordHeadersDTOBuilder builder = RecordHeadersDTO.builder();
    if (metadata == null) {
      return builder;
    }

    builder.id(metadata.getId()); // ID is always returned

    boolean hasProjection = reqAttrs != null && !reqAttrs.isEmpty();
    Set<String> attrs = hasProjection
        ? reqAttrs.stream().filter(Objects::nonNull).map(String::trim).map(String::toLowerCase).collect(Collectors.toSet())
        : Collections.emptySet();

    if (!hasProjection) {
      fullProjection(metadata, builder);
    } else {
      projectionBasedOnAttributes(metadata, attrs, builder);
    }

    return builder;
  }

  private static void fullProjection(RecordMetadata metadata, RecordHeadersDTOBuilder builder) {
    builder.version(metadata.getLatestVersion())
        .kind(metadata.getKind())
        .acl(metadata.getAcl())
        .legal(metadata.getLegal())
        .ancestry(metadata.getAncestry())
        .tags(metadata.getTags())
        .createUser(metadata.getUser())
        .createTime(formatDateTime(metadata.getCreateTime()))
        .modifyUser(metadata.getModifyUser())
        .modifyTime(formatDateTime(metadata.getModifyTime()));
  }

  private static void projectionBasedOnAttributes(RecordMetadata metadata, Set<String> attrs,
      RecordHeadersDTOBuilder builder) {
    if (attrs.contains(ATTRIBUTE_VERSION.toLowerCase())) {
      builder.version(metadata.getLatestVersion());
    }
    if (attrs.contains(ATTRIBUTE_KIND.toLowerCase())) {
      builder.kind(metadata.getKind());
    }
    if (attrs.contains(ATTRIBUTE_ACL.toLowerCase())) {
      builder.acl(metadata.getAcl());
    }
    if (attrs.contains(ATTRIBUTE_LEGAL.toLowerCase())) {
      builder.legal(metadata.getLegal());
    }
    if (attrs.contains(ATTRIBUTE_ANCESTRY.toLowerCase())) {
      builder.ancestry(metadata.getAncestry());
    }
    if (attrs.contains(ATTRIBUTE_TAGS.toLowerCase())) {
      builder.tags(metadata.getTags());
    }
    if (attrs.contains(ATTRIBUTE_CREATE_USER.toLowerCase())) {
      builder.createUser(metadata.getUser());
    }
    if (attrs.contains(ATTRIBUTE_CREATE_TIME.toLowerCase())) {
      builder.createTime(formatDateTime(metadata.getCreateTime()));
    }
    if (attrs.contains(ATTRIBUTE_MODIFY_USER.toLowerCase())) {
      builder.modifyUser(metadata.getModifyUser());
    }
    if (attrs.contains(ATTRIBUTE_MODIFY_TIME.toLowerCase())) {
      builder.modifyTime(formatDateTime(metadata.getModifyTime()));
    }
  }


  private static String formatDateTime(long epochTime) {
    if (epochTime == 0) {
      return null;
    }
    try {
      return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochTime));
    } catch (Exception e) {
      LOGGER.warn("Failed to format epoch time: {}", epochTime, e);
      return null;
    }
  }
}
