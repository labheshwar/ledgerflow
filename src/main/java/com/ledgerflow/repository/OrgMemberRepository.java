package com.ledgerflow.repository;

import com.ledgerflow.domain.OrgMember;
import com.ledgerflow.domain.OrgMemberId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgMemberRepository extends JpaRepository<OrgMember, OrgMemberId> {

    List<OrgMember> findByUserIdOrderByOrgIdAsc(Long userId);

    Optional<OrgMember> findByOrgIdAndUserId(Long orgId, Long userId);

    long countByOrgId(Long orgId);
}
