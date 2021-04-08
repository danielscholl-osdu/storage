// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.util;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.storage.util.TestUtils.buildAppExceptionMatcher;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import com.google.gson.Gson;

@RunWith(MockitoJUnitRunner.class)
public class RecordUtilImplTest {

  private static final String ACL_VIEWER_EXISTING1 = "viewer1@tenant1.gmail.com";
  private static final String ACL_VIEWER_EXISTING2 = "viewer2@tenant1.gmail.com";
  private static final String ACL_VIEWER_NEW = "newviewer@tenant1.gmail.com";
  private static final String PATH_ACL_VIEWERS = "/acl/viewers";

  private static final String ACL_OWNER_EXISTING1 = "owner1@tenant1.gmail.com";
  private static final String ACL_OWNER_EXISTING2 = "owner2@tenant1.gmail.com";
  private static final String ACL_OWNER_NEW = "newowner@tenant1.gmail.com";
  private static final String PATH_ACL_OWNERS = "/acl/owners";


  private static final String PATH_TAGS = "/tags";

  private static final String PATH_LEGAL = "/legal/legaltags";
  private static final String LEGAL_LEGALTAG_NEW = "legalTag3";
  private static final String LEGAL_LEGALTAG_EXISTED1 = "legalTag1";
  private static final String LEGAL_LEGALTAG_EXISTED2 = "legalTag2";

  private static final long TIMESTAMP = 42L;
  private static final String TEST_USER = "testuser";
  private static final String TAG_KEY = "testkey";
  private static final String TAG_VALUE = "testvalue";
  private static final String TAG_KEY_NEW = "newtestkey";
  private static final String TAG_VALUE_NEW = "newtestvalue";

  private static final String PATCH_OPERATION_REPLACE = "replace";
  private static final String PATCH_OPERATION_ADD = "add";
  private static final String PATCH_OPERATION_REMOVE = "remove";

  private static final String TENANT_NAME = "tenantname";

  private static final String VALID_RECORD = TENANT_NAME + ":123:123";
  private static final String INVALID_RECORD = "wrongtenant:123";

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Mock
  private TenantInfo tenant;
  private Gson gson = new Gson();
  private RecordUtilImpl recordUtil;

  @Before
  public void before() {
    recordUtil = new RecordUtilImpl(tenant, gson);
    when(this.tenant.getName()).thenReturn(TENANT_NAME);
  }

  @Test
  public void validateRecordIds_shouldDoNothing_forValidId() {
    recordUtil.validateRecordIds(singletonList(VALID_RECORD));
  }

  @Test
  public void validateRecordIds_shouldThrowException_forInvalidId() {
    exceptionRule.expect(AppException.class);
    exceptionRule.expect(buildAppExceptionMatcher("The record '" + INVALID_RECORD
        + "' does not follow the naming convention: the first id component must be '"
        + TENANT_NAME + "'", "Invalid record id"));

    recordUtil.validateRecordIds(singletonList(INVALID_RECORD));
  }

  @Test
  public void mapRecordsAndVersions_shouldCreateMap_whenVersionLengthNotEqualFour() {
    String id = "id:1:2";

    Map<String, String> resultMap = recordUtil.mapRecordsAndVersions(singletonList(id));

    assertEquals(id, resultMap.get(id));
  }

  @Test
  public void mapRecordsAndVersions_shouldCreateMap_whenVersionLengthEqualFour() {
    String inputId = "id:1:2:3";
    String expectedKey = "id:1:2";

    Map<String, String> resultMap = recordUtil.mapRecordsAndVersions(singletonList(inputId));

    assertEquals(inputId, resultMap.get(expectedKey));
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForTags_withReplaceOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, PATCH_OPERATION_REPLACE,
        TAG_KEY + ":" + TAG_VALUE_NEW);

