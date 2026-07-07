package com.metalurgica.estoque.exception;

public class TokenGenerationException extends RuntimeException {

    public TokenGenerationException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
