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

package org.opengroup.osdu.storage.provider.gcp.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.stereotype.Component;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.IamScopes;
import com.google.api.services.iam.v1.model.SignJwtRequest;
import com.google.api.services.iam.v1.model.SignJwtResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ServiceAccountJwtClientImpl implements IServiceAccountJwtClient {

	private static final String JWT_AUDIENCE = "https://www.googleapis.com/oauth2/v4/token";
	private static final String SERVICE_ACCOUNT_NAME_FORMAT = "projects/%s/serviceAccounts/%s";

	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	private Iam iam;

	@Autowired
	private ITenantFactory tenantStorageFactory;

	@Autowired
	private JaxRsDpsLog logger;

	@Value("${STORAGE_HOSTNAME}")
	public String STORAGE_HOSTNAME;

	@Value("${GOOGLE_AUDIENCES}")
	public String GOOGLE_AUDIENCES;

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

			SignJwtRequest signJwtRequest = new SignJwtRequest();
			signJwtRequest.setPayload(JSON_FACTORY.toString(signJwtPayload));

			String serviceAccountName = String.format(SERVICE_ACCOUNT_NAME_FORMAT, tenantInfo.getProjectId(),
					tenantInfo.getServiceAccount());

			Iam.Projects.ServiceAccounts.SignJwt signJwt = getIam().projects().serviceAccounts()
					.signJwt(serviceAccountName, signJwtRequest);
			SignJwtResponse signJwtResponse = signJwt.execute();
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

	Iam getIam() throws GeneralSecurityException, IOException {
		if (this.iam == null) {
			HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
			if (credential.createScopedRequired()) {
				List<String> scopes = new ArrayList<>();
				scopes.add(IamScopes.CLOUD_PLATFORM);
				credential = credential.createScoped(scopes);
			}

			this.iam = new Iam.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credential))
					.setApplicationName(STORAGE_HOSTNAME).build();
		}

		return this.iam;
	}

	private Map<String, Object> getJwtCreationPayload(TenantInfo tenantInfo) {
		String googleAudience = GOOGLE_AUDIENCES;
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