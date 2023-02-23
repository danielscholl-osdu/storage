package org.opengroup.osdu.storage.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RequestValidationException extends RuntimeException {
    @Builder.Default
    private HttpStatus status = HttpStatus.BAD_REQUEST;
    @Builder.Default
    private String reason = "Validation failed";
    private String message;
}
