package org.opengroup.osdu.storage.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StringConstants {
    public static final String COLLABORATIONS_FEATURE_NAME = "collaborations-enabled";
    public static final String KIND = "/kind";
    public static final String TAGS = "/tags";
    public static final String REGEX_TAGS_PATH_FOR_ADD_OR_REMOVE_SINGLE_KEY_VALUE = TAGS.concat("/.+");
    public static final String ACL_VIEWERS = "/acl/viewers";
    public static final String ACL_OWNERS = "/acl/owners";
    public static final String LEGAL_TAGS = "/legal/legaltags";
    public static final String ANCESTRY_PARENTS = "/ancestry/parents";
    public static final String META = "/meta";
    public static final String MODIFY_USER = "/modifyUser";
    public static final String MODIFY_TIME = "/modifyTime";
    public static final String METADATA_PREFIX = "/metadata";
    public static final String DATA = "/data";
    public static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH = "(" + String.join("|", ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS) + ")";
    public static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH_FOR_ADD_OR_REMOVE_SINGLE_VALUE = REGEX_ACLS_LEGAL_ANCESTRY_PATH + "/(\\d+|-)";
    public static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList(KIND, TAGS, ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS, DATA, META));
    public static final Set<String> ACLS_LEGAL_ANCESTY_PATHS = new HashSet<>(Arrays.asList(ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS));
    public static final Set<String> INVALID_PATHS_FOR_REMOVE_OPERATION = ACLS_LEGAL_ANCESTY_PATHS;
    public static final String OP = "op";
    public static final String PATH = "path";
    public static final String VALUE = "value";

    public static final int MIN_OP_NUMBER = 1;
    public static final int MAX_OP_NUMBER = 100;
}
