package org.opengroup.osdu.storage.swagger;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import springfox.documentation.oas.web.OpenApiTransformationContext;
import springfox.documentation.oas.web.WebMvcOpenApiTransformationFilter;
import springfox.documentation.spi.DocumentationType;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpringfoxSwaggerHostResolver implements WebMvcOpenApiTransformationFilter {

    @Override
    public boolean supports(DocumentationType delimiter) {
        return delimiter == DocumentationType.OAS_30;
    }

    @Override
    public OpenAPI transform(OpenApiTransformationContext<HttpServletRequest> context) {
        OpenAPI swagger = context.getSpecification();

        Server server = swagger.getServers().get(0);
        if (server.getUrl().contains(":443")) {
            // via the gateway
            server.setUrl(server.getUrl().replace(":443",""));
        }

        return swagger;
    }
}
