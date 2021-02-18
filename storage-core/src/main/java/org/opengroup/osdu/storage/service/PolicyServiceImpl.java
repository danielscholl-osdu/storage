package org.opengroup.osdu.storage.service;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.model.policy.PolicyResponse;
import org.opengroup.osdu.storage.model.policy.StoragePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PolicyServiceImpl implements IPolicyService {

    @Autowired
    private DpsHeaders headers;

    @Value("${POLICY_API}")
    private String policyApi;

    @Autowired
    private JaxRsDpsLog logger;

    private final Gson gson = new Gson();

    @Override
    public PolicyResponse evaluatePolicy(StoragePolicy policy) {

        try {
            HttpClient httpClient = HttpClients.createDefault();
            HttpPost postRequest = new HttpPost(policyApi + "/evaluations/query");

            String jsonString = this.gson.toJson(policy);
            HttpEntity inputEntity = new StringEntity(jsonString);
            ((StringEntity) inputEntity).setContentType("application/json");
            postRequest.setEntity(inputEntity);
            postRequest.addHeader("authorization", headers.getAuthorization());
            postRequest.addHeader("data-partition-id", headers.getPartitionIdWithFallbackToAccountId());

            this.logger.info("policy service called");

            HttpResponse response = httpClient.execute(postRequest);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");
            return gson.fromJson(responseString, PolicyResponse.class);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Policy service unavailable", "Error making request to Policy service");
        }
    }
}

