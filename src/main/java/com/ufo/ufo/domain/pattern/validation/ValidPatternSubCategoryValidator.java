package com.ufo.ufo.domain.pattern.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPatternSubCategoryValidator implements ConstraintValidator<ValidPatternSubCategory, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PatternSubCategoryQuery.isValidNullable(value);
    }
}
