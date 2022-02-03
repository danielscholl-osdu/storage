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
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationInput;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationRequest;
import org.opengroup.osdu.storage.opa.model.CreateOrUpdateValidationResponse;
import org.opengroup.osdu.storage.opa.model.ValidationInputRecord;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class OPAServiceImpl implements IOPAService {

    @Value("${OPA_API}")
    private String opaEndpoint;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;

    private HttpClient httpClient = new HttpClient();
    private Gson gson = new Gson();

    @Override
    public List<ValidationOutputRecord> validateRecordsCreationOrUpdate(List<Record> inputRecords, Map<String, RecordMetadata> existingRecords) {
        List<ValidationInputRecord> createRecords = new ArrayList<>();
        List<ValidationInputRecord> updateRecords = new ArrayList<>();
        for (Record record : inputRecords) {
            ValidationInputRecord validationInputRecord = ValidationInputRecord.builder()
                    .id(record.getId())
                    .kind(record.getKind())
                    .legal(record.getLegal())
                    .acls(record.getAcl()).build();

            if (!existingRecords.containsKey(record.getId())) {
                createRecords.add(validationInputRecord);
            } else {
                updateRecords.add(validationInputRecord);
            }
        }

        String token = headers.getAuthorization().replace("Bearer ", "");
        CreateOrUpdateValidationInput createInput = CreateOrUpdateValidationInput.builder()
                .datapartitionid(headers.getPartitionId())
                .token(token)
                .xuserid(headers.getUserId())
                .operation("create")
                .records(createRecords).build();
        CreateOrUpdateValidationInput updateInput = CreateOrUpdateValidationInput.builder()
                .datapartitionid(headers.getPartitionId())
                .token(token)
                .xuserid(headers.getUserId())
                .operation("update")
                .records(updateRecords).build();

        CreateOrUpdateValidationRequest createValidationRequest = CreateOrUpdateValidationRequest.builder().input(createInput).build();
        CreateOrUpdateValidationRequest updateValidationRequest = CreateOrUpdateValidationRequest.builder().input(updateInput).build();


        CreateOrUpdateValidationResponse createResponse = evaluateDataAuthorizationPolicy(createValidationRequest);
        CreateOrUpdateValidationResponse updateResponse = evaluateDataAuthorizationPolicy(updateValidationRequest);

        List<ValidationOutputRecord> result = new ArrayList<>();
        result.addAll(createResponse.getResult());
        result.addAll(updateResponse.getResult());
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
