package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.google.common.collect.ImmutableSet;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.storage.model.RecordPatchOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
@RunWith(MockitoJUnitRunner.class)
public class PatchUtilImplTest {

    public static final String RECORD_ID = "recordId";
    public static final String TEST_STRING_1 = "test string #1";
    public static final String TEST_STRING_2 = "test string #2";
    public static final String TEST_STRING_3 = "test string #3";
    public static final String TEST_STRING_4 = "test string #4";
    private static final String PREVIOUS_KIND = "previous kind" ;
    private static final String NEW_KIND = "new kind" ;
    private static final Optional<CollaborationContext> collaborationContext = Optional.empty();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RecordPatchOperation> patchOperations = new ArrayList<>();

    @InjectMocks
    private PatchUtilImpl sut;

    @Test
    public void shouldConvertPatchOperationToJsonPatchForAddAclViewersOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        Acl acl = Acl.builder().viewers(new String[]{TEST_STRING_1}).build();
        record.setAcl(acl);
        patchOperations.add(RecordPatchOperation.builder()
                .op("add")
                .path("/acl/viewers")
                .value(new String[]{TEST_STRING_2, TEST_STRING_3})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getAcl().getViewers())
                .hasSize(3)
                .containsExactly(TEST_STRING_1, TEST_STRING_2, TEST_STRING_3);
    }

    @Test
    public void shouldConvertPatchOperationToJsonPatchForAddKindOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        record.setKind(PREVIOUS_KIND);
        patchOperations.add(RecordPatchOperation.builder()
                .op("add")
                .path("/kind")
                .value(new String[]{NEW_KIND})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getKind()).isEqualTo(NEW_KIND);
    }

    @Test
    public void shouldConvertPatchOperationToJsonPatchForAddLegalTagsOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        Legal legal = new Legal();
        Set<String> legalTags = ImmutableSet.of(TEST_STRING_1, TEST_STRING_2);
        legal.setLegaltags(legalTags);
        record.setLegal(legal);
        patchOperations.add(RecordPatchOperation.builder()
                .op("add")
                .path("/legal/legaltags")
                .value(new String[]{TEST_STRING_3, TEST_STRING_4})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getLegal().getLegaltags())
                .hasSize(4)
                .containsExactlyInAnyOrder(TEST_STRING_1, TEST_STRING_2, TEST_STRING_3, TEST_STRING_4);
    }

    @Test
    public void shouldConvertPatchOperationToJsonPatchForReplaceViewersOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        Acl acl = Acl.builder().viewers(new String[]{TEST_STRING_1, TEST_STRING_4}).build();
        record.setAcl(acl);
        patchOperations.add(RecordPatchOperation.builder()
                .op("replace")
                .path("/acl/viewers")
                .value(new String[]{TEST_STRING_2, TEST_STRING_3})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getAcl().getViewers())
                .hasSize(2)
                .containsExactly(TEST_STRING_2, TEST_STRING_3);
    }

    @Test
    public void shouldConvertPatchOperationToJsonPatchForReplaceKindOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        record.setKind(PREVIOUS_KIND);
        patchOperations.add(RecordPatchOperation.builder()
                .op("replace")
                .path("/kind")
                .value(new String[]{NEW_KIND})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getKind()).isEqualTo(NEW_KIND);
    }

    @Test
    @Ignore
    public void shouldConvertPatchOperationToJsonPatchForRemoveOperation() throws JsonProcessingException, JsonPatchException {
        Record record = new Record();
        Acl acl = Acl.builder().viewers(new String[]{TEST_STRING_1, TEST_STRING_2}).build();
        record.setAcl(acl);
        patchOperations.add(RecordPatchOperation.builder()
                .op("remove")
                .path("/acl/viewers")
                .value(new String[]{TEST_STRING_2})
                .build());

        Map<String, JsonPatch> result = sut.convertPatchOpsToJsonPatch(Collections.singletonList(RECORD_ID), patchOperations, collaborationContext);
        Record recordPatched = applyPatch(record, result.get(RECORD_ID));

        assertThat(recordPatched.getAcl().getViewers())
                .hasSize(1)
                .containsExactly(TEST_STRING_1);
    }

    private Record applyPatch(Record record, JsonPatch result) throws JsonPatchException, JsonProcessingException {
        JsonNode patched = result.apply(objectMapper.convertValue(record, JsonNode.class));
        return objectMapper.treeToValue(patched, Record.class);
    }

}
