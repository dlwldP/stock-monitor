package com.stockmonitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A stock the user is watching (not necessarily held). Corresponds to
 * {@code watchlist_items} in docs/PLANNING.md section 7.
 *
 * <p>No {@code user_id} yet — this MVP is single-user (see README), so watchlist
 * items are global to the deployment. Add ownership once multi-user login exists.
 */
@Entity
@Table(name = "watchlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "market"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchlistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Market market;

	/** Optional friendly label (e.g. "삼성전자") since the mock/real quote source may not resolve names yet. */
	@Column(length = 100)
	private String displayName;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public WatchlistItem(String symbol, Market market, String displayName) {
		this.symbol = symbol;
		this.market = market;
		this.displayName = displayName;
	}
}
