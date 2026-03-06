package com.ufo.ufo.domain.attendance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidYearValidator implements ConstraintValidator<ValidYear, Integer> {

    private static final int MIN_YEAR = 2026;
    private static final int MAX_YEAR = 9999;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value != null && value >= MIN_YEAR && value <= MAX_YEAR;
    }
}
