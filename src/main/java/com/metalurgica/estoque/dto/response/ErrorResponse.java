package com.metalurgica.estoque.dto.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String erro,
        String mensagem,
        LocalDateTime timestamp
) {}
