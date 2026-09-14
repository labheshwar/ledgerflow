package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Role;

public record MembershipResponse(Long orgId, String organizationName, Role role) {}
