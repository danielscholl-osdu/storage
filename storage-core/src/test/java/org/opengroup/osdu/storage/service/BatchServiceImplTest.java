package org.opengroup.osdu.storage.service;

import com.google.common.collect.Sets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.crs.CrsConverterClientFactory;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.crs.ConvertStatus;
import org.opengroup.osdu.core.common.model.crs.RecordsAndStatuses;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.MultiRecordRequest;
import org.opengroup.osdu.core.common.model.storage.MultiRecordResponse;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.conversion.DpsConversionService;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

@ExtendWith(MockitoExtension.class)
class BatchServiceImplTest {
    @Mock
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsConversionService conversionService;

    @Mock
    private CrsConverterClientFactory crsConverterClientFactory;

    @Mock
    private IEntitlementsExtensionService entitlementsAndCacheService;

    @Mock
    private IOPAService opaService;

    @Mock
    private IFeatureFlag featureFlag;

    @InjectMocks
    BatchServiceImpl sut = mock(BatchServiceImpl.class, Mockito.CALLS_REAL_METHODS);
    private static final String ACL_OWNER = "test_acl";
    private static final String TEST_KIND = "test_kind";
    private static final String TEST_ID_1 = "test_id1";
    private static final String TEST_ID_2 = "test_id2";
    private static final String INT_NUMBER = "IntegerNumber";
    private static final String[] OWNERS = new String[]{ACL_OWNER};

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        Field gsonField = BatchServiceImpl.class.getDeclaredField("gson");
        gsonField.setAccessible(true);
        gsonField.set(sut, new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create());
    }

    @Test
    void getMultipleRecords_returnsInvalidRecords_whenRecordsAreNotFound() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(new HashMap());

        MultiRecordIds multiRecordIds = new MultiRecordIds();
        multiRecordIds.setRecords(recordIds);

        MultiRecordInfo multiRecordInfo = sut.getMultipleRecords(multiRecordIds, Optional.empty());

        assertTrue(multiRecordInfo.getRecords().isEmpty());
        assertTrue(!multiRecordInfo.getInvalidRecords().isEmpty());
        assertTrue(multiRecordInfo.getRetryRecords().isEmpty());
    }

    @Test
    void getMultipleRecords_returnsValidRecords_whenRecordsAreFound() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();

        recordMetadataMap.put(TEST_ID_1, buildRecordMetadata(TEST_ID_1));
        recordMetadataMap.put(TEST_ID_2, buildRecordMetadata(TEST_ID_2));

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        MultiRecordIds multiRecordIds = new MultiRecordIds();
        multiRecordIds.setRecords(recordIds);

        Map<String, String> recordIdContentMap = new HashMap<>();
        recordIdContentMap.put(TEST_ID_1, new Gson().toJson(buildRecord(TEST_ID_1)));
        recordIdContentMap.put(TEST_ID_2, new Gson().toJson(buildRecord(TEST_ID_1)));

        when(cloudStorage.read(any(),any())).thenReturn(recordIdContentMap);
        when(entitlementsAndCacheService.isDataManager(headers)).thenReturn(true);

        MultiRecordInfo multiRecordInfo = sut.getMultipleRecords(multiRecordIds, Optional.empty());

        assertTrue(!multiRecordInfo.getRecords().isEmpty());
        assertTrue(multiRecordInfo.getInvalidRecords().isEmpty());
        assertTrue(multiRecordInfo.getRetryRecords().isEmpty());
    }

    @Test
    void getMultipleRecords_returnsValidRecords_whenRecordsContainIntegerNumberInDataField() {
        List<String> recordIds = Arrays.asList(TEST_ID_1);
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();
        recordMetadataMap.put(TEST_ID_1, buildRecordMetadata(TEST_ID_1));

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        MultiRecordIds multiRecordIds = new MultiRecordIds();
        multiRecordIds.setRecords(recordIds);

        Map<String, String> recordIdContentMap = new HashMap<>();
        Record record = buildRecord(TEST_ID_1);
        Map<String, Object> recordData = new HashMap<>();
        recordData.put(INT_NUMBER, 1);
        record.setData(recordData);
        recordIdContentMap.put(TEST_ID_1, new Gson().toJson(record));

        when(cloudStorage.read(any(),any())).thenReturn(recordIdContentMap);
        when(entitlementsAndCacheService.isDataManager(headers)).thenReturn(true);

        MultiRecordInfo multiRecordInfo = sut.getMultipleRecords(multiRecordIds, Optional.empty());

        assertFalse(multiRecordInfo.getRecords().isEmpty());
        assertTrue(multiRecordInfo.getInvalidRecords().isEmpty());
        assertTrue(multiRecordInfo.getRetryRecords().isEmpty());
        assertEquals("1", multiRecordInfo.getRecords().get(0).getData().get(INT_NUMBER).toString());
    }

    @Test
    void fetchMultipleRecords_returnsInvalidRecords_whenFrameOfReferenceIsEmptyAndRecordsNotFound() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(new HashMap());

        MultiRecordRequest multiRecordRequest = new MultiRecordRequest();
        multiRecordRequest.setRecords(recordIds);

        MultiRecordResponse multiRecordResponse = sut.fetchMultipleRecords(multiRecordRequest, Optional.empty());

        assertTrue(multiRecordResponse.getRecords().isEmpty());
        assertTrue(!multiRecordResponse.getNotFound().isEmpty());
        assertTrue(multiRecordResponse.getConversionStatuses().isEmpty());
    }

    @Test
    void fetchMultipleRecords_returnsRecordsAsIs_whenFrameOfReferenceIsEmptyAndRecordsAreFound() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(new HashMap());

        MultiRecordRequest multiRecordRequest = new MultiRecordRequest();
        multiRecordRequest.setRecords(recordIds);

        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();

        recordMetadataMap.put(TEST_ID_1, buildRecordMetadata(TEST_ID_1));
        recordMetadataMap.put(TEST_ID_2, buildRecordMetadata(TEST_ID_2));

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        Map<String, String> recordIdContentMap = new HashMap<>();
        recordIdContentMap.put(TEST_ID_1, new Gson().toJson(buildRecord(TEST_ID_1)));
        recordIdContentMap.put(TEST_ID_2, new Gson().toJson(buildRecord(TEST_ID_1)));

        when(cloudStorage.read(any(),any())).thenReturn(recordIdContentMap);
        when(entitlementsAndCacheService.isDataManager(headers)).thenReturn(false);

        List<RecordMetadata> entitlementsResponse = new ArrayList<>();
        entitlementsResponse.addAll(recordMetadataMap.values());
        when(entitlementsAndCacheService.hasValidAccess(any(), any())).thenReturn(entitlementsResponse);

        MultiRecordResponse multiRecordResponse = sut.fetchMultipleRecords(multiRecordRequest, Optional.empty());

        assertTrue(!multiRecordResponse.getRecords().isEmpty());
        assertTrue(multiRecordResponse.getNotFound().isEmpty());
        assertTrue(multiRecordResponse.getConversionStatuses().isEmpty());
    }

    @Test
    void fetchMultipleRecords_returnsConvertedRecords_whenFrameOfReferenceIsPresentAndRecordsAreFound() {
        ReflectionTestUtils.setField(crsConverterClientFactory, "crsApi", "crs_endpoint");
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);

        Map<String, String> osduHeaders = new HashMap<>();
        osduHeaders.put("frame-of-reference", "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;");

        when(headers.getHeaders()).thenReturn(osduHeaders);
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(new HashMap());

        MultiRecordRequest multiRecordRequest = new MultiRecordRequest();
        multiRecordRequest.setRecords(recordIds);

        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();

        recordMetadataMap.put(TEST_ID_1, buildRecordMetadata(TEST_ID_1));
        recordMetadataMap.put(TEST_ID_2, buildRecordMetadata(TEST_ID_2));

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        Map<String, String> recordIdContentMap = new HashMap<>();
        String jsonStringRecord1 = new Gson().toJson(buildRecord(TEST_ID_1));
        String jsonStringRecord2 = new Gson().toJson(buildRecord(TEST_ID_1));

        recordIdContentMap.put(TEST_ID_1, jsonStringRecord1);
        recordIdContentMap.put(TEST_ID_2, jsonStringRecord2);

        when(cloudStorage.read(any(),any())).thenReturn(recordIdContentMap);
        when(entitlementsAndCacheService.isDataManager(headers)).thenReturn(false);
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        ValidationOutputRecord validationOutputRecord = new ValidationOutputRecord();
        validationOutputRecord.setId(TEST_ID_1);
        validationOutputRecord.setErrors(new ArrayList<>());

        ValidationOutputRecord validationOutputRecord2 = new ValidationOutputRecord();
        validationOutputRecord2.setId(TEST_ID_2);
        validationOutputRecord2.setErrors(new ArrayList<>());

        validationOutputRecords.add(validationOutputRecord);
        validationOutputRecords.add(validationOutputRecord2);

        when(opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        List<JsonObject> convertedRecords = new ArrayList<>();
        convertedRecords.add(new JsonParser().parse(jsonStringRecord1).getAsJsonObject());

        List<ConversionStatus> conversionStatuses = new ArrayList<>();
        ConversionStatus conversionStatus = new ConversionStatus();
        conversionStatus.setStatus(ConvertStatus.SUCCESS.toString());
        conversionStatus.setId(TEST_ID_1);

        conversionStatuses.add(conversionStatus);

        RecordsAndStatuses recordsAndStatuses = new RecordsAndStatuses();
        recordsAndStatuses.setConversionStatuses(conversionStatuses);
        recordsAndStatuses.setRecords(convertedRecords);

        when(conversionService.doConversion(any())).thenReturn(recordsAndStatuses);

        MultiRecordResponse multiRecordResponse = sut.fetchMultipleRecords(multiRecordRequest, Optional.empty());

        assertTrue(!multiRecordResponse.getRecords().isEmpty());
        assertTrue(!multiRecordResponse.getNotFound().isEmpty());
        assertTrue(!multiRecordResponse.getConversionStatuses().isEmpty());
    }

    @Test
    void getMultipleRecords_returnsInvalidRecords_whenRecordsWithoutGcsVersionPaths() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();
        RecordMetadata recordMetadataTestId1 = buildRecordMetadata(TEST_ID_1);
        RecordMetadata recordMetadataTestId2 = buildRecordMetadata(TEST_ID_2);
        recordMetadataTestId1.setGcsVersionPaths(new ArrayList<>());
        recordMetadataTestId2.setGcsVersionPaths(new ArrayList<>());
        recordMetadataMap.put(TEST_ID_1, recordMetadataTestId1);
        recordMetadataMap.put(TEST_ID_2, recordMetadataTestId2);

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        MultiRecordIds multiRecordIds = new MultiRecordIds();
        multiRecordIds.setRecords(recordIds);
        MultiRecordInfo multiRecordInfo = sut.getMultipleRecords(multiRecordIds, Optional.empty());

        assertTrue(multiRecordInfo.getRecords().isEmpty());
        assertFalse(multiRecordInfo.getInvalidRecords().isEmpty());
        assertTrue(multiRecordInfo.getRetryRecords().isEmpty());
    }

    @Test
    void fetchMultipleRecords_returnsNotFoundRecords_whenRecordsWithoutVersionPaths() {
        List<String> recordIds = Arrays.asList(TEST_ID_1, TEST_ID_2);
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();
        RecordMetadata recordMetadataTestId1 = buildRecordMetadata(TEST_ID_1);
        RecordMetadata recordMetadataTestId2 = buildRecordMetadata(TEST_ID_2);
        recordMetadataTestId1.setGcsVersionPaths(new ArrayList<>());
        recordMetadataTestId2.setGcsVersionPaths(new ArrayList<>());
        recordMetadataMap.put(TEST_ID_1, recordMetadataTestId1);
        recordMetadataMap.put(TEST_ID_2, recordMetadataTestId2);

        MultiRecordRequest multiRecordRequest = new MultiRecordRequest();
        multiRecordRequest.setRecords(recordIds);

        when(recordRepository.get(recordIds, Optional.empty())).thenReturn(recordMetadataMap);

        MultiRecordIds multiRecordIds = new MultiRecordIds();
        multiRecordIds.setRecords(recordIds);
        MultiRecordResponse multiRecordResponse = sut.fetchMultipleRecords(multiRecordRequest, Optional.empty());

        assertTrue(multiRecordResponse.getRecords().isEmpty());
        assertEquals(2, multiRecordResponse.getNotFound().size());
    }


    private static RecordMetadata buildRecordMetadata(String recordId) {
        Acl acl = new Acl();
        acl.setOwners(OWNERS);
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(recordId);
        recordMetadata.setKind(TEST_KIND);
        recordMetadata.setAcl(acl);
        recordMetadata.setStatus(RecordState.active);
        recordMetadata.setGcsVersionPaths(Arrays.asList("1", "2"));
        return recordMetadata;
    }

    private static Record buildRecord(String recordId) {
        Legal legal = new Legal();
        Acl acl = new Acl();
        acl.setOwners(OWNERS);
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));
        Record record = new Record();
        record.setKind(TEST_KIND);
        record.setId(recordId);
        record.setLegal(legal);
        // set up empty ancestry for record1
        RecordAncestry ancestry = new RecordAncestry();
        record.setAncestry(ancestry);
        record.setAcl(acl);
        return record;
    }

}
