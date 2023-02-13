package org.opengroup.osdu.storage.validation;

public class ValidationDoc {
    public static final String PATCH_RECORD_OPERATIONS_NOT_EMPTY = "Record patch operations cannot be empty";
    public static final String INVALID_PATCH_PATH = "Invalid Patch Path: can only be '/acl', '/legal/legaltags', '/tags', '/kind', '/ancestry' or '/data'";
    public static final String INVALID_PATCH_OPERATION = "Invalid Patch Operation: can only be 'replace' or 'add' or 'remove'";
}
