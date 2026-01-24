package com.legymernok.backend.web;

import com.legymernok.backend.service.admin.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final LogService logService;

    @GetMapping
    @PreAuthorize("hasAuthority('logs:read')")
    public ResponseEntity<List<String>> getLogs(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(logService.getLatestLogs(limit));
    }
}