/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.util;

import com.google.auth.oauth2.AccessToken;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.ServiceAccountName;
import com.google.cloud.iam.credentials.v1.SignJwtRequest;
import com.google.cloud.iam.credentials.v1.SignJwtResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.web.context.annotation.RequestScope;

@Primary
@Component
@RequestScope
public class ServiceAccountJwtClientImpl implements IServiceAccountJwtClient {

	private static final String JWT_AUDIENCE = "https://www.googleapis.com/oauth2/v4/token";
	private static final String SERVICE_ACCOUNT_NAME_FORMAT ="projects/-/serviceAccounts/%s";

	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private IamCredentialsClient iamCredentialsClient;

	@Autowired
	private ITenantFactory tenantStorageFactory;

	@Autowired
	private JaxRsDpsLog logger;

	@Value("${STORAGE_HOSTNAME}")
	public String storageHostname;

	@Value("${GOOGLE_AUDIENCES}")
	public String googleAudiences;

	@Override
	public String getIdToken(String tenantName) {
		this.logger.info("Tenant name received for auth token is: " + tenantName);
		TenantInfo tenantInfo = this.tenantStorageFactory.getTenantInfo(tenantName);
		if (tenantInfo == null) {
			this.logger.error("Invalid tenant name receiving from pubsub");
			throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid tenant Name", "Invalid tenant Name from pubsub");
		}
		try {
			// 1. get signed JWT
			Map<String, Object> signJwtPayload = getJwtCreationPayload(tenantInfo);

			ServiceAccountName name = ServiceAccountName.parse(String.format(SERVICE_ACCOUNT_NAME_FORMAT,
					tenantInfo.getServiceAccount()));
			List<String> delegates = new ArrayList<>();
			delegates.add(tenantInfo.getServiceAccount());

			SignJwtRequest request = SignJwtRequest.newBuilder()
					.setName(name.toString())
					.addAllDelegates(delegates)
					.setPayload(JSON_FACTORY.toString(signJwtPayload))
					.build();
			SignJwtResponse signJwtResponse = this.getIamCredentialsClient().signJwt(request);
			String signedJwt = signJwtResponse.getSignedJwt();

			// 2. get id token
			List<NameValuePair> postParameters = new ArrayList<>();
			postParameters.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"));
			postParameters.add(new BasicNameValuePair("assertion", signedJwt));

			HttpPost post = new HttpPost(JWT_AUDIENCE);
			post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
			post.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));

			try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
				CloseableHttpResponse httpResponse = httpClient.execute(post);

				JsonObject jsonContent = new JsonParser().parse(EntityUtils.toString(httpResponse.getEntity()))
						.getAsJsonObject();

				if (!jsonContent.has("id_token")) {
					this.logger.error(String.format("Google IAM response: %s", jsonContent.toString()));
					throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
							"User is not authorized to perform this operation.");
				}

				String token = jsonContent.get("id_token").getAsString();

				return "Bearer " + token;
			}
		} catch (AppException e) {
			throw e;
		} catch (Exception e) {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Persistence error", "Error generating token",
					e);
		}

	}

	IamCredentialsClient getIamCredentialsClient() throws IOException {
		if (this.iamCredentialsClient == null) {
			this.iamCredentialsClient = IamCredentialsClient.create();
		}
		return this.iamCredentialsClient;
	}

	private Map<String, Object> getJwtCreationPayload(TenantInfo tenantInfo) {
		String googleAudience = googleAudiences;
		if (googleAudience.contains(",")) {
			googleAudience = googleAudience.split(",")[0];
		}
		Map<String, Object> payload = new HashMap<>();
		payload.put("target_audience", googleAudience);
		payload.put("aud", JWT_AUDIENCE);
		payload.put("exp", System.currentTimeMillis() / 1000 + 3600);
		payload.put("iat", System.currentTimeMillis() / 1000);
		payload.put("iss", tenantInfo.getServiceAccount());
		return payload;
	}
}