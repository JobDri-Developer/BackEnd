package com.jobdri.jobdri_api.domain.mockapply.repository;

import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplySequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MockApplySequenceRepository extends JpaRepository<MockApplySequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mas
            from MockApplySequence mas
            where mas.userId = :userId
              and mas.companyId = :companyId
              and mas.detailClassificationId = :detailClassificationId
            """)
    Optional<MockApplySequence> findByKeyForUpdate(
            @Param("userId") Long userId,
            @Param("companyId") Long companyId,
            @Param("detailClassificationId") Long detailClassificationId
    );
}
