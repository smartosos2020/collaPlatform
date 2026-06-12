package com.colla.platform.shared.pagination;

import java.util.List;

public record PageResponse<T>(
    List<T> data,
    int page,
    int pageSize,
    long total,
    String requestId
) {
}

