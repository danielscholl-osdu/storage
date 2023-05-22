package org.opengroup.osdu.storage.swagger;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@OpenAPIDefinition(
        info = @Info(
                title = "${api.title}",
                description = "${api.description}",
                version = "${api.version}",
                contact = @Contact(name = "${api.contact.name}", email = "${api.contact.email}"),
                license = @License(name = "${api.license.name}", url = "${api.license.url}")),
        servers = @Server(url = "${api.server.url}"),
        security = @SecurityRequirement(name = "Authorization"),
        tags = {
                @Tag(name = "records", description = "Records management operations"),
                @Tag(name = "query", description = "Querying Records operations"),
                @Tag(name = "info", description = "Version info endpoint")
        }
)
@SecurityScheme(name = "Authorization", scheme = "bearer", bearerFormat = "Authorization", type = SecuritySchemeType.HTTP)
@Configuration
@Profile("!noswagger")
public class SwaggerConfiguration {
  @Bean
  public OperationCustomizer customize() {
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
