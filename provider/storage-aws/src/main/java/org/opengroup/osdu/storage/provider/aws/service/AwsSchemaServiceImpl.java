/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaInfo;
import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AWS implementation of the Schema Service.
 */
@Service
public class AwsSchemaServiceImpl implements SchemaService {

    private static final int PAGE_SIZE = 1000; // Request larger page size to minimize API calls
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String DATA_PARTITION_ID_HEADER = "data-partition-id";
    private static final String SCHEMA_ENDPOINT = "/schema";
    private static final String PAGINATION_PARAMS = "?offset=%d&limit=%d";

    @Value("${SCHEMA_API:}")
    private String schemaApiUrl;

    private final RestTemplate restTemplate;
    private final DpsHeaders headers;
    private final JaxRsDpsLog logger;

    /**
     * Constructor for AwsSchemaServiceImpl.
     *
     * @param restTemplate the REST template for making HTTP requests
     * @param headers the DPS headers for authentication and context
     * @param logger the logger for logging messages
     */
    public AwsSchemaServiceImpl(RestTemplate restTemplate, DpsHeaders headers, JaxRsDpsLog logger) {
        this.restTemplate = restTemplate;
        this.headers = headers;
        this.logger = logger;
    }

    @Override
    public SchemaInfoResponse getAllSchemas() {
        try {
            HttpHeaders httpHeaders = createHttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            
            SchemaInfoResponse combinedResponse = new SchemaInfoResponse();
            List<SchemaInfo> allSchemas = fetchAllSchemaPages(entity);
            
            populateResponseWithSchemas(combinedResponse, allSchemas);
            
            logger.info("Successfully retrieved all {} schemas from Schema Service", String.valueOf(allSchemas.size()));
            return combinedResponse;
            
        } catch (RestClientException e) {
            logAndThrowSchemaError(e);
            return null; // This line will never be reached due to the exception being thrown
        } catch (URISyntaxException e) {
            logger.error("Invalid URI syntax when accessing Schema Service: {}", e.getMessage());
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                    "Error retrieving schemas", 
                    "Invalid URI when accessing Schema Service: " + e.getMessage());
        }
    }

    @Override
    public List<String> getAllKinds() {
        SchemaInfoResponse response = getAllSchemas();
        
        if (response == null || response.getSchemaInfos() == null || response.getSchemaInfos().isEmpty()) {
            logger.error("No schemas returned from Schema Service");
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                    "No schemas available", 
                    "Schema Service returned no schemas");
        }
        
        // Extract unique entity types (kinds) from schema infos
        List<String> kinds = response.getSchemaInfos().stream()
                .map(schemaInfo -> schemaInfo.getSchemaIdentity().getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        
        logger.info("Retrieved {} unique kinds from Schema Service", String.valueOf(kinds.size()));
        return kinds;
    }
    
    /**
     * Creates HTTP headers for Schema Service requests.
     *
     * @return HttpHeaders with authorization and partition ID
     */
    private HttpHeaders createHttpHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(AUTHORIZATION_HEADER, this.headers.getAuthorization());
        httpHeaders.set(DATA_PARTITION_ID_HEADER, this.headers.getPartitionId());
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return httpHeaders;
    }
    
    /**
     * Fetches all schema pages from the Schema Service.
     *
     * @param entity HTTP entity with headers
     * @return List of all schema infos
     * @throws URISyntaxException if the URI is invalid
     */
    private List<SchemaInfo> fetchAllSchemaPages(HttpEntity<String> entity) throws URISyntaxException {
        List<SchemaInfo> allSchemas = new ArrayList<>();
        int offset = 0;
        boolean hasMoreData = true;
        
        while (hasMoreData) {
            String url = schemaApiUrl + SCHEMA_ENDPOINT + String.format(PAGINATION_PARAMS, offset, PAGE_SIZE);
            URI uri = new URI(url);
            
            ResponseEntity<SchemaInfoResponse> response = restTemplate.exchange(
                uri, 
                HttpMethod.GET, 
                entity, 
                SchemaInfoResponse.class);
            
            SchemaInfoResponse pageResponse = response.getBody();
            
            if (pageResponse == null || pageResponse.getSchemaInfos() == null || pageResponse.getSchemaInfos().isEmpty()) {
                hasMoreData = false;
            } else {
                List<SchemaInfo> pageSchemas = pageResponse.getSchemaInfos();
                allSchemas.addAll(pageSchemas);
                
                offset += pageSchemas.size();
                
                if (pageResponse.getTotalCount() > 0 && offset >= pageResponse.getTotalCount()) {
                    hasMoreData = false;
                }

                logger.info("Retrieved " + pageSchemas.size() + " schemas, total so far: " + allSchemas.size() + ", total available: " + pageResponse.getTotalCount());
            }
        }
        
        return allSchemas;
    }
    
    /**
     * Populates the response object with schema information.
     *
     * @param response the response object to populate
     * @param schemas the list of schemas to include in the response
     */
    private void populateResponseWithSchemas(SchemaInfoResponse response, List<SchemaInfo> schemas) {
        response.setSchemaInfos(schemas);
        response.setCount(schemas.size());
        response.setTotalCount(schemas.size());
        response.setOffset(0);
    }
    
    /**
     * Logs an error and throws an AppException for schema retrieval failures.
     *
     * @param e the exception that occurred
     * @throws AppException always thrown with error details
     */
    private void logAndThrowSchemaError(Exception e) {
        logger.error("Error retrieving schemas from Schema Service: {}", e.getMessage());
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                "Error retrieving schemas", 
                "Failed to retrieve schemas from Schema Service: " + e.getMessage());
    }
}
