package com.ledgerflow.web;

import com.ledgerflow.domain.OrgMember;
import com.ledgerflow.service.AuthService;
import com.ledgerflow.tenancy.TenantContext;
import com.ledgerflow.web.dto.CurrentUserResponse;
import com.ledgerflow.web.dto.LoginRequest;
import com.ledgerflow.web.dto.LoginResponse;
import com.ledgerflow.web.dto.MembershipResponse;
import com.ledgerflow.web.dto.SignUpRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.username(), request.password()));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return new LoginResponse(
                authService.signUp(request.username(), request.password(), request.organizationName()));
    }

    /**
     * Re-issues the token against another organization. The org is a signed
     * claim, so switching means a new token rather than a header the client
     * could set to anything.
     */
    @PostMapping("/switch-org/{orgId}")
    public LoginResponse switchOrganization(@PathVariable Long orgId, Principal principal) {
        return new LoginResponse(authService.switchOrganization(principal.getName(), orgId));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Principal principal) {
        List<OrgMember> memberships = authService.membershipsOf(principal.getName());
        Long currentOrgId = TenantContext.require();

        List<MembershipResponse> membershipResponses = memberships.stream()
                .map(m -> new MembershipResponse(
                        m.getOrgId(), authService.organization(m.getOrgId()).getName(), m.getRole()))
                .toList();

        MembershipResponse current = membershipResponses.stream()
                .filter(m -> m.orgId().equals(currentOrgId))
                .findFirst()
                .orElseThrow();

        return new CurrentUserResponse(
                principal.getName(),
                current.orgId(),
                current.organizationName(),
                current.role(),
                membershipResponses);
    }
}
