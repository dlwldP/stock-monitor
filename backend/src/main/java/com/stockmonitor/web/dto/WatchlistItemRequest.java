package com.stockmonitor.web.dto;

import com.stockmonitor.domain.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WatchlistItemRequest(@NotBlank String symbol, @NotNull Market market, String displayName) {
}
