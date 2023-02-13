package org.opengroup.osdu.storage.util.api;

import com.google.common.base.Strings;

public enum PatchOperations {

    ADD("add"),
    REPLACE("replace"),
    REMOVE("remove"),
    UNDEFINED("undefined");

    private final String op;

    PatchOperations(String op) {
        this.op = op;
    }

    public String getOperation() {
        return op;
    }

    public static PatchOperations forOperation(String value) {

        if (Strings.isNullOrEmpty(value)) return PatchOperations.UNDEFINED;

        for (PatchOperations op : values()) {
            if (op.getOperation().equalsIgnoreCase(value)) {
                return op;
            }
        }
        return PatchOperations.UNDEFINED;
    }
}
