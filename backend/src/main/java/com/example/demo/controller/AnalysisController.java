package com.example.demo.controller;

import com.example.demo.entity.AnalysisRequest;
import com.example.demo.event.AiAnalysisEvent;
import com.example.demo.repository.AnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
            java.security.Principal principal) {
        // 1. Save initial record
        AnalysisRequest request = new AnalysisRequest(file.getOriginalFilename());
        AnalysisRequest savedRequest = repository.save(request);

        String username = (principal != null) ? principal.getName() : "anonymous";

        // 2. Publish event (Async processing starts here)
        eventPublisher.publishEvent(new AiAnalysisEvent(savedRequest.getId(), username));

        // 3. Return ID immediately
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedRequest.getId());
        response.put("message", "File uploaded and analysis started.");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisRequest> getStatus(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
