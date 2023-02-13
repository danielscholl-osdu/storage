package org.opengroup.osdu.storage.validation.api;

import org.opengroup.osdu.storage.validation.impl.PatchValidator;

import java.lang.annotation.*;

import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {PatchValidator.class})
@Documented
public @interface ValidPatchOperation {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
