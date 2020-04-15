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

package org.opengroup.osdu.storage.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;

public class TransferInfoTest {

    @Test
    public void should_newTransferInfoWithVersionEqualsToTransferId_when_createObjectWithoutTransferId() {

        TransferInfo sut = new TransferInfo("testUser", 10);

        assertEquals("testUser", sut.getUser());
        assertEquals(new Integer(10), sut.getRecordCount());
        assertTrue(sut.getVersion() > 0);
    }

    @Test
    public void should_newTransferWithVersionDifferentThanTransferId_when_createObjectWithTransferId() {
        TransferInfo sut = new TransferInfo("anyone", 10);

        assertEquals("anyone", sut.getUser());
        assertEquals(new Integer(10), sut.getRecordCount());
        assertTrue(sut.getVersion() > 0);
    }
}