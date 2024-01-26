package org.opengroup.osdu.storage.provider.azure.service;

import com.google.common.collect.Sets;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.MessageBusImpl;
import org.opengroup.osdu.storage.provider.azure.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalComplianceChangeServiceAzureImplTest {
    @InjectMocks
    LegalComplianceChangeServiceAzureImpl sut;
    @Mock
    private IRecordsMetadataRepository recordsRepo;
    @Mock
    private DpsHeaders headers;
    @Mock
    private LegalTagCache legalTagCache;
    @Mock
    private MessageBusImpl pubSubclient;
    @Mock
    private IFeatureFlag collaborationFeatureFlag;

    @Test
    void updateComplianceOnRecords() throws ComplianceUpdateStoppedException {
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = mock(AbstractMap.SimpleEntry.class);
        RecordMetadata recordMetadata1 = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");

        when(results.getValue()).thenReturn(Arrays.asList(recordMetadata1, recordMetadata2));

        when(recordsRepo.queryByLegalTagName(any(), any(int.class), any())).thenReturn(results);

        LegalTagChangedCollection legalTagChangedCollection = new LegalTagChangedCollection();
        LegalTagChanged legalTagChanged = new LegalTagChanged();
        legalTagChanged.setChangedTagName("new tag");
        legalTagChanged.setChangedTagStatus("compliant");

        LegalTagChanged legalTagChanged1 = new LegalTagChanged();
        legalTagChanged1.setChangedTagName("new tag");
        legalTagChanged1.setChangedTagStatus("compliant");
        List<LegalTagChanged> legalTagsChanged = Arrays.asList(legalTagChanged, legalTagChanged1);

        legalTagChangedCollection.setStatusChangedTags(legalTagsChanged);

        Map<String, LegalCompliance> legalComplianceMap = sut.updateComplianceOnRecords(legalTagChangedCollection, headers);

        assertEquals(2, legalComplianceMap.size());
    }

    @Test
    void legalTagCacheIsCleared_ifIncompliantLegalTagChangedEventRecieved() throws ComplianceUpdateStoppedException {
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = mock(AbstractMap.SimpleEntry.class);
        RecordMetadata recordMetadata1 = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");

        when(results.getValue()).thenReturn(Arrays.asList(recordMetadata1, recordMetadata2));

        when(recordsRepo.queryByLegalTagName(any(), any(int.class), any())).thenReturn(results);

        LegalTagChangedCollection legalTagChangedCollection = new LegalTagChangedCollection();
        LegalTagChanged legalTagChanged = new LegalTagChanged();
        legalTagChanged.setChangedTagName("new tag");
        legalTagChanged.setChangedTagStatus("incompliant");

        LegalTagChanged legalTagChanged1 = new LegalTagChanged();
        legalTagChanged1.setChangedTagName("new tag2");
        legalTagChanged1.setChangedTagStatus("compliant");
        List<LegalTagChanged> legalTagsChanged = Arrays.asList(legalTagChanged, legalTagChanged1);
        legalTagChangedCollection.setStatusChangedTags(legalTagsChanged);

        Map<String, LegalCompliance> legalComplianceMap = sut.updateComplianceOnRecords(legalTagChangedCollection, headers);

        assertEquals(2, legalComplianceMap.size());
        verify(legalTagCache, times(1)).delete("new tag");
    }

    @Test
    void legalTagCacheIsCleared_ifInvalidLegalTagChangedEventReceived() throws ComplianceUpdateStoppedException {
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = mock(AbstractMap.SimpleEntry.class);
        RecordMetadata recordMetadata1 = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");

        when(results.getValue()).thenReturn(Arrays.asList(recordMetadata1, recordMetadata2));

        when(recordsRepo.queryByLegalTagName(any(), any(int.class), any())).thenReturn(results);

        LegalTagChangedCollection legalTagChangedCollection = new LegalTagChangedCollection();
        LegalTagChanged legalTagChanged = new LegalTagChanged();
        legalTagChanged.setChangedTagName("new tag");
        legalTagChanged.setChangedTagStatus("incompliant");

        LegalTagChanged legalTagChanged1 = new LegalTagChanged();
        legalTagChanged1.setChangedTagName("new tag2");
        legalTagChanged1.setChangedTagStatus("invalid");
        List<LegalTagChanged> legalTagsChanged = Arrays.asList(legalTagChanged, legalTagChanged1);

        legalTagChangedCollection.setStatusChangedTags(legalTagsChanged);

        Map<String, LegalCompliance> legalComplianceMap = sut.updateComplianceOnRecords(legalTagChangedCollection, headers);

        assertEquals(2, legalComplianceMap.size());
        verify(legalTagCache, times(1)).delete("new tag");
    }

    @Test
    void emptyRecordSetIsRetrieved_ifRecordsRepositoryThrowsException() throws ComplianceUpdateStoppedException {
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = mock(AbstractMap.SimpleEntry.class);
        RecordMetadata recordMetadata1 = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");

        when(results.getValue()).thenReturn(Arrays.asList(recordMetadata1, recordMetadata2));
        when(recordsRepo.queryByLegalTagName(any(), any(int.class), any())).thenReturn(results);

        LegalTagChangedCollection legalTagChangedCollection = new LegalTagChangedCollection();
        LegalTagChanged legalTagChanged = new LegalTagChanged();
        legalTagChanged.setChangedTagName("new tag");
        legalTagChanged.setChangedTagStatus("incompliant");

        LegalTagChanged legalTagChanged1 = new LegalTagChanged();
        legalTagChanged1.setChangedTagName("new tag2");
        legalTagChanged1.setChangedTagStatus("invalid");
        List<LegalTagChanged> legalTagsChanged = Arrays.asList(legalTagChanged, legalTagChanged1);

        when(recordsRepo.createOrUpdate(any(), any())).thenThrow(RuntimeException.class);

        legalTagChangedCollection.setStatusChangedTags(legalTagsChanged);

        AppException exception = assertThrows(AppException.class, () -> {
            sut.updateComplianceOnRecords(legalTagChangedCollection, headers);
        });

        verify(legalTagCache, times(1)).delete("new tag");
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
        assertEquals("The server could not process your request at the moment.", exception.getMessage());
    }

    private RecordMetadata setUpRecordMetadata(String id) {
        Record record = new Record();
        record.setId(id);
        Acl acl = Acl.builder()
                .viewers(new String[]{"viewers"})
                .owners(new String[]{"owners"})
                .build();
        record.setAcl(acl);
        record.setKind("kind");
        Legal legal = new Legal();
        legal.setStatus(LegalCompliance.compliant);
        legal.setLegaltags(Sets.newHashSet("legalTag1"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("US"));
        record.setLegal(legal);
        record.setVersion(1L);
        RecordMetadata recordMetadata = new RecordMetadata(record);
        recordMetadata.setGcsVersionPaths(Arrays.asList("1", "2"));
        return recordMetadata;
    }
}
