package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.MessageBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.azure.exception.ServiceBusInvalidMessageBodyException;
import org.opengroup.osdu.storage.provider.azure.config.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.slf4j.Logger;


import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalComplianceChangeUpdateTest {
    private static final String messageId = "40cc96f5-85b9-4923-9a5b-c27f67a3e815";
    private static final String validStatusLegalTagChangedMessage = "{\"message\":{\"data\":{\"statusChangedTags\":[{\"changedTagName\":\"opendes-Test-Legal-Tag-expired1\",\"changedTagStatus\":\"incompliant\"},{\"changedTagName\":\"opendes-Test-Legal-Tag-expired2\",\"changedTagStatus\":\"incompliant\"},{\"changedTagName\":\"opendes-Test-Legal-Tag-not-expired\",\"changedTagStatus\":\"compliant\"}]},\"data-partition-id\":\"opendes\",\"correlation-id\":\"000000000-0000-0000-0000-000000000000\",\"user\":\"00000000-0000-0000-0000-000000000000\"}}";
    private static final String expectedJsonParseExceptionMessage = "The legal tags changed request in the service bus message body could not be parsed into LegalTagsChangedRequest";

    @InjectMocks
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    @Mock
    private Logger logger;

    @Mock
    private MDCContextMap mdcContextMap;

    @Mock
    private Message message;

    @Mock
    private ThreadDpsHeaders headers;

    @Mock
    private ComplianceMessagePullReceiver complianceMessagePullReceiver;

    private MessageBody getMessageBody(String messageValue) {
        byte[] binaryData = messageValue.getBytes();
        return MessageBody.fromBinaryData(Collections.singletonList(binaryData));
    }

    @BeforeEach
    void init() throws Exception {
        Field headersField = LegalComplianceChangeUpdate.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        headersField.set(legalComplianceChangeUpdate, headers);

        Field mdcContextMapField = LegalComplianceChangeUpdate.class.getDeclaredField("mdcContextMap");
        mdcContextMapField.setAccessible(true);
        mdcContextMapField.set(legalComplianceChangeUpdate, mdcContextMap);

        Field complianceMessagePullReceiverField = LegalComplianceChangeUpdate.class.getDeclaredField("complianceMessagePullReceiver");
        complianceMessagePullReceiverField.setAccessible(true);
        complianceMessagePullReceiverField.set(legalComplianceChangeUpdate, complianceMessagePullReceiver);

        lenient().when(message.getMessageId()).thenReturn(messageId);
    }

    @Test
    void shouldRaiseServiceBusInvalidMessageBodyException_whenRetrieveDataFromEmptyMessage() {
        final String emptyJsonMessage = "{}";
        when(message.getMessageBody()).thenReturn(getMessageBody(emptyJsonMessage));
        ServiceBusInvalidMessageBodyException ex = assertThrows(ServiceBusInvalidMessageBodyException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));
        assertEquals("The service bus message field is null in the legal tags changed request", ex.getMessage());
        verify(logger, times(1)).error(
            eq(String.format("Error occurred when retrieving the message from the service bus: %s", ex.getMessage())),
            any(ServiceBusInvalidMessageBodyException.class)
        );
    }

    @Test
    void shouldRaiseServiceBusInvalidMessageBodyException_whenInvalidMessageBody() {
        final String invalidServiceBusMessageBody = "{\"some_test\":{\"test\":\"hello_world\"}}";
        when(message.getMessageBody()).thenReturn(getMessageBody(invalidServiceBusMessageBody));
        ServiceBusInvalidMessageBodyException ex = assertThrows(ServiceBusInvalidMessageBodyException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));
        assertEquals("The service bus message field is null in the legal tags changed request", ex.getMessage());
        verify(logger, times(1)).error(
            eq(String.format("Error occurred when retrieving the message from the service bus: %s", ex.getMessage())),
            any(ServiceBusInvalidMessageBodyException.class)
        );
    }

    @Test
    void shouldRaiseServiceBusInvalidMessageBodyException_whenNullMessage() {
        ServiceBusInvalidMessageBodyException ex = assertThrows(ServiceBusInvalidMessageBodyException.class, () -> legalComplianceChangeUpdate.updateCompliance(null));

        verify(logger, times(1)).error(
            eq(String.format("Error occurred when retrieving the message from the service bus: %s", ex.getMessage())),
            any(ServiceBusInvalidMessageBodyException.class)
        );
    }

    @Test
    void shouldLogMissingStatusChangedTags_whenStatusChangedTagsMissing() throws Exception {
        final String invalidStatusLegalTagChangedMessage = "{\"message\":{\"data\":{\"legalTags\":[{\"tagName\":\"opendes-Test-Legal-Tag-not-expired\",\"expirationDate\":\"Sep 6, 2099\"}]},\"data-partition-id\":\"opendes\",\"correlation-id\":\"dd4bab9e-6e88-40d8-90b1-b35a7b89d69f\",\"user\":\"60c4b736-2aa4-4889-88a0-d50503d63de7\"}}";
        when(message.getMessageBody()).thenReturn(getMessageBody(invalidStatusLegalTagChangedMessage));

        legalComplianceChangeUpdate.updateCompliance(message);

        verify(logger, times(1)).info(
            eq(String.format("Message in service bus does not contain statusChangedTags. Ignoring message with id: %s", messageId))
        );
    }

    @Test
    void updateComplianceShouldUpdateThePullReceiver_whenMessageIsValid() throws Exception {
        when(message.getMessageBody()).thenReturn(getMessageBody(validStatusLegalTagChangedMessage));

        legalComplianceChangeUpdate.updateCompliance(message);

        verify(logger, times(1)).info(
            eq(String.format("Received a message from the service bus with message ID %s", messageId))
        );
        verify(logger, times(1)).info(
            eq("Deleting associated records for the incompliant legal tag: opendes-Test-Legal-Tag-expired1")
        );

        verify(logger, times(1)).info(
            eq("Deleting associated records for the incompliant legal tag: opendes-Test-Legal-Tag-expired2")
        );

        // Verify that a compliant and non-expired legal tag is not deleted.
        verify(logger, times(0)).info(
            eq("Deleting associated records for the incompliant legal tag: opendes-Test-Legal-Tag-not-expired")
        );
        verify(complianceMessagePullReceiver).receiveMessage(any(), any());
    }

    @Test
    void updateComplianceShouldThrowAppException_whenComplianceMessagePullReceiverThrowsAppException() throws Exception {
        when(message.getMessageBody()).thenReturn(getMessageBody(validStatusLegalTagChangedMessage));
        AppException exception = new AppException(403, "some error", "some error");
        Mockito.doThrow(exception).when(complianceMessagePullReceiver).receiveMessage(any(), any());

        AppException actualException = assertThrows(AppException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));

        verify(complianceMessagePullReceiver).receiveMessage(any(), any());
        assertEquals(exception.getMessage(), actualException.getError().getMessage());

        verify(logger, times(1)).error(
            eq(String.format("Error occurred when updating compliance on records: %s", actualException.getMessage())),
            any(AppException.class)
        );
    }

    @Test
    void updateComplianceShouldThrowException_whenComplianceMessagePullReceiverThrowsException() throws Exception {
        when(message.getMessageBody()).thenReturn(getMessageBody(validStatusLegalTagChangedMessage));
        Exception exception = new RuntimeException("some error");

        doThrow(exception).when(complianceMessagePullReceiver).receiveMessage(any(), any());

        Exception actualException = assertThrows(Exception.class, () -> legalComplianceChangeUpdate.updateCompliance(message));

        verify(complianceMessagePullReceiver).receiveMessage(any(), any());
        assertEquals(exception.getMessage(), actualException.getMessage());

        verify(logger, times(1)).error(
            eq(String.format("Error occurred when updating compliance on records: %s", exception.getMessage())),
            any(Exception.class)
        );
    }

    @Test
    void shouldRaiseJsonException_whenLegalTagRequestIsEmpty() {
        when(message.getMessageBody()).thenReturn(getMessageBody(""));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));
        assertEquals(expectedJsonParseExceptionMessage, ex.getMessage());
        verify(logger, times(1)).error(
            eq(String.format("Error occurred when parsing the legal tags changed request from the service bus: %s", ex.getMessage())),
            any(Exception.class)
        );
    }

    @Test
    void shouldRaiseJsonParseException_whenInvalidJsonInMessageBody() {
        final String invalidJSON = "{\"test\":}";
        when(message.getMessageBody()).thenReturn(getMessageBody(invalidJSON));
        JsonSyntaxException ex = assertThrows(JsonSyntaxException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));
        verify(logger, times(1)).error(
            eq(String.format("Error occurred when parsing the JSON message from the service bus: %s", ex.getMessage())),
            any(Exception.class)
        );
    }

    @Test
    void shouldRaiseJsonParseException_whenGsonThrowsJsonSyntaxException() {
        final String invalidLegalTagsChangedRequest = "{\"message\": {\"data\": 123, \"correlation-id\": true}}";
        when(message.getMessageBody()).thenReturn(getMessageBody(invalidLegalTagsChangedRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> legalComplianceChangeUpdate.updateCompliance(message));
        assertTrue(ex.getMessage().startsWith("The legal tags changed request in the service bus message body could not be parsed into LegalTagsChangedRequest due to"));
        verify(logger, times(1)).error(
            eq(String.format("Error occurred when parsing the legal tags changed request from the service bus: %s", ex.getMessage())),
            any(Exception.class)
        );
    }
}
