package com.stockmonitor.repository;

import com.stockmonitor.domain.AccountSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {

	List<AccountSnapshot> findAllByOrderBySnapshotAtDesc(Pageable pageable);

	/** Oldest first, so RetentionService can keep the last snapshot of each day and drop the rest. */
	List<AccountSnapshot> findBySnapshotAtBeforeOrderBySnapshotAtAsc(Instant cutoff);
}
