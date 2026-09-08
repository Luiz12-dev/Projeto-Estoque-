package com.metalurgica.estoque.exception;

import com.metalurgica.estoque.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handleEstoqueInsuficiente(EstoqueInsuficienteException ex) {
        log.warn("Estoque insuficiente: {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Estoque Insuficiente",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Recurso Não Encontrado",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getMessage());
        var error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflito de Dados",
                "Já existe um registro com esses dados. Verifique campos que precisam ser únicos (nome, código, login).",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Erro de validação: {}", mensagem);
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                mensagem,
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição Inválida",
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Trata conflitos de optimistic locking (concorrência no estoque).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência (optimistic lock): {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflito de Concorrência",
                "Os dados foram alterados por outro usuário. Por favor, recarregue e tente novamente.",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Trata erros de geração de token JWT.
     */
    @ExceptionHandler(TokenGenerationException.class)
    public ResponseEntity<ErrorResponse> handleTokenGeneration(TokenGenerationException ex) {
        log.error("Erro ao gerar token JWT: ", ex);
        var error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro de Autenticação",
                "Ocorreu um erro ao gerar o token de acesso. Tente novamente.",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Corpo de requisição que o Jackson não consegue ler: JSON malformado, tipo
     * incompatível, enum inexistente.
     * <p>
     * Cair no tratamento genérico transformava erro do cliente em 500. Isso
     * atrapalha de duas formas: o log da oficina fica cheio de "erro interno"
     * que não é do servidor, e a tela não consegue distinguir "mandei algo
     * errado" de "o sistema quebrou".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        log.warn("Corpo de requisição ilegível: {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição Inválida",
                "Não foi possível ler os dados enviados. Confira o formato da requisição.",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Parâmetro da URL com tipo errado: texto onde se espera número, ou valor
     * fora do enum — por exemplo {@code ?situacao=INEXISTENTE}.
     * <p>
     * Também caía no tratamento genérico e virava 500, escondendo que o erro
     * era do cliente.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParametroInvalido(MethodArgumentTypeMismatchException ex) {
        log.warn("Parâmetro inválido: {}={}", ex.getName(), ex.getValue());
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição Inválida",
                String.format("O valor '%s' não é válido para o parâmetro '%s'.",
                        ex.getValue(), ex.getName()),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Rota que não existe. Sem isto, um erro de digitação numa chamada aparecia
     * como 500 e parecia defeito do servidor.
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleRotaInexistente(Exception ex) {
        log.warn("Rota inexistente: {}", ex.getMessage());
        var error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não Encontrado",
                "Esta rota não existe.",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro inesperado: ", ex);
        var error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro Interno",
                "Ocorreu um erro inesperado: " + ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
