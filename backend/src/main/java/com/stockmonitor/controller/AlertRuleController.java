package com.stockmonitor.controller;

import com.stockmonitor.service.AlertRuleService;
import com.stockmonitor.web.dto.AlertRuleRequest;
import com.stockmonitor.web.dto.AlertRuleResponse;
import com.stockmonitor.web.dto.AlertRuleUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

	private final AlertRuleService service;

	public AlertRuleController(AlertRuleService service) {
		this.service = service;
	}

	@GetMapping
	public List<AlertRuleResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AlertRuleResponse create(@Valid @RequestBody AlertRuleRequest request) {
		return service.create(request);
	}

	@PatchMapping("/{id}")
	public AlertRuleResponse update(@PathVariable Long id, @Valid @RequestBody AlertRuleUpdateRequest request) {
		return service.setActive(id, request.active());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}
}
