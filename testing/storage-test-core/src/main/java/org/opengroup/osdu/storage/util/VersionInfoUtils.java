package org.opengroup.osdu.storage.util;

import com.google.gson.Gson;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opengroup.osdu.core.common.model.info.VersionInfo;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class VersionInfoUtils {

  public VersionInfo getVersionInfoFromResponse(CloseableHttpResponse response) {
    assertTrue(response.getEntity().getContentType().contains("application/json"));
    String json = null;
    try {
      json = EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
    Gson gson = new Gson();
    return gson.fromJson(json, VersionInfo.class);
  }
}
