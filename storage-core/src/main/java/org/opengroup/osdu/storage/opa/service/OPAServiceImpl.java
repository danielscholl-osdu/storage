// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.opa.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationInput;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationRequest;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationResponse;
import org.opengroup.osdu.storage.opa.model.ValidationInputRecord;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@ConfigurationProperties(prefix = "opa")
public class OPAServiceImpl implements IOPAService {

    private String opaEndpoint;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;

    private HttpClient httpClient = new HttpClient();
    private Gson gson = new Gson();

    @Override
    public List<ValidationOutputRecord> validateUserAccessToRecords(List<RecordMetadata> recordsMetadata, OperationType operationType) {
        List<ValidationInputRecord> recordsTobeValidated = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            ValidationInputRecord validationInputRecord = ValidationInputRecord.builder()
                    .id(recordMetadata.getId())
                    .kind(recordMetadata.getKind())
                    .legal(recordMetadata.getLegal())
                    .acls(recordMetadata.getAcl()).build();

            recordsTobeValidated.add(validationInputRecord);
        }

        String token = headers.getAuthorization().replace("Bearer ", "");
        CreateOrUpdateValidationInput dataAuthzInput = CreateOrUpdateValidationInput.builder()
                .datapartitionid(headers.getPartitionId())
                .token(token)
                .xuserid(headers.getUserId())
                .operation(operationType.getValue())
                .records(recordsTobeValidated).build();

        CreateOrUpdateValidationRequest dataAuthzValidationRequest = CreateOrUpdateValidationRequest.builder().input(dataAuthzInput).build();

        CreateOrUpdateValidationResponse dataAuthzValidationResponse = evaluateDataAuthorizationPolicy(dataAuthzValidationRequest);

        List<ValidationOutputRecord> result = new ArrayList<>();
        result.addAll(dataAuthzValidationResponse.getResult());
        return result;
    }

    private CreateOrUpdateValidationResponse evaluateDataAuthorizationPolicy(CreateOrUpdateValidationRequest createOrUpdateValidationRequest) {
        if (createOrUpdateValidationRequest.getInput().getRecords().isEmpty()) {
            return CreateOrUpdateValidationResponse.builder().result(Collections.emptyList()).build();
        }

        Type validationRequestType = new TypeToken<CreateOrUpdateValidationRequest>() {}.getType();
        String requestBody = gson.toJson(createOrUpdateValidationRequest, validationRequestType);

        String evaluateUrl = opaEndpoint + "/v1/data/dataauthz/records";
        logger.debug("opa url: " + evaluateUrl);
        HttpRequest httpRequest = HttpRequest.builder()
                .url(evaluateUrl)
                .httpMethod("POST")
                .body(requestBody).build();

        HttpResponse httpResponse = httpClient.send(httpRequest);
        if (httpResponse.isSuccessCode()) {
            Type validationResponseType = new TypeToken<CreateOrUpdateValidationResponse>(){}.getType();
            try {
                CreateOrUpdateValidationResponse createOrUpdateValidationResponse = gson.fromJson(httpResponse.getBody(), validationResponseType);
                if (createOrUpdateValidationResponse.getResult() == null) {
                    logger.warning("Data Authorization Policy is undefined.");
                    throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error in data authorization", "error getting data authorization result");
                }
                return createOrUpdateValidationResponse;
            } catch (JsonSyntaxException ex) {
                logger.warning(String.format("Error generating response from OPA: %s", ex.getMessage()), ex);
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error in data authorization", "error getting data authorization result", ex);
            }
        }
        logger.warning(String.format("Failure when calling OPA with response code %d, response body: %s", httpResponse.getResponseCode(), httpResponse.getBody()));
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error in data authorization", "error getting data authorization result");
    }

    // for unit testing purpose
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
