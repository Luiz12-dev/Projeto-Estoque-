package com.metalurgica.estoque.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filtro de rate limiting para o endpoint de login.
 * Limita a 10 tentativas por IP a cada 15 minutos.
 * <p>
 * Implementação thread-safe com auto-limpeza periódica para evitar memory leaks.
 * A decisão de bloqueio é feita atomicamente dentro do {@code compute} para
 * eliminar race conditions entre contagem e verificação.
 * <p>
 * Em produção com múltiplas instâncias, considere usar Redis ou bucket4j
 * para rate limiting distribuído.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutos

    private final Map<String, RateInfo> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isLoginEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);

        // Decisão atômica: incrementa o contador E verifica o limite dentro do compute,
        // eliminando a race condition entre compute() e get() que existia antes.
        boolean blocked = isRateLimited(ip);

        if (blocked) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"erro\":\"Rate Limit\",\"mensagem\":\"Muitas tentativas de login. Tente novamente em 15 minutos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Incrementa o contador de tentativas e retorna se o IP deve ser bloqueado.
     * Toda a lógica é executada atomicamente dentro de {@code compute()},
     * garantindo que contagem e verificação não sofram race conditions.
     */
    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        // Usamos um array de 1 elemento como holder para capturar o resultado
        // de dentro do lambda (variáveis locais devem ser effectively final).
        boolean[] result = {false};

        attempts.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                // Janela expirada ou primeiro acesso — inicia nova janela
                return new RateInfo(now, new AtomicInteger(1));
            }

            int currentCount = existing.count.incrementAndGet();
            if (currentCount > MAX_ATTEMPTS) {
                result[0] = true;
            }
            return existing;
        });

        return result[0];
    }

    /**
     * Limpeza periódica de entradas expiradas para evitar memory leak.
     * Executa a cada 15 minutos. Remove IPs cuja janela já expirou,
     * liberando memória de clientes que não fazem mais tentativas.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000L)
    void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, RateInfo>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RateInfo> entry = it.next();
            if (now - entry.getValue().windowStart > WINDOW_MS) {
                it.remove();
            }
        }
    }

    private boolean isLoginEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/login".equals(request.getRequestURI());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateInfo {
        final long windowStart;
        final AtomicInteger count;

        RateInfo(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
