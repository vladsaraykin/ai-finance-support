package com.finance.support.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** A category code or a user's category name; predefined codes remain API compatible. */
public record Category(String name) {
    public static final Category GROCERIES = new Category("GROCERIES");
    public static final Category RESTAURANTS = new Category("RESTAURANTS");
    public static final Category TRANSPORT = new Category("TRANSPORT");
    public static final Category HOUSING = new Category("HOUSING");
    public static final Category HEALTH = new Category("HEALTH");
    public static final Category ENTERTAINMENT = new Category("ENTERTAINMENT");
    public static final Category UTILITIES = new Category("UTILITIES");
    public static final Category SALARY = new Category("SALARY");
    public static final Category OTHER = new Category("OTHER");

    public Category {
        if (name == null || name.isBlank() || name.length() > 160 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Категория должна содержать от 1 до 160 символов без управляющих символов.");
        }
        name = name.strip();
    }

    @JsonCreator
    public static Category valueOf(String value) {
        return new Category(value);
    }

    @Override
    @JsonValue
    public String name() {
        return name;
    }

    public static Category[] values() {
        return new Category[]{GROCERIES, RESTAURANTS, TRANSPORT, HOUSING, HEALTH, ENTERTAINMENT, UTILITIES, SALARY, OTHER};
    }
}
