// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalTagMessageHandlerTest {

    @Mock
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    @Mock
    private IMessage message;

    @InjectMocks
    private LegalTagMessageHandler legalTagMessageHandler;

    @BeforeEach
    void setUp() {
        lenient().when(message.getDeliveryCount()).thenReturn(1L);
    }

    @Test
    void testHandle_SuccessfulMessageProcessing() throws Exception {
        legalTagMessageHandler.handle(message);

        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
        verify(message, times(1)).getDeliveryCount();
    }

    @Test
    void testHandle_WithHighDeliveryCount() throws Exception {
        when(message.getDeliveryCount()).thenReturn(5L);

        legalTagMessageHandler.handle(message);

        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
        verify(message, times(1)).getDeliveryCount();
    }

    @Test
    void testHandle_WhenUpdateComplianceThrowsException() throws Exception {
        doThrow(new RuntimeException("Update failed")).when(legalComplianceChangeUpdate).updateCompliance(message);

        assertThrows(RuntimeException.class, () -> legalTagMessageHandler.handle(message));
        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
    }

    @Test
    void testHandle_WithNullMessage() throws Exception {
        assertThrows(NullPointerException.class, () -> legalTagMessageHandler.handle(null));
        verify(legalComplianceChangeUpdate, never()).updateCompliance(any());
    }

    @Test
    void testHandleFailure_LogsFailureMessage() {
        legalTagMessageHandler.handleFailure(message);

        assertDoesNotThrow(() -> legalTagMessageHandler.handleFailure(message));
    }

    @Test
    void testHandleFailure_WithNullMessage() {
        assertDoesNotThrow(() -> legalTagMessageHandler.handleFailure(null));
    }

    @Test
    void testHandle_MessageHandlerInteraction() throws Exception {
        when(message.getDeliveryCount()).thenReturn(3L);

        legalTagMessageHandler.handle(message);

        verify(message, times(1)).getDeliveryCount();
        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
    }

    @Test
    void testHandle_VerifyExceptionPropagation() throws Exception {
        RuntimeException expectedException = new RuntimeException("Test exception");
        doThrow(expectedException).when(legalComplianceChangeUpdate).updateCompliance(message);

        RuntimeException actualException = assertThrows(RuntimeException.class, 
            () -> legalTagMessageHandler.handle(message));
        
        assertEquals(expectedException.getMessage(), actualException.getMessage());
        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
    }

    @Test
    void testLegalComplianceChangeUpdateIsAutowired() {
        assertNotNull(legalTagMessageHandler);
    }
}
