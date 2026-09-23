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

package org.opengroup.osdu.storage.util;

import com.google.gson.Gson;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class DummyRecordsHelper {

    protected static final long NOW = System.currentTimeMillis();

    public final String KIND = TenantUtils.getTenantName() + ":storage:inttest:1.0.0" + NOW;

    public QueryResultMock getQueryResultMockFromResponse(CloseableHttpResponse response) {
        assertTrue(response.getEntity().getContentType().contains("application/json"));
        String json;
        try {
            json = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        Gson gson = new Gson();
        return gson.fromJson(json, QueryResultMock.class);
    }

    public RecordsMock getRecordsMockFromResponse(CloseableHttpResponse response) {
        assertTrue(response.getEntity().getContentType().contains("application/json"));
        String json = null;
        try {
            json = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        Gson gson = new Gson();
        return gson.fromJson(json, RecordsMock.class);
    }

    public ConvertedRecordsMock getConvertedRecordsMockFromResponse(CloseableHttpResponse response) {
        assertTrue(response.getEntity().getContentType().contains("application/json"));
        String json = null;
        try {
            json = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        Gson gson = new Gson();
        return gson.fromJson(json, ConvertedRecordsMock.class);
    }

    public class QueryResultMock {
        public String cursor;
        public String[] results;
    }

    public class RecordsMock {
        public RecordResultMock[] records;
        public String[] invalidRecords;
        public String[] retryRecords;
    }

    public class ConvertedRecordsMock {
        public RecordResultMock[] records;
        public String[] notFound;
        public List<RecordStatusMock> conversionStatuses;
    }

    public class RecordStatusMock {
        public String id;
        public String status;
        public List<String> errors;
    }

    public class RecordResultMock {
        public String id;
        public String version;
        public String kind;
        public RecordAclMock acl;
        public Map<String, Object> data;
        public RecordLegalMock legal;
        public RecordAncestryMock ancestry;
        public Map<String, String> tags;
        public String modifyTime;
        public String modifyUser;
        public String createTime;
        public String createUser;
    }

    public class RecordAclMock {
        public String[] viewers;
        public String[] owners;
    }

    public class RecordLegalMock {
        public String[] legaltags;
        public String[] otherRelevantDataCountries;
    }

    public class RecordAncestryMock {
        public String[] parents;
    }

    public class CreateRecordResponse {
        public int recordCount;
        public String[] recordIds;
        public String[] skippedRecordIds;
        public String[] recordIdVersions;
    }
}
