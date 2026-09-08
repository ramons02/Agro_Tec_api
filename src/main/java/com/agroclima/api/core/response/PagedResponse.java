package com.agroclima.api.core.response;

import java.util.List;

public record PagedResponse<T>(List<T> itens, long total, int page, int pageSize) {}
