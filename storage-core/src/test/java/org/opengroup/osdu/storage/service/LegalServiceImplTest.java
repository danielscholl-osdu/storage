// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.legal.*;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.legal.ILegalFactory;
import org.opengroup.osdu.core.common.legal.ILegalProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LegalServiceImplTest {

    @Mock
    private DpsHeaders headers;

    @Mock
    private ICache<String, String> cache;

    @Mock
    private ILegalFactory factory;

    @Mock
    private ILegalProvider legalService;

    @InjectMocks
    private LegalServiceImpl sut;

    @Before
    public void setup() {
        when(this.factory.create(this.headers)).thenReturn(this.legalService);
    }

    @Test
    public void should_notCallLegalService_when_validatingLegalTagsIsResolvedInCache() {
        Set<String> legaltags = Sets.newHashSet("tag1", "tag2");

        when(this.cache.get("tag1")).thenReturn("cache hit");
        when(this.cache.get("tag2")).thenReturn("cache hit");

        this.sut.validateLegalTags(legaltags);
        verify(this.factory, never()).create(any());
    }

    @Test
    public void should_callLegalServiceAndCacheTags_when_notAllLegalTagsAreCached() throws Exception {
        Set<String> legaltags = Sets.newHashSet("tag1", "tag2", "tag3");

        InvalidTagsWithReason invalidTags = new InvalidTagsWithReason();
        invalidTags.setInvalidLegalTags(new InvalidTagWithReason[] {});

        when(this.cache.get("tag1")).thenReturn("cache hit");
        when(this.cache.get("tag2")).thenReturn("cache hit");
        when(this.cache.get("tag3")).thenReturn(null);

        when(this.legalService.validate("tag1", "tag2", "tag3")).thenReturn(invalidTags);

        this.sut.validateLegalTags(legaltags);

        verify(this.cache).put("tag1", "Valid LegalTag");
        verify(this.cache).put("tag2", "Valid LegalTag");
        verify(this.cache).put("tag3", "Valid LegalTag");
    }

    @Test
    public void should_throwAppExceptionWithBadRequestCode_when_anInvalidLegalTagIsReturnedFromLegalService()
            throws Exception {
        Set<String> legaltags = Sets.newHashSet("tag3");

        InvalidTagWithReason invalidTag = new InvalidTagWithReason();
        invalidTag.setName("tag3");
        invalidTag.setReason("not found");

        InvalidTagsWithReason invalidTags = new InvalidTagsWithReason();
        invalidTags.setInvalidLegalTags(new InvalidTagWithReason[] { invalidTag });

        when(this.cache.get("yxJYdg==")).thenReturn(null);
        when(this.legalService.validate("tag3")).thenReturn(invalidTags);

        try {
            this.sut.validateLegalTags(legaltags);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid legal tags", e.getError().getReason());
            assertEquals("Invalid legal tags: tag3", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwAppExceptionWithSameErrorCodeFromLegalService_when_anExceptionHappensCallingLegalService()
            throws Exception {
        Set<String> legaltags = Sets.newHashSet("tag3");

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        LegalException legalException = new LegalException("service crashed", response);

        when(this.cache.get("yxJYdg==")).thenReturn(null);
        when(this.legalService.validate("tag3")).thenThrow(legalException);

        try {
            this.sut.validateLegalTags(legaltags);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("Error validating legal tags", e.getError().getReason());
            assertEquals("An unexpected error occurred when validating legal tags", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_callLegalServiceAndCacheORDC_when_countriesAreNotCached() throws Exception {

        Set<String> countries = Sets.newHashSet("US", "FR");

        Map<String, String> countriesList = new HashMap<>();
        countriesList.put("BR", "Brazil");
        countriesList.put("US", "USA");
        countriesList.put("FR", "France");

        LegalTagProperties legalTagProperties = new LegalTagProperties();
        legalTagProperties.setOtherRelevantDataCountries(countriesList);

        when(this.legalService.getLegalTagProperties()).thenReturn(legalTagProperties);

        this.sut.validateOtherRelevantDataCountries(countries);

        verify(this.legalService, times(1)).getLegalTagProperties();

        try {
            countries.add("USS");
            this.sut.validateOtherRelevantDataCountries(countries);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid other relevant data countries", e.getError().getReason());
            assertEquals("The country code 'USS' is invalid", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwAppExceptionWithSameErrorCodeFromLegalService_when_anExceptionHappensCallingLegalServiceGettingORDC()
            throws Exception {
        Set<String> countries = Sets.newHashSet("XP");

        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        LegalException legalException = new LegalException("service crashed", response);

        when(this.legalService.getLegalTagProperties()).thenThrow(legalException);

        try {
            LegalServiceImpl.validCountryCodes = null;

            this.sut.validateOtherRelevantDataCountries(countries);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("Error getting legal tag properties", e.getError().getReason());
            assertEquals("An unexpected error occurred when getting legal tag properties", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_inheritTagsAndOrdcFromParents_when_processingDerivativesSuccessfully() {

        final String PARENT_RECORD_ID_1 = "tenant1:parent:id:111";
        final String PARENT_RECORD_ID_2 = "tenant1:parent:id:222";
        final String CURRENT_RECORD_ID = "tenant1:current:id:789";
        final Long PARENT_1_VERSION = 111L;
        final Long PARENT_2_VERSION = 222L;
        final Long CURRENT_VERSION = 456789L;

        Legal currentLegal = new Legal();
        currentLegal.setLegaltags(Sets.newHashSet("tag1", "tag2"));
        currentLegal.setOtherRelevantDataCountries(Sets.newHashSet("BR", "FR"));

        RecordAncestry currentAncestry = new RecordAncestry();
        currentAncestry.setParents(Sets.newHashSet(PARENT_RECORD_ID_1, PARENT_RECORD_ID_2));

        Record currentRecord = new Record();
        currentRecord.setId(CURRENT_RECORD_ID);
        currentRecord.setVersion(CURRENT_VERSION);
        currentRecord.setLegal(currentLegal);
        currentRecord.setAncestry(currentAncestry);

        Legal parentLegal1 = new Legal();
        parentLegal1.setLegaltags(Sets.newHashSet("tag3", "tag4"));
        parentLegal1.setOtherRelevantDataCountries(Sets.newHashSet("UK", "US"));

        Legal parentLegal2 = new Legal();
        parentLegal2.setLegaltags(Sets.newHashSet("tag5", "tag6"));
        parentLegal2.setOtherRelevantDataCountries(Sets.newHashSet("CH", "CL"));

        Record parentRecord1 = new Record();
        parentRecord1.setId(PARENT_RECORD_ID_1);
        parentRecord1.setVersion(PARENT_1_VERSION);
        parentRecord1.setLegal(parentLegal1);

        Record parentRecord2 = new Record();
        parentRecord2.setId(PARENT_RECORD_ID_2);
        parentRecord2.setVersion(PARENT_2_VERSION);
        parentRecord2.setLegal(parentLegal2);

        RecordMetadata parentRecordMetadata1 = new RecordMetadata(parentRecord1);
        RecordMetadata parentRecordMetadata2 = new RecordMetadata(parentRecord2);

        Map<String, RecordMetadata> existingRecordsMetadata = new HashMap<>();
        existingRecordsMetadata.put(PARENT_RECORD_ID_1, parentRecordMetadata1);
        existingRecordsMetadata.put(PARENT_RECORD_ID_2, parentRecordMetadata2);

        Map<String, List<RecordIdWithVersion>> recordParentMap = new HashMap<>();
        recordParentMap.put(CURRENT_RECORD_ID, Lists.newArrayList(
                RecordIdWithVersion.builder().recordId(PARENT_RECORD_ID_1).build(),
                RecordIdWithVersion.builder().recordId(PARENT_RECORD_ID_2).build()));

        this.sut.populateLegalInfoFromParents(Lists.newArrayList(currentRecord), existingRecordsMetadata,
                recordParentMap);

        assertEquals(CURRENT_RECORD_ID, currentRecord.getId());
        assertEquals(CURRENT_VERSION, currentRecord.getVersion());
        assertEquals(6, currentRecord.getLegal().getLegaltags().size());
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag1"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag2"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag3"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag4"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag5"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag6"));
        assertEquals(6, currentRecord.getLegal().getOtherRelevantDataCountries().size());
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("BR"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("FR"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("UK"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("US"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("CH"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("CL"));
        assertEquals(currentAncestry, currentRecord.getAncestry());
    }

    @Test
    public void should_inheritTagsAndOrdcFromParents_when_processingDerivativesEvenWithBlankLegalTags() {

        final String PARENT_RECORD_ID_1 = "tenant1:parent:id:111";
        final String PARENT_RECORD_ID_2 = "tenant1:parent:id:222";
        final String CURRENT_RECORD_ID = "tenant1:current:id:789";
        final Long PARENT_1_VERSION = 111L;
        final Long PARENT_2_VERSION = 222L;
        final Long CURRENT_VERSION = 456789L;

        Legal currentLegal = new Legal();

        RecordAncestry currentAncestry = new RecordAncestry();
        currentAncestry.setParents(Sets.newHashSet(PARENT_RECORD_ID_1, PARENT_RECORD_ID_2));

        Record currentRecord = new Record();
        currentRecord.setId(CURRENT_RECORD_ID);
        currentRecord.setVersion(CURRENT_VERSION);
        currentRecord.setLegal(currentLegal);
        currentRecord.setAncestry(currentAncestry);

        Legal parentLegal1 = new Legal();
        parentLegal1.setLegaltags(Sets.newHashSet("tag3", "tag4"));
        parentLegal1.setOtherRelevantDataCountries(Sets.newHashSet("UK", "US"));

        Legal parentLegal2 = new Legal();
        parentLegal2.setLegaltags(Sets.newHashSet("tag5", "tag6"));
        parentLegal2.setOtherRelevantDataCountries(Sets.newHashSet("CH", "CL"));

        Record parentRecord1 = new Record();
        parentRecord1.setId(PARENT_RECORD_ID_1);
        parentRecord1.setVersion(PARENT_1_VERSION);
        parentRecord1.setLegal(parentLegal1);

        Record parentRecord2 = new Record();
        parentRecord2.setId(PARENT_RECORD_ID_2);
        parentRecord2.setVersion(PARENT_2_VERSION);
        parentRecord2.setLegal(parentLegal2);

        RecordMetadata parentRecordMetadata1 = new RecordMetadata(parentRecord1);
        RecordMetadata parentRecordMetadata2 = new RecordMetadata(parentRecord2);

        Map<String, RecordMetadata> existingRecordsMetadata = new HashMap<>();
        existingRecordsMetadata.put(PARENT_RECORD_ID_1, parentRecordMetadata1);
        existingRecordsMetadata.put(PARENT_RECORD_ID_2, parentRecordMetadata2);

        Map<String, List<RecordIdWithVersion>> recordParentMap = new HashMap<>();
        recordParentMap.put(CURRENT_RECORD_ID, Lists.newArrayList(
                RecordIdWithVersion.builder().recordId(PARENT_RECORD_ID_1).build(),
                RecordIdWithVersion.builder().recordId(PARENT_RECORD_ID_2).build()));

        this.sut.populateLegalInfoFromParents(Lists.newArrayList(currentRecord), existingRecordsMetadata,
                recordParentMap);

        assertEquals(CURRENT_RECORD_ID, currentRecord.getId());
        assertEquals(CURRENT_VERSION, currentRecord.getVersion());
        assertEquals(4, currentRecord.getLegal().getLegaltags().size());
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag3"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag4"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag5"));
        assertTrue(currentRecord.getLegal().getLegaltags().contains("tag6"));
        assertEquals(4, currentRecord.getLegal().getOtherRelevantDataCountries().size());
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("UK"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("US"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("CH"));
        assertTrue(currentRecord.getLegal().getOtherRelevantDataCountries().contains("CL"));
        assertEquals(currentAncestry, currentRecord.getAncestry());
    }
}