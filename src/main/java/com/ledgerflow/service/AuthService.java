package com.ledgerflow.service;

import com.ledgerflow.domain.OrgMember;
import com.ledgerflow.domain.Organization;
import com.ledgerflow.domain.Role;
import com.ledgerflow.domain.User;
import com.ledgerflow.repository.OrgMemberRepository;
import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.repository.UserRepository;
import com.ledgerflow.security.JwtService;
import com.ledgerflow.tenancy.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final ChartOfAccountsSeeder chartOfAccountsSeeder;
    private final OrganizationProvisioner organizationProvisioner;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrgMemberRepository orgMemberRepository,
            ChartOfAccountsSeeder chartOfAccountsSeeder,
            OrganizationProvisioner organizationProvisioner) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.orgMemberRepository = orgMemberRepository;
        this.chartOfAccountsSeeder = chartOfAccountsSeeder;
        this.organizationProvisioner = organizationProvisioner;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException if
     *         the username/password pair is invalid.
     */
    public String login(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));

        User user = userRepository.findByUsername(username).orElseThrow();
        OrgMember membership = orgMemberRepository.findByUserIdOrderByOrgIdAsc(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "User " + username + " does not belong to any organization"));

        return jwtService.generateToken(username, membership.getRole(), membership.getOrgId());
    }

    /**
     * Issues a token for a different organization the user belongs to. The
     * membership check is the authorization: without it, anyone could mint a
     * token for any org id and the tenant context would honour it.
     */
    public String switchOrganization(String username, Long orgId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        OrgMember membership = orgMemberRepository
                .findByOrgIdAndUserId(orgId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of that organization"));

        return jwtService.generateToken(username, membership.getRole(), orgId);
    }

    /**
     * Creates a user, the organization they will administer, and the chart of
     * accounts that organization starts with.
     *
     * Deliberately not one transaction. The organization has to exist and be
     * committed before anything can act *for* it, because every write to
     * accounts is checked against the tenant in context and there is no tenant
     * while the organization is still being created. So the seeding runs in
     * its own transaction, inside runAs, once there is an organization to be.
     *
     * If seeding fails the signup still stands: the user can sign in and the
     * chart can be created by hand or by re-running the seeder. An
     * organization with no accounts is an annoyance; a signup that half
     * succeeded and left an unusable login would be worse.
     */
    public String signUp(String username, String password, String organizationName) {
        Long orgId = organizationProvisioner.createOrganizationWithOwner(username, password, organizationName);

        try {
            TenantContext.runAs(orgId, chartOfAccountsSeeder::seedDefaultChart);
        } catch (RuntimeException e) {
            log.error("Created organization {} but could not seed its chart of accounts", orgId, e);
        }

        return jwtService.generateToken(username, Role.ADMIN, orgId);
    }

    public List<OrgMember> membershipsOf(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return orgMemberRepository.findByUserIdOrderByOrgIdAsc(user.getId());
    }

    public Organization organization(Long orgId) {
        return organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new NoSuchElementException("No organization with id " + orgId));
    }
}
