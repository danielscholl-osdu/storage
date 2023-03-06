package org.opengroup.osdu.storage.validation;

public class ValidationDoc {
    public static final String PATCH_RECORD_OPERATIONS_NOT_EMPTY = "Record patch operations cannot be empty";
    public static final String INVALID_PATCH_PATH = "Invalid Patch Path: can only be '/acl/viewers', 'acl/owners', '/legal/legaltags', '/tags', '/kind', '/ancestry/parents', '/data' or '/meta'";
    public static final String INVALID_PATCH_OPERATION = "Invalid Patch Operation: can only be 'replace' or 'add' or 'remove'";
    public static final String INVALID_PATCH_OPERATION_SIZE = "Invalid Patch Operation: the number of operations can only be between 1 and 100";
    public static final String INVALID_PATCH_OPERATION_PATH_FOR_REMOVE_OPERATION = "Invalid Patch Operation: the path for remove operation must contain index of the value to be deleted";
    public static final String INVALID_PATCH_OPERATION_TYPE_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only 'replace' operation is allowed";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only one value is allowed";
    public static final String KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION = "Invalid kind: '%s', does not follow the required naming convention";
    public static final String INVALID_INPUT_SIZE = "Invalid input size: The input limit for record IDs is between 1-100";
}
