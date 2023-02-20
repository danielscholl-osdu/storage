package org.opengroup.osdu.storage.validation.api;

import org.opengroup.osdu.storage.validation.impl.JsonPatchValidator;

import java.lang.annotation.*;

import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {JsonPatchValidator.class})
@Documented
public @interface ValidJsonPatch {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
