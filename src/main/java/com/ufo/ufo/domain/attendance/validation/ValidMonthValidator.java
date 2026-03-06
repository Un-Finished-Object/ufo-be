package com.ufo.ufo.domain.attendance.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidMonthValidator implements ConstraintValidator<ValidMonth, Integer> {

    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value != null && value >= MIN_MONTH && value <= MAX_MONTH;
    }
}
