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

import java.util.Arrays;
import java.util.Map;
import lombok.Data;

@Data
public class CreatedRecordInStorage {
    public int recordCount;
    public String[] recordIds;
    public String[] skippedRecordIds;

    @Override
    public String toString() {
        return "CreatedRecordInStorage{" +
                "recordCount=" + recordCount +
                ", recordIds=" + Arrays.toString(recordIds) +
                ", skippedRecordIds=" + Arrays.toString(skippedRecordIds) +
                '}';
    }
    public class RecordResult {
        public String id;
        public String version;
        public String kind;
        public RecordAcl acl;
        public Map<String, Object> data;
        public RecordLegal legal;
        public RecordAncestry ancestry;
    }

    public class RecordAcl {
        public String[] viewers;
        public String[] owners;
    }

    public class RecordLegal {
        public String[] legaltags;
        public String[] otherRelevantDataCountries;
    }

    public class RecordAncestry {
        public String[] parents;
    }
}
