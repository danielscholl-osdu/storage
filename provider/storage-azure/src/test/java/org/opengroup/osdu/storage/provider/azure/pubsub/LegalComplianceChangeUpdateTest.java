package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.MessageBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.azure.service.LegalComplianceChangeServiceAzureImpl;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LegalComplianceChangeUpdateTest {
    private static final String emptyMessage = "{}";
    private static final String messageId = "40cc96f5-85b9-4923-9a5b-c27f67a3e815";
    private static Exception exception = null;

    @InjectMocks
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    @Mock
    private LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure;

    @Mock
    private Message message;


    @BeforeEach
    public void init() {
        lenient().when(message.getMessageId()).thenReturn(messageId);

    }

    @Test
    public void shouldRaiseInRetrieveDataFromMessage() throws Exception {
        when(message.getMessageBody()).thenReturn(getMessageBody(emptyMessage));
        try {
            legalComplianceChangeUpdate.updateCompliance(message);
        } catch (Exception ex) {
            exception = ex;
        }
        assertNotNull(exception);
    }

    private MessageBody getMessageBody(String messageValue) {
        byte[] binaryData = messageValue.getBytes();
        return MessageBody.fromBinaryData(Collections.singletonList(binaryData));
    }
}