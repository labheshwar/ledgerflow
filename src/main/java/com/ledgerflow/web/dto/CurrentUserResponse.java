package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Role;
import java.util.List;

/** Who the caller is, which organization this session acts for, and where else they can go. */
public record CurrentUserResponse(
        String username,
        Long currentOrgId,
        String currentOrgName,
        Role role,
        List<MembershipResponse> memberships) {}
