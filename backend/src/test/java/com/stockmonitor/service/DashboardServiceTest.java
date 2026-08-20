package com.stockmonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AccountSnapshot;
import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.AccountSummary;
import com.stockmonitor.external.toss.Holding;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.AccountSnapshotRepository;
import com.stockmonitor.web.dto.AccountSnapshotResponse;
import com.stockmonitor.web.dto.DashboardResponse;
import com.stockmonitor.web.dto.HoldingResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	@Mock
	private TossApiClient tossApiClient;

	@Mock
	private AccountSnapshotRepository snapshotRepository;

	private DashboardService service;

	@BeforeEach
	void setUp() {
		service = new DashboardService(tossApiClient, snapshotRepository);
	}

	@Test
	void computesEvalAmountAndPnlFromTheLiveQuote() {
		Holding holding = new Holding("005930", Market.KR, "삼성전자", new BigDecimal("10"), new BigDecimal("65000"));
		when(tossApiClient.getHoldings()).thenReturn(List.of(holding));
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(
				new Quote("005930", Market.KR, new BigDecimal("70000"), BigDecimal.ZERO, 1000, 1000, new BigDecimal("90000"), new BigDecimal("50000"), Instant.now()));
		when(tossApiClient.getAccountSummary()).thenReturn(new AccountSummary(new BigDecimal("700000"), new BigDecimal("50000"), new BigDecimal("7.69")));

		DashboardResponse response = service.getDashboard();

		HoldingResponse h = response.holdings().get(0);
		assertThat(h.evalAmount()).isEqualByComparingTo("700000.00");
		assertThat(h.pnl()).isEqualByComparingTo("50000.00");
		assertThat(h.pnlRate()).isEqualByComparingTo("7.69"); // 50000 / 650000 * 100
	}

	@Test
	void pnlRateIsZeroWhenCostBasisIsZero() {
		Holding holding = new Holding("005930", Market.KR, "삼성전자", new BigDecimal("10"), BigDecimal.ZERO);
		when(tossApiClient.getHoldings()).thenReturn(List.of(holding));
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(
				new Quote("005930", Market.KR, new BigDecimal("70000"), BigDecimal.ZERO, 1000, 1000, new BigDecimal("90000"), new BigDecimal("50000"), Instant.now()));
		when(tossApiClient.getAccountSummary()).thenReturn(new AccountSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

		DashboardResponse response = service.getDashboard();

		assertThat(response.holdings().get(0).pnlRate()).isEqualByComparingTo("0");
	}

	@Test
	void historyIsReturnedOldestFirst() {
		AccountSnapshot older = new AccountSnapshot(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
		AccountSnapshot newer = new AccountSnapshot(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO);
		// Repository contract is "desc" (newest first) - the service must reverse it.
		when(snapshotRepository.findAllByOrderBySnapshotAtDesc(org.mockito.ArgumentMatchers.any(Pageable.class)))
				.thenReturn(List.of(newer, older));

		List<AccountSnapshotResponse> history = service.getHistory(10);

		assertThat(history).extracting(AccountSnapshotResponse::totalValue)
				.containsExactly(new BigDecimal("100"), new BigDecimal("200"));
	}
}
