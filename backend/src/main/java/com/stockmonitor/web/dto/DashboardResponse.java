package com.stockmonitor.web.dto;

import com.stockmonitor.external.toss.AccountSummary;
import java.util.List;

public record DashboardResponse(AccountSummary accountSummary, List<HoldingResponse> holdings) {
}
