package org.opengroup.osdu.storage.util.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.junit.Test;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.storage.model.RecordPatchOperation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class PatchUtilTest {

    public static final String VIEWER_TESTER_1 = "viewer@tester1";
    public static final String VIEWER_TESTER_2 = "viewer@tester2";
    public static final String VIEWER_TESTER_3 = "viewer@tester3";
    public static final String VIEWER_TESTER_4 = "viewer@tester4";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldConvertPatchOperationToJsonPatchForAddOperation() throws JsonProcessingException, JsonPatchException {
        List<RecordPatchOperation> patchOperations = new ArrayList<>();
        patchOperations.add(RecordPatchOperation.builder()
                .op("add")
                .path("/acl/viewers")
                .value(new String[]{VIEWER_TESTER_2, VIEWER_TESTER_3})
                .build());

        Record record = new Record();
        Acl acl = Acl.builder().viewers(new String[]{VIEWER_TESTER_1}).build();
        record.setAcl(acl);

        JsonPatch result = PatchUtil.convertPatchOpsToJsonPatch(patchOperations);

        JsonNode patched = result.apply(objectMapper.convertValue(record, JsonNode.class));
        Record recordPatched = objectMapper.treeToValue(patched, Record.class);

        assertThat(recordPatched.getAcl().getViewers())
                .hasSize(3)
                .containsExactly(VIEWER_TESTER_1, VIEWER_TESTER_2, VIEWER_TESTER_3);
    }

    @Test
    public void shouldConvertPatchOperationToJsonPatchForReplaceOperation() throws JsonProcessingException, JsonPatchException {
        List<RecordPatchOperation> patchOperations = new ArrayList<>();
        patchOperations.add(RecordPatchOperation.builder()
                .op("replace")
                .path("/acl/viewers")
                .value(new String[]{VIEWER_TESTER_2, VIEWER_TESTER_3})
                .build());

        Record record = new Record();
        Acl acl = Acl.builder().viewers(new String[]{VIEWER_TESTER_1, VIEWER_TESTER_4}).build();
        record.setAcl(acl);

        JsonPatch result = PatchUtil.convertPatchOpsToJsonPatch(patchOperations);

        JsonNode patched = result.apply(objectMapper.convertValue(record, JsonNode.class));
        Record recordPatched = objectMapper.treeToValue(patched, Record.class);

        assertThat(recordPatched.getAcl().getViewers())
                .hasSize(2)
                .containsExactly(VIEWER_TESTER_2, VIEWER_TESTER_3);
    }

}
