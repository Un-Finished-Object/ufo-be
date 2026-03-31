package com.ufo.ufo.domain.pattern.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPatternCategoryValidator implements ConstraintValidator<ValidPatternCategory, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PatternCategoryQuery.isValid(value);
    }
}
