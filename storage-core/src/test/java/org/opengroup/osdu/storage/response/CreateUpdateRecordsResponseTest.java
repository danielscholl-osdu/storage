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

package org.opengroup.osdu.storage.response;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;

public class CreateUpdateRecordsResponseTest {

    @Test
    public void should_returnRecordIdsSkippedRecordIdsInMutualExclusiveListsPlusTotalRecordCountInResponse_when_creatingPutResponse() {

        Record r1 = new Record();
        r1.setId("my id 1");

        Record r2 = new Record();
        r2.setId("my id 2");

        Record r3 = new Record();
        r3.setId("my id 3");

        Record r4 = new Record();
        r4.setId("my id 4");

        TransferInfo transfer = new TransferInfo();
        transfer.setRecordCount(4);
        transfer.setSkippedRecords(Lists.newArrayList("my id 4", "my id 1"));

        List<Record> records = Lists.newArrayList(r1, r2, r3, r4);

        CreateUpdateRecordsResponse response = new CreateUpdateRecordsResponse(transfer, records);

        assertEquals(new Integer(4), response.getRecordCount());
        assertArrayEquals(new String[] { "my id 2", "my id 3" }, response.getRecordIds().toArray());
        assertArrayEquals(new String[] { "my id 4", "my id 1" }, response.getSkippedRecordIds().toArray());
    }
}