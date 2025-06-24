package org.opengroup.osdu.storage.provider.azure.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class MDCContextMapTest {
    @InjectMocks
    MDCContextMap mdcContextMap;

    @Test
    void getContextMap_ReturnsCorrectContextMap() {
        Map<String, String> contextMap = mdcContextMap.getContextMap("correlation-id", "data-partition-id");

        assertEquals(2, contextMap.size());
    }
}
