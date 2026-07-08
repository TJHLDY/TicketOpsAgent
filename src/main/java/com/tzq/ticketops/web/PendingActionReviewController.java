package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.PendingActionRecord;
import com.tzq.ticketops.agent.PendingActionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/pending-actions")
public class PendingActionReviewController {

    private static final String EXECUTION_STATUS = "NOT_EXECUTED_MOCK_ONLY";

    private final AgentExecutionLogRepository logRepository;

    public PendingActionReviewController(AgentExecutionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping("/{actionId}")
    public PendingActionReviewResponse find(@PathVariable long actionId) {
        return logRepository.findPendingActionById(actionId)
                .map(record -> PendingActionReviewResponse.from(record, ""))
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, ApiErrorCode.PENDING_ACTION_NOT_FOUND));
    }

    @PostMapping(
            value = "/{actionId}/approve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public PendingActionReviewResponse approve(
            @PathVariable long actionId,
            @Valid @RequestBody PendingActionReviewRequest request
    ) {
        return review(
                actionId,
                PendingActionStatus.APPROVED,
                request,
                "Action approved for audit demo only. No real account operation was executed."
        );
    }

    @PostMapping(
            value = "/{actionId}/reject",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public PendingActionReviewResponse reject(
            @PathVariable long actionId,
            @Valid @RequestBody PendingActionReviewRequest request
    ) {
        return review(
                actionId,
                PendingActionStatus.REJECTED,
                request,
                "Action rejected for audit demo only. No real account operation was executed."
        );
    }

    private PendingActionReviewResponse review(
            long actionId,
            PendingActionStatus status,
            PendingActionReviewRequest request,
            String message
    ) {
        PendingActionRecord existing = logRepository.findPendingActionById(actionId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, ApiErrorCode.PENDING_ACTION_NOT_FOUND));
        if (existing.status() != PendingActionStatus.PENDING) {
            throw ApiException.of(HttpStatus.CONFLICT, ApiErrorCode.PENDING_ACTION_ALREADY_REVIEWED);
        }
        logRepository.updatePendingActionReview(actionId, status, request.reviewerId(), request.reviewComment());
        PendingActionRecord updated = logRepository.findPendingActionById(actionId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, ApiErrorCode.PENDING_ACTION_NOT_FOUND));
        return PendingActionReviewResponse.from(updated, message);
    }

    public record PendingActionReviewRequest(
            @NotBlank String reviewerId,
            @NotBlank @Size(max = 200) String reviewComment
    ) {
    }

    public record PendingActionReviewResponse(
            long id,
            String ticketId,
            String actionType,
            String summary,
            String status,
            String reviewerId,
            String reviewComment,
            Instant reviewedAt,
            String executionStatus,
            String message
    ) {
        static PendingActionReviewResponse from(PendingActionRecord record, String message) {
            return new PendingActionReviewResponse(
                    record.id(),
                    record.ticketId(),
                    record.actionType().name(),
                    record.summary(),
                    record.status().name(),
                    record.reviewerId(),
                    record.reviewComment(),
                    record.reviewedAt(),
                    EXECUTION_STATUS,
                    message
            );
        }
    }
}
