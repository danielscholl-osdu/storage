/*
 *  Copyright @ Microsoft Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.web.model;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.request.ReplayFilter;

class ReplayMetaDataTest {

    @Test
    void allArgsConstructor_ShouldSetAllFields() {
        Date date = new Date();
        ReplayFilter filter = new ReplayFilter();

        ReplayMetaData metadata = new ReplayMetaData(
                "id1", "replayId1", "kind1", "operation1", 100L, date, filter, 50L, "state1", "10s"
        );

        assertEquals("id1", metadata.getId());
        assertEquals("replayId1", metadata.getReplayId());
        assertEquals("kind1", metadata.getKind());
        assertEquals("operation1", metadata.getOperation());
        assertEquals(100L, metadata.getTotalRecords());
        assertEquals(date, metadata.getStartedAt());
        assertEquals(filter, metadata.getFilter());
        assertEquals(50L, metadata.getProcessedRecords());
        assertEquals("state1", metadata.getState());
        assertEquals("10s", metadata.getElapsedTime());
    }

    @Test
    void builder_ShouldCreateInstanceWithAllFields() {
        Date date = new Date();
        ReplayFilter filter = new ReplayFilter();

        ReplayMetaData metadata = ReplayMetaData.builder()
                .id("id1")
                .replayId("replayId1")
                .kind("kind1")
                .operation("operation1")
                .totalRecords(100L)
                .startedAt(date)
                .filter(filter)
                .processedRecords(50L)
                .state("state1")
                .elapsedTime("10s")
                .build();

        assertEquals("id1", metadata.getId());
        assertEquals("replayId1", metadata.getReplayId());
        assertEquals("kind1", metadata.getKind());
        assertEquals("operation1", metadata.getOperation());
        assertEquals(100L, metadata.getTotalRecords());
        assertEquals(date, metadata.getStartedAt());
        assertEquals(filter, metadata.getFilter());
        assertEquals(50L, metadata.getProcessedRecords());
        assertEquals("state1", metadata.getState());
        assertEquals("10s", metadata.getElapsedTime());
    }

    @Test
    void builder_ShouldCreateInstanceWithPartialFields() {
        ReplayMetaData metadata = ReplayMetaData.builder()
                .id("id1")
                .replayId("replayId1")
                .build();

        assertEquals("id1", metadata.getId());
        assertEquals("replayId1", metadata.getReplayId());
        assertNull(metadata.getKind());
        assertNull(metadata.getOperation());
    }

    @Test
    void equals_BasicContract() {
        ReplayMetaData m = new ReplayMetaData();
        
        // Reflexivity
        assertEquals(m, m);
        
        // Null check
        assertNotEquals(null, m);
        
        // Different type
        assertNotEquals(m, new Object());
    }

    @Test
    void equals_AllFieldsEqual() {
        Date date = new Date();
        ReplayFilter filter = new ReplayFilter();

        ReplayMetaData m1 = full("id", "r", "k", "op", 100L, date, filter, 50L, "s", "10s");
        ReplayMetaData m2 = full("id", "r", "k", "op", 100L, date, filter, 50L, "s", "10s");

        assertEquals(m1, m2);
    }

    @Test
    void equals_DifferentStartedAt() {
        ReplayFilter f = new ReplayFilter();
        ReplayMetaData m1 = full("id", "r", "k", "op", 100L, new Date(1000), f, 50L, "s", "10s");
        ReplayMetaData m2 = full("id", "r", "k", "op", 100L, new Date(2000), f, 50L, "s", "10s");
        assertNotEquals(m1, m2);
    }

    private ReplayMetaData full(String id, String replayId, String kind, String operation,
                                Long totalRecords, Date startedAt, ReplayFilter filter,
                                Long processedRecords, String state, String elapsedTime) {
        ReplayMetaData m = new ReplayMetaData();
        m.setId(id);
        m.setReplayId(replayId);
        m.setKind(kind);
        m.setOperation(operation);
        m.setTotalRecords(totalRecords);
        m.setStartedAt(startedAt);
        m.setFilter(filter);
        m.setProcessedRecords(processedRecords);
        m.setState(state);
        m.setElapsedTime(elapsedTime);
        return m;
    }
}
