package org.opengroup.osdu.storage.swagger;


import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@Profile("!noswagger")
public class SwaggerConfiguration {

    @Value("${api.title}")
    private String apiTitle;

    @Value("${api.description}")
    private String apiDescription;

    @Value("${api.version}")
    private String apiVersion;

    @Value("${api.contact.name}")
    private String contactName;

    @Value("${api.contact.email}")
    private String contactEmail;

    @Value("${api.license.name}")
    private String licenseName;

    @Value("${api.license.url}")
    private String licenseUrl;

    @Value("${api.server.url}")
    private String apiServerUrl;

    @Value("${api.server.fullUrl.enabled:false}")
    private boolean isServerFullUrlEnabled;

    @Bean
    public OpenAPI customOpenAPI() {

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("Authorization")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");
        final String securitySchemeName = "Authorization";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securitySchemeName);
        Components components = new Components().addSecuritySchemes(securitySchemeName, securityScheme);

        OpenAPI openAPI = new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(apiInfo())
                .tags(tags());

        if(isServerFullUrlEnabled)
            return openAPI;
        return openAPI
                .servers(Arrays.asList(new Server().url(apiServerUrl)));
    }

    private List<Tag> tags() {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("records").description("Records management operations"));
        tags.add(new Tag().name("query").description("Querying Records operations"));
        tags.add(new Tag().name("info").description("Version info endpoint"));
        return tags;
    }

    private Info apiInfo() {
        return new Info()
                .title(apiTitle)
                .description(apiDescription)
                .version(apiVersion)
                .license(new License().name(licenseName).url(licenseUrl))
                .contact(new Contact().name(contactName).email(contactEmail));
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
                Parameter dataPartitionId = new Parameter()
                        .name(DpsHeaders.DATA_PARTITION_ID)
                        .description("Tenant Id")
                        .in("header")
                        .required(true)
                        .schema(new StringSchema());
                Parameter frameOfReference = new Parameter()
                        .name(DpsHeaders.FRAME_OF_REFERENCE)
                        .description("This value indicates whether normalization applies, should be either " +
                                "`none` or `units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;`")
                        .in("header")
                        .required(true)
                        .example("units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;")
                        .schema(new StringSchema());
          
                Operation currentOperation = operation.addParametersItem(dataPartitionId);
                if(currentOperation.getOperationId().equals("fetchRecords"))
                  currentOperation = currentOperation.addParametersItem(frameOfReference);
                return currentOperation;
              };
    }
}