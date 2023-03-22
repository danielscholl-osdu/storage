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
    public static final String INVALID_PATCH_PATH_START = "Invalid Patch Path: can only starts with '/acl/viewers', 'acl/owners', '/legal/legaltags', '/tags', '/kind', '/ancestry/parents', '/data' or '/meta'";
    public static final String INVALID_PATCH_OPERATION = "Invalid Patch Operation: can only be 'replace' or 'add' or 'remove'";
    public static final String INVALID_PATCH_OPERATION_SIZE = "Invalid Patch Operation: the number of operations can only be between 1 and 100";
    public static final String INVALID_PATCH_PATH_FOR_ADD_OPERATION = "Invalid Patch Operation: path for add operation must contain index of the value to be added";
    public static final String INVALID_PATCH_PATH_FOR_REMOVE_OPERATION = "Invalid Patch Operation: path for remove operation must contain index of the value to be deleted";
    public static final String INVALID_PATCH_PATH_END = "Invalid Patch Operation: path cannot ends with '/'";
    public static final String INVALID_PATCH_OPERATION_TYPE_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only 'replace' operation is allowed";
    public static final String INVALID_PATCH_PATH_FOR_KIND = "Invalid Patch Operation: for patching 'kind' only '/kind' path is allowed";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only one value is allowed";
    public static final String KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION = "Invalid kind: '%s', does not follow the required naming convention";
    public static final String RECORD_ID_LIST_NOT_EMPTY = "The list of record IDs cannot be empty";
    public static final String PATCH_RECORDS_MAX = "Up to 100 records can be patched at a time";
    public static final String INVALID_RECORD_ID_PATCH = "Invalid record format: '%s'. The following format is expected: {tenant-name}:{object-type}:{unique-identifier}";
    public static final String INVALID_PATCH_VALUE_FOR_ADD_OPERATION = "Invalid Patch Operation: for 'add' operation only single 'value' is allowed";
    public static final String INVALID_PATCH_VALUE_FOR_REPLACE_OPERATION = "Invalid Patch Operation: for 'replace' operation with the specified index(or end of an array sign '-') of the element to be replaced, only single value is allowed";
}
