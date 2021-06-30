package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.MessageBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private static final String validIMessage = "{\"id\":\"40cc96f5-85b9-4923-9a5b-c27f67a3e815\",\"subject\":\"legaltagschanged\",\"data\":{\"deliveryCount\":0,\"messageId\":\"9aa0cb2c-baf6-4dcb-ae5a-c29aecca59cd\",\"messageBody\":{\"bodyType\":\"BINARY\",\"binaryData\":[\"eyJtZXNzYWdlIjp7ImRhdGEiOnsic3RhdHVzQ2hhbmdlZFRhZ3MiOlt7ImNoYW5nZWRUYWdOYW1lIjoib3BlbmRlcy1wdWJsaWMtdXNhLWRhdGFzZXQtMSIsImNoYW5nZWRUYWdTdGF0dXMiOiJpbmNvbXBsaWFudCJ9LHsiY2hhbmdlZFRhZ05hbWUiOiJvcGVuZGVzLXN0b3JhZ2UtMTYwMTk5MTMwNzkzMCIsImNoYW5nZWRUYWdTdGF0dXMiOiJpbmNvbXBsaWFudCJ9XX0sImFjY291bnQtaWQiOiJvcGVuZGVzIiwiZGF0YS1wYXJ0aXRpb24taWQiOiJvcGVuZGVzIiwiY29ycmVsYXRpb24taWQiOiI5NWFiMTVkZC00NjYzLTRjMmYtYjZmZS1kNjdiNjI1ODU4ZWEiLCJ1c2VyIjoiYTM4ZmRkN2ItZjIwOS00NTUyLTk2Y2QtMTI2ZWMyNDk0NjA1In19\"]},\"contentType\":\"application/json\",\"sequenceNumber\":0,\"properties\":{\"account-id\":\"opendes\",\"correlation-id\":\"95ab15dd-4663-4c2f-b6fe-d67b625858ea\",\"user\":\"a38fdd7b-f209-4552-96cd-126ec2494605\",\"data-partition-id\":\"opendes\"}},\"eventType\":\"legaltagschanged\",\"dataVersion\":\"1.0\",\"metadataVersion\":\"1\",\"eventTime\":\"2021-06-18T20:33:50.038Z\",\"topic\":\"/subscriptions/7c052588-ead2-45c9-9346-5b156a157bd1/resourceGroups/osdu-mvp-dp1dev-qs29-rg/providers/Microsoft.EventGrid/topics/osdu-mvp-dp1dev-qs29-grid-legaltagschangedtopic\"}";

    @InjectMocks
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    @Mock
    private LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure;

    @Mock
    private MDCContextMap mdcContextMap;

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

    @Test
    public void shouldRaiseNullPointerException() throws Exception {

        when(message.getMessageBody()).thenReturn(getMessageBody(validIMessage));
        try {
            legalComplianceChangeUpdate.updateCompliance(message);
            verify(message, times(1)).getMessageBody();
            verify(message, times(1)).setMessageId(messageId);
        }
        catch (NullPointerException ex){
            exception = ex;
        }
        assertNotNull(exception);
    }
    private MessageBody getMessageBody(String messageValue) {
        byte[] binaryData = messageValue.getBytes();
        return MessageBody.fromBinaryData(Collections.singletonList(binaryData));
    }
}