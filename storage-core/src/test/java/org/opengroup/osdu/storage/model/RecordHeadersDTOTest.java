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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

class RecordHeadersDTOTest {

  public static final String TEST_ID = "tenant1:test:123";
  public static final String TEST_KIND = "tenant1:test:kind:1.0.0";
  public static final long TEST_VERSION = 1L;
  public static final String TEST_CREATOR_USER = "creator-user";
  public static final String TEST_MODIFIER_USER = "modifier-user";
  public static final String TEST_CREATED_TIME = "2020-09-13T12:26:40.000Z";
  public static final String TEST_MODIFIED_TIME = "2020-09-13T15:26:40.000Z";

  @Test
  void should_returnNull_when_metadataIsNull() {
    assertNull(RecordHeadersDTO.from(null, null));
    assertNotNull(RecordHeadersDTO.builderWithMetadata(null, null).build());
  }

  @Test
  void should_returnAllFields_when_attributesListIsNull() {
    RecordMetadata metadata = createDummyMetadata();
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, null);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertEquals(TEST_VERSION, dto.getVersion());
    assertEquals(TEST_KIND, dto.getKind());
    assertNotNull(dto.getAcl());
    assertNotNull(dto.getLegal());
    assertNotNull(dto.getAncestry());
    assertNotNull(dto.getTags());
    assertEquals(TEST_CREATOR_USER, dto.getCreateUser());
    assertEquals(TEST_CREATED_TIME, dto.getCreateTime());
    assertEquals(TEST_MODIFIER_USER, dto.getModifyUser());
    assertEquals(TEST_MODIFIED_TIME, dto.getModifyTime());
  }

  @Test
  void should_returnAllFields_when_attributesListIsEmpty() {
    RecordMetadata metadata = createDummyMetadata();
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, Collections.emptyList());

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertEquals(TEST_VERSION, dto.getVersion());
    assertEquals(TEST_KIND, dto.getKind());
    assertNotNull(dto.getAcl());
    assertNotNull(dto.getLegal());
    assertNotNull(dto.getAncestry());
    assertNotNull(dto.getTags());
    assertEquals(TEST_CREATOR_USER, dto.getCreateUser());
    assertEquals(TEST_CREATED_TIME, dto.getCreateTime());
    assertEquals(TEST_MODIFIER_USER, dto.getModifyUser());
    assertEquals(TEST_MODIFIED_TIME, dto.getModifyTime());
  }

  @Test
  void should_returnOnlyId_when_attributesListHasNoRecognizedAttributes() {
    RecordMetadata metadata = createDummyMetadata();
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, List.of("non-existent-field"));

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertNull(dto.getVersion());
    assertNull(dto.getKind());
    assertNull(dto.getAcl());
    assertNull(dto.getLegal());
    assertNull(dto.getAncestry());
    assertNull(dto.getTags());
    assertNull(dto.getCreateUser());
    assertNull(dto.getCreateTime());
    assertNull(dto.getModifyUser());
    assertNull(dto.getModifyTime());
  }

  @Test
  void should_returnRequestedFields_when_specificAttributesAreGiven() {
    RecordMetadata metadata = createDummyMetadata();
    List<String> attributes = RecordHeadersDTO.ALL_ATTRIBUTES;
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, attributes);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertEquals(TEST_VERSION, dto.getVersion());
    assertEquals(TEST_KIND, dto.getKind());
    assertNotNull(dto.getAcl());
    assertNotNull(dto.getLegal());
    assertNotNull(dto.getAncestry());
    assertNotNull(dto.getTags());
    assertEquals(TEST_CREATOR_USER, dto.getCreateUser());
    assertEquals(TEST_CREATED_TIME, dto.getCreateTime());
    assertEquals(TEST_MODIFIER_USER, dto.getModifyUser());
    assertEquals(TEST_MODIFIED_TIME, dto.getModifyTime());
  }

  @Test
  void should_returnNullTime_when_epochTimeIsZero() {
    RecordMetadata metadata = createDummyMetadata();
    metadata.setCreateTime(0);
    metadata.setModifyTime(0);

    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, null);
    assertNotNull(dto);
    assertNull(dto.getCreateTime());
    assertNull(dto.getModifyTime());
  }

  @Test
  void should_handleCaseInsensitivityAndTrimmingOfAttributes_when_mixedCaseWithWhitespaceAreGiven() {
    RecordMetadata metadata = createDummyMetadata();
    List<String> attributes = Arrays.asList("  VERSION  ", " KiNd", "aCl ");
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, attributes);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertEquals(TEST_VERSION, dto.getVersion());
    assertEquals(TEST_KIND, dto.getKind());
    assertNotNull(dto.getAcl());
    assertNull(dto.getLegal());
    assertNull(dto.getAncestry());
    assertNull(dto.getTags());
    assertNull(dto.getCreateUser());
    assertNull(dto.getCreateTime());
    assertNull(dto.getModifyUser());
    assertNull(dto.getModifyTime());
  }

  @Test
  void should_notThrowNpe_when_attributesListContainsNull() {
    RecordMetadata metadata = createDummyMetadata();
    List<String> attributes = Arrays.asList("version", null, "kind");
    RecordHeadersDTO dto = RecordHeadersDTO.from(metadata, attributes);

    assertNotNull(dto);
    assertEquals(TEST_ID, dto.getId());
    assertEquals(TEST_VERSION, dto.getVersion());
    assertEquals(TEST_KIND, dto.getKind());
    assertNull(dto.getAcl());
  }

  @Test
  void should_testEqualsAndHashCode_forDTOs() {
    RecordHeadersDTO dto1 = RecordHeadersDTO.builder()
        .id("id1")
        .version(TEST_VERSION)
        .kind("kind1")
        .build();

    RecordHeadersDTO dto2 = RecordHeadersDTO.builder()
        .id("id1")
        .version(TEST_VERSION)
        .kind("kind1")
        .build();

    RecordHeadersDTO dto3 = RecordHeadersDTO.builder()
        .id("id2")
        .version(2L)
        .kind("kind2")
        .build();

    assertEquals(dto1, dto2);
    assertNotEquals(dto1, dto3);
    assertEquals(dto1.hashCode(), dto2.hashCode());
    assertNotEquals(dto1.hashCode(), dto3.hashCode());
    assertNotEquals(null, dto1);
    assertNotEquals("string", dto1);

    MultiRecordHeadersRequest req1 = MultiRecordHeadersRequest.builder()
        .records(Collections.singletonList("id1"))
        .attributes(Collections.singletonList("attr1"))
        .build();

    MultiRecordHeadersRequest req2 = MultiRecordHeadersRequest.builder()
        .records(Collections.singletonList("id1"))
        .attributes(Collections.singletonList("attr1"))
        .build();

    MultiRecordHeadersRequest req3 = MultiRecordHeadersRequest.builder()
        .records(Collections.singletonList("id2"))
        .attributes(Collections.singletonList("attr2"))
        .build();

    assertEquals(req1, req2);
    assertNotEquals(req1, req3);
    assertEquals(req1.hashCode(), req2.hashCode());
    assertNotEquals(req1.hashCode(), req3.hashCode());

    MultiRecordHeadersInfo info1 = MultiRecordHeadersInfo.builder()
        .records(Collections.singletonList(dto1))
        .notFound(Collections.singletonList("nf1"))
        .invalidRecords(Collections.singletonList("inv1"))
        .build();

    MultiRecordHeadersInfo info2 = MultiRecordHeadersInfo.builder()
        .records(Collections.singletonList(dto1))
        .notFound(Collections.singletonList("nf1"))
        .invalidRecords(Collections.singletonList("inv1"))
        .build();

    MultiRecordHeadersInfo info3 = MultiRecordHeadersInfo.builder()
        .records(Collections.singletonList(dto3))
        .notFound(Collections.singletonList("nf2"))
        .invalidRecords(Collections.singletonList("inv2"))
        .build();

    assertEquals(info1, info2);
    assertNotEquals(info1, info3);
    assertEquals(info1.hashCode(), info2.hashCode());
    assertNotEquals(info1.hashCode(), info3.hashCode());
  }

  @Test
  void should_testLombokConstructorAndBuilder_forCoverage() {
    RecordHeadersDTO simple = new RecordHeadersDTO();
    simple.setId("id");
    assertEquals("id", simple.getId());

    RecordHeadersDTO allArgs = new RecordHeadersDTO("id", TEST_VERSION, "kind", new Acl(), new Legal(),
        new RecordAncestry(), Collections.emptyMap(), "create", "ctime", "mod", "mtime");
    assertEquals("id", allArgs.getId());
    assertEquals(TEST_VERSION, allArgs.getVersion());

    String toString = allArgs.toString();
    assertNotNull(toString);
    assertTrue(toString.contains("id"));

    MultiRecordHeadersRequest req = new MultiRecordHeadersRequest();
    req.setRecords(Collections.singletonList("id"));
    req.setAttributes(Collections.singletonList("attr"));
    assertEquals("id", req.getRecords().get(0));
    assertEquals("attr", req.getAttributes().get(0));

    MultiRecordHeadersRequest reqAllArgs = new MultiRecordHeadersRequest(
        Collections.singletonList("id"),
        Collections.singletonList("attr")
    );
    assertNotNull(reqAllArgs.toString());

    MultiRecordHeadersInfo info = new MultiRecordHeadersInfo();
    info.setRecords(Collections.singletonList(simple));
    info.setNotFound(Collections.singletonList("notfound"));
    info.setInvalidRecords(Collections.singletonList("invalid"));

    assertEquals(1, info.getRecords().size());
    assertEquals(1, info.getNotFound().size());
    assertEquals(1, info.getInvalidRecords().size());

    MultiRecordHeadersInfo infoAllArgs = new MultiRecordHeadersInfo(
        Collections.singletonList(simple),
        Collections.singletonList("notfound"),
        Collections.singletonList("invalid")
    );
    assertNotNull(infoAllArgs.toString());
  }

  private RecordMetadata createDummyMetadata() {
    Acl acl = new Acl();
    acl.setOwners(new String[]{"owner1"});
    acl.setViewers(new String[]{"viewer1"});

    Legal legal = new Legal();
    legal.setLegaltags(Collections.singleton("legal1"));

    RecordAncestry ancestry = new RecordAncestry();
    ancestry.setParents(Set.of("parent1"));

    Map<String, String> tags = new HashMap<>();
    tags.put("tagKey", "tagVal");

    RecordMetadata metadata = new RecordMetadata();
    metadata.setId(TEST_ID);
    metadata.setKind(TEST_KIND);
    metadata.setAcl(acl);
    metadata.setLegal(legal);
    metadata.setAncestry(ancestry);
    metadata.setTags(tags);
    metadata.setUser(TEST_CREATOR_USER);
    metadata.setCreateTime(1600000000000L); // 2020-09-13T12:26:40.000Z
    metadata.setModifyUser(TEST_MODIFIER_USER);
    metadata.setModifyTime(1600010800000L); // 2020-09-13T15:26:40.000Z
    metadata.setGcsVersionPaths(List.of("1"));

    return metadata;
  }
}
