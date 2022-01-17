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

package org.opengroup.osdu.storage.swagger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.RequestParameterBuilder;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.ParameterType;
import springfox.documentation.service.RequestParameter;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
@EnableOpenApi
@Profile("!noswagger")
public class SwaggerDocumentationConfig {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String DEFAULT_INCLUDE_PATTERN = "/.*";

    @Bean
    public Docket api() {
    	RequestParameterBuilder builder = new RequestParameterBuilder();
    	List<RequestParameter> parameters = new ArrayList<>();
        builder.name(DpsHeaders.DATA_PARTITION_ID)
                .description("tenant")
                .in(ParameterType.HEADER)
                .required(true)
                .build();
        parameters.add(builder.build());
        builder.name("frame-of-reference")
                .description("reference")
                .in(ParameterType.HEADER)
                .required(true)
                .build();
        parameters.add(builder.build());
        return new Docket(DocumentationType.OAS_30)
            	.globalRequestParameters(parameters)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.opengroup.osdu.storage.api"))
                .build()
                .securityContexts(Collections.singletonList(securityContext()))
                .securitySchemes(Collections.singletonList(apiKey()));
    }

    private ApiKey apiKey() {
    	return new ApiKey(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER, "header");
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .operationSelector(o -> PathSelectors.regex(DEFAULT_INCLUDE_PATTERN).test(o.requestMappingPattern()))
                .build();
    }

    List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope
                = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes =
                new AuthorizationScope[]{authorizationScope};
        return Collections.singletonList(
        		new SecurityReference(AUTHORIZATION_HEADER, authorizationScopes));
    }
}
