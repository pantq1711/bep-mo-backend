package com.bepmo.dish;

import com.bepmo.dish.dto.DishDtos.SetAvailabilityRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DishDtosValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void setAvailability_nullIsRejected() {
        var violations = validator.validate(new SetAvailabilityRequest(null));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("isAvailable");
    }

    @Test
    void setAvailability_booleanIsAccepted() {
        assertThat(validator.validate(new SetAvailabilityRequest(Boolean.TRUE))).isEmpty();
        assertThat(validator.validate(new SetAvailabilityRequest(Boolean.FALSE))).isEmpty();
    }
}
