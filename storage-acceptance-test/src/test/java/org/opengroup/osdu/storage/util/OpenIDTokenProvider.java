/*
 * Copyright 2020-2023 Google LLC
 * Copyright 2020-2023 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import net.minidev.json.JSONObject;
import org.opengroup.osdu.storage.util.conf.OpenIDProviderConfig;

public class OpenIDTokenProvider {

  private static final OpenIDProviderConfig openIDProviderConfig = OpenIDProviderConfig.Instance();
  private static final String ID_TOKEN = "id_token";
  private final AuthorizationGrant clientGrant = new ClientCredentialsGrant();
  private final URI tokenEndpointURI;
  private final Scope scope;
  private final ClientAuthentication clientAuthentication;
  private final ClientAuthentication noAccessClientAuthentication;
  private final ClientAuthentication dataRootAuthentication;

  public OpenIDTokenProvider() {
    this.tokenEndpointURI = openIDProviderConfig.getProviderMetadata().getTokenEndpointURI();
    this.scope = new Scope(openIDProviderConfig.getScopes());
    this.clientAuthentication =
        new ClientSecretBasic(
            new ClientID(openIDProviderConfig.getClientId()),
            new Secret(openIDProviderConfig.getClientSecret()));
    this.noAccessClientAuthentication =
        new ClientSecretBasic(
            new ClientID(openIDProviderConfig.getNoAccessClientId()),
            new Secret(openIDProviderConfig.getNoAccessClientSecret()));
    this.dataRootAuthentication =
        new ClientSecretBasic(
            new ClientID(openIDProviderConfig.getDataRootClientId()),
            new Secret(openIDProviderConfig.getDataRootClientSecret()));
  }

  public String getToken() {
    try {
      TokenRequest request =
          new TokenRequest(
              this.tokenEndpointURI, this.clientAuthentication, this.clientGrant, this.scope);
      return requestToken(request);
    } catch (ParseException | IOException e) {
      throw new RuntimeException("Unable get credentials from INTEGRATION_TESTER variables", e);
    }
  }

  public String getNoAccessToken() {
    try {
      TokenRequest request =
          new TokenRequest(
              this.tokenEndpointURI,
              this.noAccessClientAuthentication,
              this.clientGrant,
              this.scope);
      return requestToken(request);
    } catch (ParseException | IOException e) {
      throw new RuntimeException("Unable get credentials from INTEGRATION_TESTER variables", e);
    }
  }

  public String getDataRootToken() {
    try {
      TokenRequest request =
          new TokenRequest(
              this.tokenEndpointURI, this.dataRootAuthentication, this.clientGrant, this.scope);
      return requestToken(request);
    } catch (ParseException | IOException e) {
      throw new RuntimeException("Unable get credentials from INTEGRATION_TESTER variables", e);
    }
  }

  private String requestToken(TokenRequest tokenRequest) throws ParseException, IOException {

    TokenResponse parse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());

    if (!parse.indicatesSuccess()) {
      throw new RuntimeException("Unable get credentials from INTEGRATION_TESTER variables");
    }

    JSONObject jsonObject = parse.toSuccessResponse().toJSONObject();
    // OAuth2 client_credentials returns access_token; some OIDC providers return id_token
    String tokenValue = jsonObject.getAsString("access_token");
    if (Objects.isNull(tokenValue) || tokenValue.isEmpty()) {
      tokenValue = jsonObject.getAsString(ID_TOKEN);
    }
    if (Objects.isNull(tokenValue) || tokenValue.isEmpty()) {
      throw new RuntimeException("Unable get credentials from INTEGRATION_TESTER variables");
    }
    return tokenValue;
  }
}
