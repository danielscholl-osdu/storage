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

package org.opengroup.osdu.storage.service;

import com.lambdaworks.redis.RedisException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.legal.ILegalFactory;
import org.opengroup.osdu.core.common.legal.ILegalProvider;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.InvalidTagWithReason;
import org.opengroup.osdu.core.common.model.legal.InvalidTagsWithReason;
import org.opengroup.osdu.core.common.model.legal.LegalException;
import org.opengroup.osdu.core.common.model.legal.LegalTagProperties;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordIdWithVersion;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LegalServiceImpl implements ILegalService {

    protected static final String LEGAL_PROPERTIES_KEY = "@legal-properties";
    protected static final String DEFAULT_DATA_COUNTRY = "US";
    public static Map<String, String> validCountryCodes;
    @Autowired
    private DpsHeaders headers;
    @Autowired
    private ICache<String, String> cache;
    @Autowired
    private ILegalFactory factory;
    @Autowired
    private JaxRsDpsLog log;

    @Override
    public void validateLegalTags(Set<String> legaltags) {

        if (this.isInCache(legaltags)) {
            return;
        }

        InvalidTagWithReason[] invalidLegalTags = this.getInvalidLegalTags(legaltags);

        if (invalidLegalTags.length > 0) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid legal tags",
                    String.format("Invalid legal tags: %s", invalidLegalTags[0].getName()));
        }

        this.addToCache(legaltags);
    }

    @Override
    public void validateOtherRelevantDataCountries(Set<String> ordc) {

        Map<String, String> validCountries = this.getValidCountryCodes();

        ordc.add(DEFAULT_DATA_COUNTRY);

        for (String country : ordc) {
            if (!validCountries.containsKey(country)) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid other relevant data countries",
                        String.format("The country code '%s' is invalid", country));
            }
        }
    }

    @Override
    public void populateLegalInfoFromParents(List<Record> inputRecords,
                                             Map<String, RecordMetadata> existingRecordsMetadata,
                                             Map<String, List<RecordIdWithVersion>> recordParentMap) {

        for (Record record : inputRecords) {
            // is there parent?
            if (recordParentMap.containsKey(record.getId())) {
                for (RecordIdWithVersion parentRecordId : recordParentMap.get(record.getId())) {
                    RecordMetadata parentRecord = existingRecordsMetadata.get(parentRecordId.getRecordId());

                    if (!record.getLegal().hasLegaltags()) {
                        record.getLegal().setLegaltags(parentRecord.getLegal().getLegaltags());
                    } else {
                        record.getLegal().getLegaltags().addAll(parentRecord.getLegal().getLegaltags());
                    }

                    if (record.getLegal().getOtherRelevantDataCountries() != null) {
                        record.getLegal().getOtherRelevantDataCountries()
                                .addAll(parentRecord.getLegal().getOtherRelevantDataCountries());
                    } else {
                        record.getLegal()
                                .setOtherRelevantDataCountries(parentRecord.getLegal().getOtherRelevantDataCountries());
                    }
                }
            }
        }
    }

    public Map<String, String> getValidCountryCodes() {
        if (validCountryCodes == null) {
            try {
                ILegalProvider legalService = this.factory.create(this.headers);
                LegalTagProperties legalTagProperties = legalService.getLegalTagProperties();
                return validCountryCodes = legalTagProperties.getOtherRelevantDataCountries();
            } catch (LegalException e) {
                throw new AppException(e.getHttpResponse().getResponseCode(), "Error getting legal tag properties",
                        "An unexpected error occurred when getting legal tag properties", e);
            }
        }

        return validCountryCodes;
    }

    @Override
    public InvalidTagWithReason[] getInvalidLegalTags(Set<String> legalTagNames) {
        try {
            ILegalProvider legalService = this.factory.create(this.headers);
            InvalidTagsWithReason response = legalService
                    .validate(legalTagNames.toArray(new String[legalTagNames.size()]));
            return response.getInvalidLegalTags();
        } catch (LegalException e) {
            throw new AppException(e.getHttpResponse().getResponseCode(), "Error validating legal tags",
                    "An unexpected error occurred when validating legal tags", e);
        }
    }

    private boolean isInCache(Set<String> legalTagNames) {
        for (String legalTagName : legalTagNames) {
            String legalTag = null;
            try {
                legalTag = this.cache.get(legalTagName);
            } catch (RedisException ex) {
                this.log.error(String.format("Error getting key %s from redis: %s", legalTagName, ex.getMessage()), ex);
            }
            if (legalTag == null) {
                return false;
            }
        }
        return true;
    }

    private void addToCache(Set<String> legalTagNames) {
        for (String legalTagName : legalTagNames) {
            this.cache.put(legalTagName, "Valid LegalTag");
        }
    }
}