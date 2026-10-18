package com.finance.support.api;

import com.finance.support.application.RagKnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final RagKnowledgeService knowledgeService;

    public KnowledgeController(RagKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RagKnowledgeService.DocumentStatus ingest(@Valid @RequestBody IngestDocumentRequest request) {
        return knowledgeService.ingest(request.title(), request.source(), request.documentType(), request.text());
    }

    @GetMapping("/documents/{fileId}")
    public RagKnowledgeService.DocumentStatus status(@PathVariable String fileId) {
        return knowledgeService.status(fileId);
    }

    public record IngestDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 300) String source,
            @NotBlank String documentType,
            @NotBlank @Size(max = 100_000) String text) {}
}