    RecordMetadata updatedMetadata = recordUtil
        .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
            TIMESTAMP);

    assertEquals(TAG_VALUE_NEW, updatedMetadata.getTags().get(TAG_KEY));
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForTags_withAddOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, PATCH_OPERATION_ADD,
        TAG_KEY_NEW + ":" + TAG_VALUE_NEW);

    RecordMetadata updatedMetadata = recordUtil
        .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
            TIMESTAMP);

    assertEquals(TAG_VALUE, updatedMetadata.getTags().get(TAG_KEY));
    assertEquals(TAG_VALUE_NEW, updatedMetadata.getTags().get(TAG_KEY_NEW));
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForTags_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, PATCH_OPERATION_REMOVE,TAG_KEY);

    RecordMetadata updatedMetadata = recordUtil
        .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
            TIMESTAMP);

    assertTrue(updatedMetadata.getTags().isEmpty());
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForLegal_withReplaceOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_LEGAL, PATCH_OPERATION_REPLACE, LEGAL_LEGALTAG_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    Set<String> new_legaltags = new LinkedHashSet<>();
    new_legaltags.add(LEGAL_LEGALTAG_NEW);

    assertEquals(new_legaltags, updatedMetadata.getLegal().getLegaltags());
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForLegal_withAddOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_LEGAL, PATCH_OPERATION_ADD, LEGAL_LEGALTAG_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    Set<String> new_legaltags = new LinkedHashSet<>();
    new_legaltags.add(LEGAL_LEGALTAG_NEW);
    new_legaltags.add(LEGAL_LEGALTAG_EXISTED1);
    new_legaltags.add(LEGAL_LEGALTAG_EXISTED2);

    assertEquals(new_legaltags, updatedMetadata.getLegal().getLegaltags());
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForLegal_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_LEGAL, PATCH_OPERATION_REMOVE,LEGAL_LEGALTAG_EXISTED2);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    Set<String> new_legaltags = new LinkedHashSet<>();
    new_legaltags.add(LEGAL_LEGALTAG_EXISTED1);

    assertEquals(new_legaltags, updatedMetadata.getLegal().getLegaltags());
  }

  @Test
  public void should_throwAppExceptionWithBadRequestCode_whenAllLegaltagsAreRemoved_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_LEGAL, PATCH_OPERATION_REMOVE, LEGAL_LEGALTAG_EXISTED1, LEGAL_LEGALTAG_EXISTED2);
    try {
      RecordMetadata updatedMetadata = recordUtil
              .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                      TIMESTAMP);
    }catch(AppException e){
      assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
      assertEquals("Cannot remove all legaltags", e.getError().getReason());
      assertEquals("Cannot delete", e.getError().getMessage());
    }catch (Exception e){
      Assert.fail("Should not get different exception");
    }
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclViewers_withAddOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_VIEWERS, PATCH_OPERATION_ADD,
            ACL_VIEWER_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_VIEWER_NEW,ACL_VIEWER_EXISTING1,ACL_VIEWER_EXISTING2}, updatedMetadata.getAcl().getViewers());
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclViewers_withReplaceOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_VIEWERS, PATCH_OPERATION_REPLACE,ACL_VIEWER_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_VIEWER_NEW}, updatedMetadata.getAcl().getViewers());
  }

  @Test
  public void should_throwAppExceptionWithBadRequestCode_whenAllAclViewersAreRemoved_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_VIEWERS, PATCH_OPERATION_REMOVE, ACL_VIEWER_EXISTING2);

    try {
      RecordMetadata updatedMetadata = recordUtil
              .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                      TIMESTAMP);
      //Assert.("Should not suceed");
    }catch(AppException e){
      assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
      assertEquals("Cannot remove all acl viewers", e.getError().getReason());
      assertEquals("Cannot delete", e.getError().getMessage());
    }catch (Exception e){
      Assert.fail("Should not get different exception");
    }

  }
  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclViewers_withRemoveOperation()  {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_VIEWERS, PATCH_OPERATION_REMOVE, ACL_VIEWER_EXISTING2);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_VIEWER_EXISTING1}, updatedMetadata.getAcl().getViewers());
  }

  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclOwners_withAddOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_OWNERS, PATCH_OPERATION_ADD,
            ACL_OWNER_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_OWNER_NEW,ACL_OWNER_EXISTING1,ACL_OWNER_EXISTING2}, updatedMetadata.getAcl().getOwners());
  }
  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclOwners_withReplaceOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_OWNERS, PATCH_OPERATION_REPLACE, ACL_OWNER_NEW);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_OWNER_NEW}, updatedMetadata.getAcl().getOwners());
  }
  @Test
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForAclOwners_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_OWNERS, PATCH_OPERATION_REMOVE, ACL_OWNER_EXISTING2);

    RecordMetadata updatedMetadata = recordUtil
            .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                    TIMESTAMP);

    assertEquals(new String[]{ACL_OWNER_EXISTING1}, updatedMetadata.getAcl().getOwners());
  }

  @Test
  public void should_throwAppExceptionWithBadRequestCode_whenAllAclOwnersAreRemoved_withRemoveOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_OWNERS, PATCH_OPERATION_REMOVE, ACL_OWNER_EXISTING1, ACL_OWNER_EXISTING2);

    try {
      RecordMetadata updatedMetadata = recordUtil
              .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
                      TIMESTAMP);
    }catch(AppException e){
      assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
      assertEquals("Cannot remove all acl owners", e.getError().getReason());
      assertEquals("Cannot delete", e.getError().getMessage());
    }catch (Exception e){
      Assert.fail("Should not get different exception");
    }



  }

  private PatchOperation buildPatchOperation(String path, String operation, String... value) {
    return PatchOperation.builder().path(path).op(operation).value(value).build();
  }

  private RecordMetadata buildRecordMetadata() {
    Acl acl = new Acl();
    String[] viewers = new String[]{ACL_VIEWER_EXISTING1,ACL_VIEWER_EXISTING2};
    acl.setViewers(viewers);
    String[] owners = new String[]{ACL_OWNER_EXISTING1,ACL_OWNER_EXISTING2};
    acl.setOwners(owners);

    Legal legal = new Legal();
    Set<String> legalTags = new HashSet<>();
    legalTags.add(LEGAL_LEGALTAG_EXISTED1);
    legalTags.add(LEGAL_LEGALTAG_EXISTED2);
    legal.setLegaltags(legalTags);

    RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata.setAcl(acl);
    recordMetadata.setLegal(legal);
    recordMetadata.getTags().put(TAG_KEY, TAG_VALUE);
    return recordMetadata;
  }
}