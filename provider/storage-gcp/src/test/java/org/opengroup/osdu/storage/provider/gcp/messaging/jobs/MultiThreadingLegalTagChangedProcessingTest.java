/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.auth.TokenProvider;
import org.opengroup.osdu.core.common.logging.DefaultLogger;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.InvalidTagWithReason;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriver;
import org.opengroup.osdu.core.gcp.oqm.model.OqmAckReplier;
import org.opengroup.osdu.core.gcp.oqm.model.OqmMessage;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingConfigurationProperties;
import org.opengroup.osdu.storage.provider.gcp.messaging.jobs.config.PullConfigStub;
import org.opengroup.osdu.storage.provider.gcp.messaging.jobs.stub.OqmPubSubStub;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadLegalTagConsistencyValidator;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadStorageAuditLogger;
import org.opengroup.osdu.storage.provider.gcp.messaging.thread.ThreadScopeContextHolder;
import org.opengroup.osdu.storage.provider.gcp.web.cache.GroupCache;
import org.opengroup.osdu.storage.provider.gcp.web.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.gcp.web.cache.SchemaCache;
import org.opengroup.osdu.storage.provider.gcp.web.repository.OsmRecordsMetadataRepository;
import org.opengroup.osdu.storage.service.LegalServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PullConfigStub.class}, webEnvironment = WebEnvironment.NONE, properties = {"oqmDriver=any"})
public class MultiThreadingLegalTagChangedProcessingTest {

    private static final String FIRST_TEST_TENANT = "test";
    private static final String FIRST_TEST_PROJECT = "test.project";
    private static final String SECOND_TEST_PROJECT = "test2.project";
    private static final String SECOND_TEST_TENANT = "test2";
    private static final String FIRST_TEST_LEGALTAG = "test-dataset-tag";
    private static final String SECOND_TEST_LEGALTAG = "test2-dataset-tag";
    private static final String REASON = "LegalTag not found";
    private static final String TEST_CORRELATION = "123";
    private static final int numberOfAllRuns = 500;
    private static final int numberOfTenantRuns = numberOfAllRuns / 2;
    private final CountDownLatch latch = new CountDownLatch(numberOfAllRuns);

    @MockBean
    private GroupCache groupCache;

    @MockBean(name = "LegalTagCache")
    private LegalTagCache legalTagCache;

    @MockBean
    private SchemaCache schemaCache;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean
    private ITenantFactory tenantInfoFactory;

    @MockBean
    private OqmAckReplier oqmAckReplier;

    @MockBean
    private LegalServiceImpl legalService;

    @MockBean
    private OsmRecordsMetadataRepository recordsMetadataRepository;

    @MockBean
    private OqmDriver oqmDriver;

    @MockBean
    private DefaultLogger iLogger;

    @MockBean
    private ThreadStorageAuditLogger jaxRsDpsLog;

    @Autowired
    private OqmPubSubStub pubSubStub;

    @Autowired
    private PullConfigStub pullConfigStub;

    @Autowired
    private ThreadLegalTagConsistencyValidator legalTagConsistencyValidator;

    @Autowired
    private LegalComplianceChangeServiceGcpImpl complianceChangeServiceGcp;

    @Autowired
    private ThreadDpsHeaders threadDpsHeaders;

    @Autowired
    private MessagingConfigurationProperties configurationProperties;

    @Before
    public void setUp() {
        configurationProperties.setStorageServiceAccountEmail("storage");

        setUpTenants();

        for (int i = 0; i < numberOfAllRuns; i++) {
            setUpLegalServiceGetInvalidLegalTags(FIRST_TEST_LEGALTAG + i);
            setUpLegalServiceGetInvalidLegalTags(SECOND_TEST_LEGALTAG + i);
            setStorageRepoQueryByLegal(FIRST_TEST_LEGALTAG + i);
            setStorageRepoQueryByLegal(SECOND_TEST_LEGALTAG + i);
        }
    }

    @Test
    public void testMultithreadingMessageProcessingWithDifferentTenants() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(numberOfTenantRuns);
        for (int i = 0; i < numberOfTenantRuns; i++) {
            int finalI = i;
            service.execute(() -> messageRun(FIRST_TEST_LEGALTAG + finalI, FIRST_TEST_TENANT));
            service.execute(() -> messageRun(SECOND_TEST_LEGALTAG + finalI, SECOND_TEST_TENANT));
        }
        latch.await();

