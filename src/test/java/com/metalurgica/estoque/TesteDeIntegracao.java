package com.metalurgica.estoque;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes que precisam de banco de verdade.
 * <p>
 * O restante da suíte roda com Mockito e não toca banco nenhum; quem herda
 * daqui sobe um PostgreSQL 16 em container, com as migrations aplicadas na
 * ordem real e {@code ddl-auto=validate}.
 * <p>
 * Isso existe porque o arranjo anterior não provava nada sobre produção: os
 * testes usavam H2 com o esquema gerado pelo Hibernate a partir das entidades,
 * e o Flyway desligado. Eram duas fontes de esquema diferentes, e nada
 * verificava que concordavam — um teste verde não dizia que a aplicação subiria.
 * <p>
 * O container é <b>estático</b> e nunca é parado de propósito: o Testcontainers
 * reaproveita a mesma instância entre as classes de teste e a derruba no fim da
 * JVM (padrão "singleton container"). Subir um Postgres por classe custaria
 * dezenas de segundos sem melhorar o isolamento, já que cada teste roda em
 * transação própria.
 */
@Tag(TesteDeIntegracao.TAG)
@ActiveProfiles("integracao")
public abstract class TesteDeIntegracao {

    /** Separa quem precisa de Docker de quem não precisa; ver o pom.xml. */
    public static final String TAG = "integracao";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("estoque_teste")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propriedades.add("spring.datasource.username", POSTGRES::getUsername);
        propriedades.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
