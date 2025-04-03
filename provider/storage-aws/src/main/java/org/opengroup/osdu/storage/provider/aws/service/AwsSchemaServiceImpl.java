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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AwsSchemaServiceImpl implements SchemaService {

    @Value("${SCHEMA_API:}")
    private String schemaApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;
    
    private static final int PAGE_SIZE = 1000; // Request larger page size to minimize API calls

    @Override
    public SchemaInfoResponse getAllSchemas() {
        try {
            // Set up headers for the request
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("Authorization", this.headers.getAuthorization());
            httpHeaders.set("data-partition-id", this.headers.getPartitionId());
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            
            // Create the HTTP entity
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            
            // Initialize a combined response to hold all schemas
            SchemaInfoResponse combinedResponse = new SchemaInfoResponse();
            List<SchemaInfo> allSchemas = new ArrayList<>();
            
            // Start with offset 0
            int offset = 0;
            boolean hasMoreData = true;
            
            // Loop until we've fetched all schemas
            while (hasMoreData) {
                // Make the request to the schema service with pagination parameters
                ResponseEntity<SchemaInfoResponse> response = restTemplate.exchange(
                    schemaApiUrl + "/schema?offset=" + offset + "&limit=" + PAGE_SIZE, 
                    HttpMethod.GET, 
                    entity, 
                    SchemaInfoResponse.class);
                
                SchemaInfoResponse pageResponse = response.getBody();
                
                if (pageResponse == null || pageResponse.getSchemaInfos() == null || pageResponse.getSchemaInfos().isEmpty()) {
                    // No more data to fetch
                    hasMoreData = false;
                } else {
                    // Add schemas from this page to our combined list
                    allSchemas.addAll(pageResponse.getSchemaInfos());
                    
                    // Update offset for next page
                    offset += pageResponse.getSchemaInfos().size();
                    
                    // Check if we've reached the total count
                    if (pageResponse.getTotalCount() > 0 && offset >= pageResponse.getTotalCount()) {
                        hasMoreData = false;
                    }
                    
                    logger.info(String.format("Retrieved %d schemas, total so far: %d, total available: %d", 
                        pageResponse.getSchemaInfos().size(), allSchemas.size(), pageResponse.getTotalCount()));
                }
            }
            
            // Set the combined results
            combinedResponse.setSchemaInfos(allSchemas);
            combinedResponse.setCount(allSchemas.size());
            combinedResponse.setTotalCount(allSchemas.size());
            combinedResponse.setOffset(0);
            
            logger.info("Successfully retrieved all " + allSchemas.size() + " schemas from Schema Service");
            return combinedResponse;
            
        } catch (RestClientException e) {
            logger.error("Error retrieving schemas from Schema Service: " + e.getMessage(), e);
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                    "Error retrieving schemas", 
                    "Failed to retrieve schemas from Schema Service: " + e.getMessage());
        }
    }

    @Override
    public List<String> getAllKinds() {
        SchemaInfoResponse response = getAllSchemas();
        
        if (response == null || response.getSchemaInfos() == null) {
            logger.error("No schemas returned from Schema Service");
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                    "No schemas available", 
                    "Schema Service returned no schemas");
        }
        
        // Extract unique entity types (kinds) from schema infos
        List<String> kinds = response.getSchemaInfos().stream()
                .map(schemaInfo -> schemaInfo.getSchemaIdentity().getEntityType())
                .distinct()
                .collect(Collectors.toList());
        
        logger.info("Retrieved " + kinds.size() + " unique kinds from Schema Service");
        return kinds;
    }
}