        List<Map<DpsHeaders, PubSubInfo[]>> collector = pubSubStub.getCollector();

        assertEquals(numberOfAllRuns, collector.size());

        long firstTenantEvents = collector.stream().flatMap(entry -> entry.keySet().stream()).map(DpsHeaders::getPartitionId)
            .filter(partition -> partition.equals(FIRST_TEST_TENANT)).count();

        long secondTenantEvents = collector.stream().flatMap(entry -> entry.keySet().stream()).map(DpsHeaders::getPartitionId)
            .filter(partition -> partition.equals(SECOND_TEST_TENANT)).count();

        assertEquals(numberOfTenantRuns, firstTenantEvents);
        assertEquals(numberOfTenantRuns, secondTenantEvents);
    }

    private void messageRun(String legalTagName, String tenantName) {
        try {
            OqmMessage firstOqmMessage = new OqmMessage("1", "{\n"
                + "    \"statusChangedTags\": [{\n"
                + "            \"changedTagName\": \"" + legalTagName + "\",\n"
                + "            \"changedTagStatus\": \"incompliant\"\n"
                + "        }\n"
                + "    ]\n"
                + "}",
                ImmutableMap.of(
                    DpsHeaders.ACCOUNT_ID,
                    tenantName,
                    DpsHeaders.CORRELATION_ID,
                    TEST_CORRELATION,
                    DpsHeaders.DATA_PARTITION_ID,
                    tenantName
                )
            );

            threadDpsHeaders.setThreadContext(firstOqmMessage.getAttributes());
            LegalTagChangedProcessing legalTagChangedProcessing =
                new LegalTagChangedProcessing(legalTagConsistencyValidator, complianceChangeServiceGcp, threadDpsHeaders);

            legalTagChangedProcessing.process(firstOqmMessage);
        } catch (Exception e) {
            System.out.println(Arrays.toString(e.getStackTrace()));
        } finally {
            ThreadScopeContextHolder.currentThreadScopeAttributes().clear();
            latch.countDown();
        }
    }


    private void setUpLegalServiceGetInvalidLegalTags(String legalTagName) {
        InvalidTagWithReason[] firstInvalidTagWithReasons = new InvalidTagWithReason[1];
        InvalidTagWithReason invalidTagWithReason = new InvalidTagWithReason();
        invalidTagWithReason.setName(legalTagName);
        invalidTagWithReason.setReason(REASON);
        firstInvalidTagWithReasons[0] = invalidTagWithReason;

        when(legalService.getInvalidLegalTags(Collections.singleton(legalTagName))).thenReturn(firstInvalidTagWithReasons);

    }

    private void setStorageRepoQueryByLegal(String legalTagName) {
        Legal legal = new Legal();
        legal.setLegaltags(Collections.singleton(legalTagName));

        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("record-id" + legalTagName);
        recordMetadata.setLegal(legal);

        SimpleEntry<String, List<RecordMetadata>> mapWithValues = new SimpleEntry<>("empty", Collections.singletonList(recordMetadata));
        SimpleEntry<String, List<RecordMetadata>> emptyMap = new SimpleEntry<>("empty", Collections.emptyList());

        when(recordsMetadataRepository.queryByLegal(legalTagName, LegalCompliance.compliant, 500))
            .thenReturn(mapWithValues)
            .thenReturn(emptyMap);

    }

    private void setUpTenants() {
        TenantInfo testTenant = new TenantInfo();
        testTenant.setProjectId(FIRST_TEST_PROJECT);
        testTenant.setDataPartitionId(FIRST_TEST_TENANT);
        testTenant.setName(FIRST_TEST_TENANT);

        TenantInfo secondTestTenant = new TenantInfo();
        secondTestTenant.setProjectId(SECOND_TEST_PROJECT);
        secondTestTenant.setDataPartitionId(SECOND_TEST_TENANT);
        secondTestTenant.setName(SECOND_TEST_TENANT);

        when(tenantInfoFactory.listTenantInfo()).thenReturn(ImmutableList.of(testTenant, secondTestTenant));
    }
}