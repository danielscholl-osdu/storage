package org.opengroup.osdu.storage.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RecordConstants {
    public static final String COLLABORATIONS_FEATURE_NAME = "collaborations-enabled";
    public static final String KIND_PATH = "/kind";
    public static final String TAGS_PATH = "/tags";
    public static final String REGEX_TAGS_PATH_FOR_ADD_OR_REMOVE_SINGLE_KEY_VALUE = TAGS_PATH.concat("/.+");
    public static final String ACL_VIEWERS_PATH = "/acl/viewers";
    public static final String ACL_OWNERS_PATH = "/acl/owners";
    public static final String LEGAL_TAGS_PATH = "/legal/legaltags";
    public static final String ANCESTRY_PARENTS_PATH = "/ancestry/parents";
    public static final String META_PATH = "/meta";
    public static final String MODIFY_USER_PATH = "/modifyUser";
    public static final String MODIFY_TIME_PATH = "/modifyTime";
    public static final String METADATA_PREFIX_PATH = "/metadata";
    public static final String DATA_PATH = "/data";
    public static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH = "(" + String.join("|", ACL_VIEWERS_PATH, ACL_OWNERS_PATH, LEGAL_TAGS_PATH, ANCESTRY_PARENTS_PATH) + ")";
    public static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH_FOR_ADD_OR_REMOVE_SINGLE_VALUE = REGEX_ACLS_LEGAL_ANCESTRY_PATH + "/(\\d+|-)";
    public static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList(KIND_PATH, TAGS_PATH, ACL_VIEWERS_PATH, ACL_OWNERS_PATH, LEGAL_TAGS_PATH, ANCESTRY_PARENTS_PATH, DATA_PATH, META_PATH));
    public static final Set<String> ACLS_LEGAL_ANCESTY_PATHS = new HashSet<>(Arrays.asList(ACL_VIEWERS_PATH, ACL_OWNERS_PATH, LEGAL_TAGS_PATH, ANCESTRY_PARENTS_PATH));
    public static final Set<String> INVALID_PATHS_FOR_REMOVE_OPERATION = ACLS_LEGAL_ANCESTY_PATHS;
    public static final String OP = "op";
    public static final String PATH = "path";
    public static final String VALUE = "value";

    public static final int MIN_OP_NUMBER = 1;
    public static final int MAX_OP_NUMBER = 100;
    public static final int MAX_RECORD_ID_NUMBER = 100;
    public static final String REGEX_VERSION_IDS = "[0-9]+(,[0-9]+)*";
    public static final int MAX_VERSION_IDS_NUMBER = 50;

    public static final String OPA_FEATURE_NAME = "featureFlag.opa.enabled";

}
