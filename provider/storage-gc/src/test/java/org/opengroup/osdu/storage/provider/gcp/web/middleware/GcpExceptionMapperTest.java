package org.opengroup.osdu.storage.provider.gcp.web.middleware;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.google.cloud.storage.StorageException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.storage.util.GlobalExceptionMapper;
import org.springframework.http.ResponseEntity;

@RunWith(MockitoJUnitRunner.class)
public class GcpExceptionMapperTest {

    @InjectMocks
    private GcpExceptionMapper sut;

    @Mock
    private GlobalExceptionMapper mapper;

    @Test
    public void handleStorageExceptionShouldReturnForbiddenResponseWhenCatchesStorageException() {
        StorageException exception = new StorageException(403, "Access Denied");
        AppError expectedBody = new AppError(FORBIDDEN.value(),
            "Access Denied", "The user is not authorized to perform this action");
        when(mapper.getErrorResponse(ArgumentMatchers .argThat(
            e -> StorageException.class == e.getOriginalException().getClass())))
            .thenReturn(new ResponseEntity<>(expectedBody, FORBIDDEN));

        ResponseEntity<Object> response = sut.handleStorageException(exception);

        assertThat(response.getStatusCodeValue(), is(FORBIDDEN.value()));
        assertThat(response.getBody(), is(expectedBody));
    }
}
