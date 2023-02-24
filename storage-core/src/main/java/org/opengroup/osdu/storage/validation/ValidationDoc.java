package org.opengroup.osdu.storage.validation;

public class ValidationDoc {
    public static final String PATCH_RECORD_OPERATIONS_NOT_EMPTY = "Record patch operations cannot be empty";
    public static final String INVALID_PATCH_PATH = "Invalid Patch Path: can only be '/acl', '/legal/legaltags', '/tags', '/kind', '/ancestry/parents', '/data' or '/meta'";
    public static final String INVALID_PATCH_OPERATION = "Invalid Patch Operation: can only be 'replace' or 'add' or 'remove'";
    public static final String INVALID_PATCH_OPERATION_TYPE_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only 'replace' operation is allowed";
    public static final String INVALID_PATCH_VALUES_FORMAT_FOR_KIND = "Invalid Patch Operation: for patching '/kind' only one value is allowed";
    public static final String KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION = "Invalid kind: '%s', does not follow the required naming convention";
}
