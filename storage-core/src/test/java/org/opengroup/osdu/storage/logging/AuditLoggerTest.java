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

package org.opengroup.osdu.storage.logging;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.mockito.runners.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class AuditLoggerTest {

    @Mock
    private JaxRsDpsLog log;

    @InjectMocks
    private StorageAuditLogger sut;

    @Mock
    private DpsHeaders dpsHeaders;


    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(dpsHeaders.getUserEmail()).thenReturn("user");
    }

    @Test
    public void should_writeCreateOrUpdateRecordsEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.createOrUpdateRecordsSuccess(resource);
        this.sut.createOrUpdateRecordsFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writeDeleteRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.deleteRecordSuccess(resource);
        this.sut.deleteRecordFail(resource);

        verify(this.log,times(2)).audit(any());
    }

    @Test
    public void should_writePurgeRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.purgeRecordSuccess(resource);
        this.sut.purgeRecordFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writeReadAllVersionsRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readAllVersionsOfRecordSuccess(resource);
        this.sut.readAllVersionsOfRecordFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writeReadSpecificVersionRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readSpecificVersionOfRecordSuccess(resource);
        this.sut.readSpecificVersionOfRecordFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writeReadRecordLatestVersionEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readLatestVersionOfRecordSuccess(resource);
        this.sut.readLatestVersionOfRecordFail(resource);

        verify(this.log,times(2)).audit(any());
    }

    @Test
    public void should_writeReadMultipleRecordsEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeReadAllRecordsOfGivenKindEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readAllRecordsOfGivenKindSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeReadAllKindsEvent() {
      List<String> resource = Collections.singletonList("1");
        this.sut.readAllKindsSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeCreateSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.createSchemaSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeDeleteSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.deleteSchemaSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeReadSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readSchemaSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_updateRecordComplianceStateEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.updateRecordsComplianceStateSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_readMultipleRecordsWithOptionalConversionSuccessEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsWithOptionalConversionSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_readMultipleRecordsWithOptionalConversionFailEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsWithOptionalConversionFail(resource);

        verify(this.log).audit(any());
    }
}

