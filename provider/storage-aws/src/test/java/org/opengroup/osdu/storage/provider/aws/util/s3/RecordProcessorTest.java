/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.util.s3;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;

class RecordProcessorTest {
    @Mock
    private RecordProcessing recordProcessing;
    @Mock
    private RecordMetadata recordMetadata;
    @Mock
    private S3RecordClient s3Client;
    @Mock
    private  AwsServiceException exception;
    public CallableResult result;
    private static final String DATA_PARTITION = "some-partition";
    private static final String RECORD_ID = "some-record-id";
    private AutoCloseable toClose;

    @BeforeEach
    void setUp() {
        toClose = openMocks(this);
        when(recordProcessing.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.getId()).thenReturn(RECORD_ID);
    }

    @AfterEach
    void teardown() throws Throwable {
        toClose.close();
    }
    @Test
    void should_correctlySaveRecord_when_callIsCalled() {
        RecordProcessor recordProcessor = new RecordProcessor(recordProcessing, s3Client, DATA_PARTITION);
        recordProcessor.call();
        verify(s3Client, times(1)).saveRecord(recordProcessing, DATA_PARTITION);
        assertNull(recordProcessor.getException());
        assertEquals(CallableResult.PASS, recordProcessor.getResult());
        assertEquals(RECORD_ID, recordProcessor.getRecordId());
    }

    @Test
    void should_setError_when_s3Errors() {
        doThrow(exception).when(s3Client).saveRecord(recordProcessing, DATA_PARTITION);
        RecordProcessor recordProcessor = new RecordProcessor(recordProcessing, s3Client, DATA_PARTITION);
        recordProcessor.call();
        verify(s3Client, times(1)).saveRecord(recordProcessing, DATA_PARTITION);
        assertEquals(exception, recordProcessor.getException());
        assertEquals(CallableResult.FAIL, recordProcessor.getResult());
        assertEquals(RECORD_ID, recordProcessor.getRecordId());
    }
}
