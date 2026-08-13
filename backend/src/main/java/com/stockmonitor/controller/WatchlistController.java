package com.stockmonitor.controller;

import com.stockmonitor.service.WatchlistService;
import com.stockmonitor.web.dto.WatchlistItemRequest;
import com.stockmonitor.web.dto.WatchlistItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

	private final WatchlistService service;

	public WatchlistController(WatchlistService service) {
		this.service = service;
	}

	@GetMapping
	public List<WatchlistItemResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WatchlistItemResponse add(@Valid @RequestBody WatchlistItemRequest request) {
		return service.add(request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}
}
