package org.opengroup.osdu.storage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.conversion.CrsConversionService;
import org.opengroup.osdu.storage.logging.ReadAuditLogsConsumer;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.service.BulkUpdateRecordServiceImpl;
import org.opengroup.osdu.storage.service.DataAuthorizationService;
import org.opengroup.osdu.storage.service.IngestionServiceImpl;
import org.opengroup.osdu.storage.service.LegalServiceImpl;
import org.opengroup.osdu.storage.service.PartitionServiceImpl;
import org.opengroup.osdu.storage.service.PatchRecordsServiceImpl;
import org.opengroup.osdu.storage.service.PersistenceServiceImpl;
import org.opengroup.osdu.storage.service.QueryServiceImpl;
import org.opengroup.osdu.storage.service.RecordServiceImpl;
import org.opengroup.osdu.storage.service.SchemaServiceImpl;
import org.opengroup.osdu.storage.util.RecordBlocks;
import org.opengroup.osdu.storage.util.StringConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

public abstract class ApiTest<T> {
    @MockBean
    protected ICache cache;

    @MockBean
    protected IEntitlementsFactory iEntitlementsFactory;

    @MockBean
    protected CrsConversionService crsConversionService;

    @MockBean
    protected ReadAuditLogsConsumer readAuditLogsConsumer;

    @MockBean
    protected PartitionPolicyStatusService partitionPolicyStatusService;

    @MockBean
    protected BulkUpdateRecordServiceImpl bulkUpdateRecordService;

    @MockBean
    protected DataAuthorizationService dataAuthorizationService;

    @MockBean
    protected IngestionServiceImpl ingestionService;

    @MockBean
    protected LegalServiceImpl legalService;

    @MockBean
    protected PartitionServiceImpl partitionService;

    @MockBean
    protected PatchRecordsServiceImpl patchRecordsService;

    @MockBean
    protected PersistenceServiceImpl persistenceService;

    @MockBean
    protected QueryServiceImpl queryService;

    @MockBean
    protected RecordServiceImpl recordService;

    @MockBean
    protected SchemaServiceImpl schemaService;

    @MockBean
    protected RecordBlocks recordBlocks;

    @MockBean
    protected IFeatureFlag collaborationFeatureFlag;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected abstract HttpMethod getHttpMethod();
    protected abstract String getUriTemplate();

    @Before
    public void setup() {
        Mockito.when(collaborationFeatureFlag.isFeatureEnabled(StringConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(false);
    }

    protected void setupAuthorization(String role) throws EntitlementsException {
        IEntitlementsService iEntitlementsService = Mockito.mock(IEntitlementsService.class);
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName(role);
        Groups groups = new Groups();
        groups.setGroups(Collections.singletonList(groupInfo));
        groups.setMemberEmail("a@b");
        Mockito.when(iEntitlementsService.getGroups()).thenReturn(groups);
        Mockito.when(iEntitlementsFactory.create(ArgumentMatchers.any())).thenReturn(iEntitlementsService);
    }

    protected ResultActions sendRequest(T dto, Object... uriVars) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.request(getHttpMethod(), getUriTemplate(), uriVars)
                .contentType("application/json-patch+json")
                .characterEncoding(StandardCharsets.UTF_8)
                .header(DpsHeaders.AUTHORIZATION, "Bearer token")
                .header(DpsHeaders.DATA_PARTITION_ID, "opendes")
                .header(DpsHeaders.USER_ID, "a@b.com")
                .content(objectMapper.writeValueAsString(dto)));
    }

}
