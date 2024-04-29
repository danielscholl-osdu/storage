package org.opengroup.osdu.storage.validation.api;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.opengroup.osdu.core.common.model.storage.validation.KindValidator;
import org.opengroup.osdu.storage.validation.impl.VersionIdsValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(
        validatedBy = {VersionIdsValidator.class}
)
@Documented
public @interface ValidVersionIds {
    String message() default "Not a valid record version Ids. Found: ${validatedValue}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
