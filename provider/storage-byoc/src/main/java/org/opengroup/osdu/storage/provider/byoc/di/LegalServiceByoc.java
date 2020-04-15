// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.byoc.di;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.legal.ILegalProvider;
import org.opengroup.osdu.core.common.model.legal.LegalException;
import org.opengroup.osdu.core.common.model.legal.InvalidTagWithReason;
import org.opengroup.osdu.core.common.model.legal.InvalidTagsWithReason;
import org.opengroup.osdu.core.common.model.legal.LegalTag;
import org.opengroup.osdu.core.common.model.legal.LegalTagProperties;

import java.util.HashMap;
import java.util.Map;

public class LegalServiceByoc implements ILegalProvider {
    DpsHeaders headers;

    public LegalServiceByoc(DpsHeaders headers){
        this.headers = headers;
    }
    @Override
    public LegalTag create(LegalTag legalTag) throws LegalException {
        return null;
    }

    @Override
    public void delete(String s) throws LegalException {

    }

    @Override
    public LegalTag get(String s) throws LegalException {
        return null;
    }

    @Override
    public LegalTagProperties getLegalTagProperties() throws LegalException {
        LegalTagProperties props = new LegalTagProperties();

        Map<String, String> countriesOfOrigin = new HashMap<>();
        countriesOfOrigin.put("US", "origin");
        props.setOtherRelevantDataCountries(countriesOfOrigin);

        Map<String, String> otherRelavantDataCountries = new HashMap<>();
        otherRelavantDataCountries.put("US", "relevant");
        props.setOtherRelevantDataCountries(otherRelavantDataCountries);

        return props;
    }

    @Override
    public InvalidTagsWithReason validate(String... strings) throws LegalException {
        InvalidTagsWithReason tags = new InvalidTagsWithReason();
        InvalidTagWithReason[] tagArr = new InvalidTagWithReason[]{};
        tags.setInvalidLegalTags(tagArr);
        return tags;
    }
}
