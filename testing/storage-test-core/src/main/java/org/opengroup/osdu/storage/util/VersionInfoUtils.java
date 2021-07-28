package org.opengroup.osdu.storage.util;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class VersionInfoUtils {

  public VersionInfo getVersionInfoFromResponse(ClientResponse response) {
    assertTrue(response.getType().toString().contains("application/json"));
    String json = response.getEntity(String.class);
    Gson gson = new Gson();
    return gson.fromJson(json, VersionInfo.class);
  }

  public class VersionInfo {
    public String groupId;
    public String artifactId;
    public String version;
    public String buildTime;
    public String branch;
    public String commitId;
    public String commitMessage;
  }
}
