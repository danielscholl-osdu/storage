package org.opengroup.osdu.storage.misc;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public abstract class SwaggerIntegrationTests extends TestBase {

    public static final String SWAGGER_API_PATH = "swagger-ui/index.html";
    public static final String SWAGGER_API_DOCS_PATH = "api-docs";

    @Test
    public void shouldReturn200_whenSwaggerApiIsCalled() throws Exception {
        CloseableHttpResponse response = TestUtils
                .send(SWAGGER_API_PATH, "GET", new HashMap<>(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    public void shouldReturn200_whenSwaggerApiDocsIsCalled() throws Exception {
        CloseableHttpResponse response = TestUtils
                .send(SWAGGER_API_DOCS_PATH, "GET", new HashMap<>(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getCode());
    }

}
