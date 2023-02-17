package org.opengroup.osdu.storage.misc;

import static org.junit.Assert.assertEquals;

import com.sun.jersey.api.client.ClientResponse;
import java.util.Collections;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public abstract class SwaggerIntegrationTests extends TestBase {

    public static final String SWAGGER_API_PATH = "swagger";
    public static final String SWAGGER_API_DOCS_PATH = "api-docs";

    @Test
    public void shouldReturn200_whenSwaggerApiIsCalled() throws Exception {
        ClientResponse response = TestUtils
                .send(SWAGGER_API_PATH, "GET", Collections.emptyMap(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());
    }

    @Test
    public void shouldReturn200_whenSwaggerApiDocsIsCalled() throws Exception {
        ClientResponse response = TestUtils
                .send(SWAGGER_API_DOCS_PATH, "GET", Collections.emptyMap(), "", "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());
    }

}
