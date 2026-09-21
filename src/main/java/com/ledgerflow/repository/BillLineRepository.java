package com.ledgerflow.repository;

import com.ledgerflow.domain.BillLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface BillLineRepository extends JpaRepository<BillLine, Long> {

    List<BillLine> findByBillIdOrderByLineOrderAsc(Long billId);

    @Modifying
    @Transactional
    void deleteByBillId(Long billId);
}
