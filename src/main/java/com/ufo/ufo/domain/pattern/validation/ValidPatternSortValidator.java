package com.ufo.ufo.domain.pattern.validation;

import com.ufo.ufo.domain.pattern.domain.PatternSort;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPatternSortValidator implements ConstraintValidator<ValidPatternSort, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PatternSort.isValid(value);
    }
}
