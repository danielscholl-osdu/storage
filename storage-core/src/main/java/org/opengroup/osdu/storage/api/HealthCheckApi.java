package org.opengroup.osdu.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "health", description = "Health endpoint")
public class HealthCheckApi {

    @Operation(summary = "${healthCheckApi.healthMessage.summary}",
            description = "${healthCheckApi.healthMessage.description}", tags = { "health" })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = { @Content(schema = @Schema(implementation = String.class)) })
    })
    @GetMapping("/health")
    public String healthMessage() {
        return "Alive";
    }

}
