package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.NoteGenerateRequest;
import com.omnistudy.model.dto.NoteResponse;
import com.omnistudy.model.dto.NoteUpsertRequest;
import com.omnistudy.model.dto.NoteWorkspaceResponse;
import com.omnistudy.model.dto.StudyMaterialRequest;
import com.omnistudy.model.dto.SubtitleCaptureRequest;
import com.omnistudy.service.NoteService;
import com.omnistudy.service.NoteGenerationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/note")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final NoteGenerationRequestService generationRequestService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Void>> generate(
            @Valid @RequestBody NoteGenerateRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        generationRequestService.request(request, userId, false);
        return ResponseEntity.accepted().body(ApiResponse.ok(null));
    }

    @PostMapping("/session/{sessionId}/subtitles")
    public ResponseEntity<ApiResponse<Void>> captureSubtitles(
            @PathVariable UUID sessionId, @RequestBody SubtitleCaptureRequest request,
            @AuthenticationPrincipal UUID userId) {
        noteService.captureSubtitles(sessionId, request, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/session/{sessionId}/auto-sync")
    public ResponseEntity<ApiResponse<Void>> autoSync(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "false") boolean finalize,
            @AuthenticationPrincipal UUID userId) {
        NoteGenerateRequest request = new NoteGenerateRequest(sessionId.toString());
        generationRequestService.request(request, userId, finalize);
        return ResponseEntity.accepted().body(ApiResponse.ok(null));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<NoteResponse>> getBySession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.getBySession(sessionId, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteWorkspaceResponse>>> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean inbox,
            @RequestParam(defaultValue = "false") boolean review,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.list(userId, query, inbox, review)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteWorkspaceResponse>> get(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.get(id, userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoteWorkspaceResponse>> create(@RequestBody NoteUpsertRequest request, @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.create(request, userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteWorkspaceResponse>> update(@PathVariable UUID id, @RequestBody NoteUpsertRequest request, @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.update(id, request, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        noteService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/study-materials")
    public ResponseEntity<ApiResponse<NoteWorkspaceResponse>> generateStudyMaterials(
            @PathVariable UUID id, @RequestBody StudyMaterialRequest request, @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.generateStudyMaterial(id, request.kind(), userId)));
    }
}
