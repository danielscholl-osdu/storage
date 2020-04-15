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

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RecordMetadataTest {

    private static final String PATH_1 = "kind/id/123";
    private static final String PATH_2 = "kind/id/456";
    private static final String PATH_3 = "kind/id/789";

    private RecordMetadata sut;

    @Before
    public void setup() {
        this.sut = new RecordMetadata();
        this.sut.setKind("kind");
        this.sut.setId("id");
        this.sut.setStatus(RecordState.active);
        this.sut.setGcsVersionPaths(Lists.newArrayList(PATH_1, PATH_2, PATH_3));
    }

    @Test
    public void should_returnLatestVersion() {
        assertTrue(789L == this.sut.getLatestVersion());
    }

    @Test
    public void should_addGcsPath() {
        this.sut.addGcsPath(1000);

        assertTrue(1000L == this.sut.getLatestVersion());
    }

    @Test
    public void should_resetGcsPath() {
        List<String> gcsVersionList = new ArrayList<>();
        gcsVersionList.add("123");
        gcsVersionList.add("456");
        this.sut.resetGcsPath(gcsVersionList);

        assertTrue(2 == this.sut.getGcsVersionPaths().size());
        assertTrue(456L == this.sut.getLatestVersion());
    }
}
