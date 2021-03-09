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

import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import com.google.gson.Gson;

@RunWith(MockitoJUnitRunner.class)
public class RecordUtilImplTest {

  private static final String ACL_VIEWER = "viewer1@tenant1.gmail.com";
  private static final String ACL_VIEWER_NEW = "newviewer1@tenant1.gmail.com";

  private static final String PATH_TAGS = "/tags";
  private static final String PATH_ACL_VIEWERS = "/acl/viewers";

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
  public void updateRecordMetaDataForPatchOperations_shouldUpdateForNonTags_withReplaceOperation() {
    RecordMetadata recordMetadata = buildRecordMetadata();
    PatchOperation patchOperation = buildPatchOperation(PATH_ACL_VIEWERS, PATCH_OPERATION_REPLACE,
        ACL_VIEWER_NEW);

    RecordMetadata updatedMetadata = recordUtil
        .updateRecordMetaDataForPatchOperations(recordMetadata, singletonList(patchOperation), TEST_USER,
            TIMESTAMP);

    assertEquals(ACL_VIEWER_NEW, updatedMetadata.getAcl().getViewers()[0]);
  }

  private PatchOperation buildPatchOperation(String path, String operation, String... value) {
    return PatchOperation.builder().path(path).op(operation).value(value).build();
  }

  private RecordMetadata buildRecordMetadata() {
    Acl acl = new Acl();
    String[] viewers = new String[]{ACL_VIEWER};
    acl.setViewers(viewers);

    RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata.setAcl(acl);
    recordMetadata.getTags().put(TAG_KEY, TAG_VALUE);
    return recordMetadata;
  }
}