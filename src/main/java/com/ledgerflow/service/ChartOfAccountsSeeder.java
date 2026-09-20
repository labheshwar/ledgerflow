package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the default chart into a newly created organization.
 *
 * REQUIRES_NEW is load bearing, and the reason is the tenant context. The
 * transaction that creates an organization runs before that organization
 * exists to be acted for, so its connection carries no tenant -- and every
 * INSERT into accounts is checked against the tenant policy. Joining that
 * transaction would mean writing accounts with no tenant set, which
 * row-level security rejects outright.
 *
 * A new transaction, begun inside TenantContext.runAs, gets the tenant
 * published to its own connection as it opens (see OrgAwareJpaTransactionManager)
 * and the inserts are checked against the right organization.
 *
 * The cost is that creating the organization and seeding its accounts are two
 * commits rather than one. That is the right trade here: an organization with
 * no accounts is a recoverable annoyance, and the alternative -- disabling the
 * isolation policy during signup -- trades a real safety property for a
 * cosmetic one.
 */
@Service
public class ChartOfAccountsSeeder {

    private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsSeeder.class);

    private final AccountRepository accountRepository;
    private final OrganizationService organizationService;

    public ChartOfAccountsSeeder(AccountRepository accountRepository, OrganizationService organizationService) {
        this.accountRepository = accountRepository;
        this.organizationService = organizationService;
    }

    /** Seeds the organization currently in context. Idempotent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int seedDefaultChart() {
        Long orgId = TenantContext.require();
        String currency = organizationService.baseCurrency();

        if (!accountRepository.findAll().isEmpty()) {
            // Re-running must not duplicate a chart someone has since edited.
            log.info("Organization {} already has accounts; leaving the chart alone", orgId);
            return 0;
        }

        Map<String, Long> idsByCode = new HashMap<>();
        for (DefaultChartOfAccounts.Seed seed : DefaultChartOfAccounts.SEEDS) {
            Account account = new Account();
            account.setOrgId(orgId);
            account.setCode(seed.code());
            account.setName(seed.name());
            account.setDescription(seed.description());
            account.setType(seed.type());
            account.setCurrency(currency);
            account.setSystemRole(seed.role());
            account.setPostable(seed.postable());
            // Seeds are ordered parents-first, so this is always resolvable.
            account.setParentId(seed.parentCode() == null ? null : idsByCode.get(seed.parentCode()));

            idsByCode.put(seed.code(), accountRepository.save(account).getId());
        }

        log.info("Seeded {} accounts for organization {}", idsByCode.size(), orgId);
        return idsByCode.size();
    }
}
