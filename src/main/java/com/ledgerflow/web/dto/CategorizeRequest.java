package com.ledgerflow.web.dto;

import jakarta.validation.constraints.NotNull;

public record CategorizeRequest(@NotNull Long accountId, String description) {}
