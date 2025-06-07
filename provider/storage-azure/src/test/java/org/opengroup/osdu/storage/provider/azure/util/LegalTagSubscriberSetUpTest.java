package org.opengroup.osdu.storage.provider.azure.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.storage.provider.azure.pubsub.LegalTagSubscriptionManagerImpl;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalTagSubscriberSetUpTest {
    @InjectMocks
    LegalTagSubscriberSetUp sut;
    @Mock
    ContextRefreshedEvent contextRefreshedEvent;
    @Mock
    private LegalTagSubscriptionManagerImpl legalTagSubscriptionManager;

    @Test
    void onApplicationEvent_doesNothing_whenLegalTagComplianceUpdateEnabled_IsFalse() {
        ReflectionTestUtils.setField(sut, "legalTagComplianceUpdateEnabled", false);

        sut.onApplicationEvent(contextRefreshedEvent);

        verify(legalTagSubscriptionManager, never()).subscribeLegalTagsChangeEvent();
    }

    @Test
    void onApplicationEvent_subscribesToLegalTagChangedEvent_whenLegalTagComplianceUpdateEnabled_IsTrue() {
        ReflectionTestUtils.setField(sut, "legalTagComplianceUpdateEnabled", true);

        sut.onApplicationEvent(contextRefreshedEvent);

        verify(legalTagSubscriptionManager, times(1)).subscribeLegalTagsChangeEvent();
    }
}
