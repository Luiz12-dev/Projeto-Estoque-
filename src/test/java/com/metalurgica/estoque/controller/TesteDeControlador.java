package com.metalurgica.estoque.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.metalurgica.estoque.config.SecurityConfig;
import com.metalurgica.estoque.config.SecurityFilter;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import com.metalurgica.estoque.exception.GlobalExceptionHandler;
import com.metalurgica.estoque.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Base dos testes de contrato HTTP.
 * <p>
 * Estes testes existem por um motivo concreto deste projeto: os modelos do
 * frontend em {@code core/models/*.model.ts} são mantidos em sincronia com os
 * DTOs daqui <b>à mão</b>, sem schema compartilhado. Renomear um campo no
 * backend quebraria a tela em silêncio, e nenhum teste de serviço perceberia —
 * eles trabalham com objetos Java, não com o JSON que sai pela rede.
 * <p>
 * O que se verifica aqui, e em nenhum outro lugar: código de status, validação
 * de entrada, nomes dos campos no JSON, tradução de exceção de domínio em
 * resposta HTTP e as regras de perfil por rota.
 * <p>
 * Usa {@code @WebMvcTest}: sobe apenas a camada web, com os serviços
 * substituídos por mock. Não toca banco, não precisa de Docker, roda em
 * milissegundos.
 */
@Import({ SecurityConfig.class, SecurityFilter.class, GlobalExceptionHandler.class })
public abstract class TesteDeControlador {

    @Autowired
    private WebApplicationContext contexto;

    protected MockMvc mockMvc;

    /**
     * O MockMvc e montado a mao com springSecurity(). No slice do @WebMvcTest o
     * suporte de seguranca dos testes nao vem ligado sozinho, e sem ele o
     * @WithMockUser nao chega ao filtro: toda rota responderia 403 e os testes
     * "provariam" que a API esta protegida sem nunca exercitar nada.
     */
    @BeforeEach
    void montarMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    /**
     * Instanciado aqui, e nao injetado: o slice do @WebMvcTest no Spring Boot 4
     * nao expoe o ObjectMapper da aplicacao. Serve so para montar o corpo das
     * requisicoes; a serializacao das respostas continua sendo a de producao,
     * que e justamente o que estes testes conferem.
     */
    protected final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    /**
     * O SecurityFilter é importado de verdade, para as regras de perfil valerem
     * como valem em produção. Suas duas dependências viram mock porque nenhum
     * teste daqui exercita token — a autenticação é simulada por
     * {@code @WithMockUser}.
     */
    @MockitoBean
    protected TokenService tokenService;

    @MockitoBean
    protected UsuarioRepository usuarioRepository;

    /** Serializa um objeto para o corpo da requisição. */
    protected String corpo(Object valor) throws Exception {
        return json.writeValueAsString(valor);
    }
}
