package com.finance.support.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseTextRequest(@NotBlank @Size(max = 2000) String text) {}
