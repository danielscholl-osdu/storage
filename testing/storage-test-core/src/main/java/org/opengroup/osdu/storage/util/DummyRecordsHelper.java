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

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class DummyRecordsHelper {

	protected static final long NOW = System.currentTimeMillis();

	public final String KIND = TenantUtils.getTenantName() + ":storage:inttest:1.0.0" + NOW;
	public final String ID = TenantUtils.getTenantName() + ":inttest-Multi-Client:flatten-full-seismic"
			+ NOW;
	public final String KIND2 = this.KIND;
	public final String ID2 = TenantUtils.getTenantName() + ":inttest-Multi-Client:flatten-full-seismic2"
			+ NOW;
	public final String KIND3 = this.KIND + "1";
	public final String ID3 = TenantUtils.getTenantName() + ":inttest-Multi-Client:flatten-full-seismic3"
			+ NOW;

	public QueryResultMock getQueryResultMockFromResponse(ClientResponse response) {
		assertEquals("application/json; charset=UTF-8", response.getType().toString());
		String json = response.getEntity(String.class);
		Gson gson = new Gson();
		return gson.fromJson(json, QueryResultMock.class);
	}

	public RecordsMock getRecordsMockFromResponse(ClientResponse response) {
		assertEquals("application/json; charset=UTF-8", response.getType().toString());
		String json = response.getEntity(String.class);
		Gson gson = new Gson();
		return gson.fromJson(json, RecordsMock.class);
	}

	public ConvertedRecordsMock getConvertedRecordsMockFromResponse(ClientResponse response) {
		assertEquals("application/json; charset=UTF-8", response.getType().toString());
		String json = response.getEntity(String.class);
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