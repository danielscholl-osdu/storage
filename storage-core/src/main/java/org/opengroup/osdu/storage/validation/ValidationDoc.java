// Copyright 2017-2023, Schlumberger
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

package org.opengroup.osdu.storage.validation;

public class ValidationDoc {
    public static final String PATCH_RECORD_OPERATIONS_NOT_EMPTY = "Record patch operations cannot be empty";
    public static final String INVALID_PATCH_PATH_START = "Invalid Patch Path: can only start with '/acl/viewers', 'acl/owners', '/legal/legaltags', '/tags', '/kind', '/ancestry/parents', '/data' or '/meta'";
    public static final String INVALID_PATCH_OPERATION = "Invalid Patch Operation: can only be 'replace' or 'add' or 'remove'";
    public static final String INVALID_PATCH_OPERATION_SIZE = "Invalid Patch Operation: the number of operations can only be between 1 and 100";
    public static final String INVALID_PATCH_PATH_FOR_REMOVE_OPERATION = "Invalid Patch Path: path for remove operation must contain index of the value to be deleted";
    public static final String INVALID_PATCH_PATH_END = "Invalid Patch Path: path cannot end with '/'";
    public static final String INVALID_PATCH_OPERATION_TYPE_FOR_KIND = "Invalid Patch Operation: for patching '/kind', only 'replace' operation is allowed";
    public static final String INVALID_PATCH_PATH_FOR_KIND = "Invalid Patch Path: for patching kind, path must be exactly '/kind'";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_KIND = "Invalid Patch Operation: for patching '/kind', only one value is allowed";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_TAGS = "Invalid Patch Operation: for patching '/tags', value can only be in {'key':'value'} format and for patching '/tags/key' value can only be in a single string format";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_ACL_LEGAL_ANCESTRY = "Invalid Patch Operation: for patching '/acl/viewers', 'acl/owners', '/legal/legaltags', '/ancestry/parents'  value can only be in an array format";
    public static final String INVALID_PATCH_SINGLE_VALUE_FORMAT_FOR_ACL_LEGAL_ANCESTRY = "Invalid Patch Operation: for patching '/acl/viewers/<index>', 'acl/owners/<index>', '/legal/legaltags/<index>', '/ancestry/parents/<index>'  value can only be in a single string format";
    public static final String INVALID_PATCH_VALUE_FORMAT_FOR_META = "Invalid Patch Operation: for patching '/meta' value can only be in an array format";
    public static final String KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION = "Invalid kind: '%s', does not follow the required naming convention";
    public static final String RECORD_ID_LIST_NOT_EMPTY = "The list of record IDs cannot be empty";
    public static final String PATCH_RECORDS_MAX = "Up to 100 records can be patched at a time";
    public static final String INVALID_RECORD_ID_PATCH = "Invalid record format: '%s'. The following format is expected: {tenant-name}:{object-type}:{unique-identifier}";
    public static final String INVALID_VERSION_IDS_SIZE = "Invalid Version Ids size : '%d'. The number of versionId can only be between 1 and 50";
    public static final String INVALID_VERSION_IDS_FOR_LATEST_VERSION = "Invalid Version Ids. The versionIds contains latest record version '%d'";
    public static final String INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS = "Invalid Version Ids. The versionIds contains non existing version(s) '%s'";
    public static final String INVALID_LIMIT = "Invalid limit.";
    public static final String INVALID_FROM_VERSION = "Invalid 'from' version.";
    public static final String INVALID_FROM_VERSION_FOR_LATEST_VERSION = INVALID_FROM_VERSION+" The 'from' should not be latest record version '%d'";
    public static final String INVALID_FROM_VERSION_FOR_NON_EXISTING_VERSIONS = INVALID_FROM_VERSION+" The record version does not contains specified from version '%d'";
    public static final String INVALID_LIMIT_FOR_FROM_VERSION = INVALID_LIMIT+" Given limit count %d, exceeds the record versions count specified by the given 'from' version '%d'";
}
