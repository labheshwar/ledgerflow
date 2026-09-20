package com.ledgerflow.service;

import com.ledgerflow.domain.OrgMember;
import com.ledgerflow.domain.Organization;
import com.ledgerflow.domain.Role;
import com.ledgerflow.domain.User;
import com.ledgerflow.repository.OrgMemberRepository;
import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an organization together with its first administrator.
 *
 * A separate bean from AuthService rather than a method on it, because
 * Spring's @Transactional is applied by a proxy: calling it on `this` from
 * another method of the same class goes straight to the method and the
 * annotation does nothing at all. The organization, the user and the
 * membership have to commit together or not at all -- a signup that created
 * an organization but no user, or a user belonging to nothing, leaves someone
 * unable to log in and no obvious way to repair it.
 */
@Service
public class OrganizationProvisioner {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationProvisioner(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrgMemberRepository orgMemberRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.orgMemberRepository = orgMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return the new organization's id
     * @throws IllegalArgumentException if the username is taken
     */
    @Transactional
    public Long createOrganizationWithOwner(String username, String password, String organizationName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("That username is already taken");
        }

        Organization organization = new Organization();
        organization.setName(organizationName);
        organization = organizationRepository.save(organization);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);

        OrgMember membership = new OrgMember();
        membership.setOrgId(organization.getId());
        membership.setUserId(user.getId());
        membership.setRole(Role.ADMIN);
        orgMemberRepository.save(membership);

        return organization.getId();
    }
}
