package com.abcbank.mobile_banking.dto.response;

import java.util.List;

public record ServiceRequestListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ServiceRequestItemResponse> items
) {
}