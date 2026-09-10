package com.metalurgica.estoque;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metalurgica.estoque.domain.entity.Usuario;
import com.metalurgica.estoque.domain.enums.Role;
import com.metalurgica.estoque.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O caminho completo — controller, serviço, repositório e banco de verdade —
 * pelas perguntas que o dono da oficina faz ao sistema.
 * <p>
 * Existe porque essa faixa não tinha nada. Os testes de unidade provam as
 * regras com o banco simulado; os de controlador provam o contrato HTTP com o
 * serviço simulado. Entre os dois havia um vão onde cabia, por exemplo, uma
 * consulta JPQL errada: verde nos dois lados e quebrada em produção.
 * <p>
 * Cada teste escreve pelo HTTP e confere pelo HTTP, sem atalho por repositório,
 * porque é assim que a tela usa o sistema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FluxoDeNegocioIntegrationTest extends TesteDeIntegracao {

    @Autowired private WebApplicationContext contexto;
    @Autowired private UsuarioRepository usuarioRepository;

    private MockMvc mockMvc;
    private Authentication comoDono;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * SecurityUtils devolve a entidade Usuario direto do principal, então
     * {@code @WithMockUser} não serve: ele coloca um User do Spring ali e o
     * cast estoura. O jeito honesto é autenticar com um usuário de verdade,
     * gravado no banco, que é o que acontece em produção.
     */
    @BeforeEach
    void prepararSessao() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        Usuario dono = usuarioRepository.findByLogin("dono-teste")
                .orElseGet(() -> usuarioRepository.save(Usuario.builder()
                        .nome("Dono do Teste")
                        .login("dono-teste")
                        .senha("irrelevante-nao-passa-por-login")
                        .role(Role.ADMIN)
                        .build()));

        comoDono = new UsernamePasswordAuthenticationToken(dono, null, dono.getAuthorities());
    }

    // =========================================================================
    //  Ajudantes: cada um cria pelo HTTP e devolve o id, para os testes lerem
    //  como a sequência de passos que a pessoa faria na tela.
    // =========================================================================

    private long postar(String rota, Map<String, ?> corpo) throws Exception {
        MvcResult r = mockMvc.perform(post(rota)
                        .with(authentication(comoDono)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String buscar(String rota) throws Exception {
        return mockMvc.perform(get(rota).with(authentication(comoDono)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private BigDecimal estoqueDe(long produtoId) throws Exception {
        return new BigDecimal(json.readTree(buscar("/api/produtos/" + produtoId))
                .get("quantidadeAtual").asText());
    }

    private BigDecimal custoDaOs(long osId) throws Exception {
        return new BigDecimal(json.readTree(buscar("/api/ordens-servico/" + osId))
                .get("custoTotal").asText());
    }

    private long criarCategoria(String nome) throws Exception {
        return postar("/api/categorias", Map.of("nome", nome));
    }

    /** Chapa de 1200 x 3000 mm a R$ 695, com corte a R$ 12 o metro. */
    private long criarChapa(String nome, long categoriaId, String quantidade) throws Exception {
        return postar("/api/produtos", Map.of(
                "nome", nome,
                "categoriaId", categoriaId,
                "quantidadeAtual", new BigDecimal(quantidade),
                "quantidadeMinima", new BigDecimal("2"),
                "unidadeMedida", "UN",
                "valorUnitario", new BigDecimal("695.00"),
                "larguraMm", new BigDecimal("1200"),
                "comprimentoMm", new BigDecimal("3000"),
                "precoMetroCorte", new BigDecimal("12.00")));
    }

    private long criarEmpresa(String nome) throws Exception {
        return postar("/api/empresas", Map.of("nome", nome));
    }

    private long criarOs(String descricao, long empresaId) throws Exception {
        return postar("/api/ordens-servico", Map.of(
                "descricao", descricao, "empresaId", empresaId));
    }

    // =========================================================================
    //  Os fluxos
    // =========================================================================

    @Test
    @DisplayName("Registrar um corte tira do estoque só a fração de chapa que a peça ocupou")
    void registrarCorteDebitaAFracaoDaChapa() throws Exception {
        long categoria = criarCategoria("Chapas " + System.nanoTime());
        long chapa = criarChapa("Chapa 3mm " + System.nanoTime(), categoria, "12");
        long empresa = criarEmpresa("Serralheria " + System.nanoTime());
        long os = criarOs("Portão basculante", empresa);

        BigDecimal antes = estoqueDe(chapa);

        // Duas peças de 200 x 100 numa chapa de 1200 x 3000: 40.000 mm² de
        // 3.600.000, vezes dois. Pouco mais de 1% da chapa.
        postar("/api/pecas-cortadas", Map.of(
                "nome", "Flange furada",
                "produtoId", chapa,
                "ordemServicoId", os,
                "larguraChapaMm", new BigDecimal("1200"),
                "comprimentoChapaMm", new BigDecimal("3000"),
                "valorChapa", new BigDecimal("695.00"),
                "larguraPecaMm", new BigDecimal("200"),
                "comprimentoPecaMm", new BigDecimal("100"),
                "quantidade", 2));

        BigDecimal consumo = antes.subtract(estoqueDe(chapa));

        assertThat(consumo)
                .as("o corte tem de descontar a fração ocupada, não chapas inteiras")
                .isEqualByComparingTo(new BigDecimal("0.0111"));
    }

    @Test
    @DisplayName("Devolver material sobrado tem de REDUZIR o custo da OS")
    void devolverMaterialReduzOCustoDaOs() throws Exception {
        long categoria = criarCategoria("Chapas " + System.nanoTime());
        long chapa = criarChapa("Chapa 3mm " + System.nanoTime(), categoria, "20");
        long empresa = criarEmpresa("Serralheria " + System.nanoTime());
        long os = criarOs("Estrutura", empresa);

        // Consumiu 10 unidades a R$ 23,40 — o custo da OS vira R$ 234,00.
        postar("/api/movimentacoes", Map.of(
                "produtoId", chapa, "tipo", "SAIDA", "ordemServicoId", os,
                "quantidade", new BigDecimal("10"), "valorUnitario", new BigDecimal("23.40")));

        assertThat(custoDaOs(os))
                .as("depois de consumir 10 a 23,40")
                .isEqualByComparingTo(new BigDecimal("234.00"));

        // Sobraram 4 e voltaram para a prateleira. O serviço gastou 6, não 14.
        postar("/api/movimentacoes", Map.of(
                "produtoId", chapa, "tipo", "ENTRADA", "ordemServicoId", os,
                "quantidade", new BigDecimal("4"), "valorUnitario", new BigDecimal("23.40")));

        assertThat(custoDaOs(os))
                .as("devolver sobra tem de baixar o custo para 6 x 23,40; "
                        + "hoje a consulta soma ENTRADA em vez de subtrair, e sobe para 327,60")
                .isEqualByComparingTo(new BigDecimal("140.40"));
    }

    @Test
    @DisplayName("Abrir uma categoria mostra os itens dela, e só eles")
    void abrirCategoriaTrazSomenteOsItensDela() throws Exception {
        String marca = String.valueOf(System.nanoTime());
        long chapas = criarCategoria("Chapas " + marca);
        long discos = criarCategoria("Discos " + marca);

        criarChapa("Chapa A " + marca, chapas, "5");
        criarChapa("Chapa B " + marca, chapas, "5");
        postar("/api/produtos", Map.of(
                "nome", "Disco 7 " + marca, "categoriaId", discos,
                "quantidadeAtual", new BigDecimal("30"), "quantidadeMinima", new BigDecimal("10"),
                "unidadeMedida", "UN", "valorUnitario", new BigDecimal("14.50")));

        String dentroDeChapas = buscar("/api/produtos?categoriaId=" + chapas);

        assertThat(json.readTree(dentroDeChapas).get("page").get("totalElements").asInt())
                .as("a categoria Chapas tem exatamente os dois itens que foram postos nela")
                .isEqualTo(2);
        assertThat(dentroDeChapas)
                .as("nenhum item de outra prateleira pode vazar para dentro desta")
                .doesNotContain("Disco 7 " + marca);

        // E o bloco tem de contar o mesmo que a listagem mostra.
        mockMvc.perform(get("/api/produtos/categorias/resumo").with(authentication(comoDono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + chapas + ")].totalItens").value(2));
    }

    @Test
    @DisplayName("Apagar categoria com item dentro é recusado, e a mensagem diz o que fazer")
    void apagarCategoriaComItemDentroEhRecusado() throws Exception {
        String marca = String.valueOf(System.nanoTime());
        long categoria = criarCategoria("Tubos " + marca);
        criarChapa("Tubo " + marca, categoria, "10");

        mockMvc.perform(delete("/api/categorias/" + categoria)
                        .with(authentication(comoDono)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        org.hamcrest.Matchers.containsString("Mova ou apague os itens")));

        // Continua lá: recusar não pode ter apagado nada pela metade.
        assertThat(buscar("/api/categorias")).contains("Tubos " + marca);
    }

    @Test
    @DisplayName("Categoria vazia pode ser apagada")
    void categoriaVaziaPodeSerApagada() throws Exception {
        String nome = "Prateleira " + System.nanoTime();
        long categoria = criarCategoria(nome);

        mockMvc.perform(delete("/api/categorias/" + categoria)
                        .with(authentication(comoDono)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(buscar("/api/categorias")).doesNotContain(nome);
    }

    @Test
    @DisplayName("Produto cadastrado com saldo nasce com a entrada no histórico")
    void produtoComSaldoGeraEntradaInicial() throws Exception {
        String marca = String.valueOf(System.nanoTime());
        long categoria = criarCategoria("Chapas " + marca);
        long chapa = criarChapa("Chapa inicial " + marca, categoria, "7");

        String historico = buscar("/api/movimentacoes?busca=Chapa inicial " + marca);

        assertThat(json.readTree(historico).get("page").get("totalElements").asInt())
                .as("cadastrar com saldo tem de deixar rastro: sem isso o estoque "
                        + "aparece do nada e ninguém sabe de onde veio")
                .isEqualTo(1);
        assertThat(historico).contains("ENTRADA");

        assertThat(estoqueDe(chapa)).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    @DisplayName("Orçamento emitido aparece na listagem daquele cliente")
    void orcamentoEmitidoApareceNaListagemDoCliente() throws Exception {
        String marca = String.valueOf(System.nanoTime());
        long categoria = criarCategoria("Chapas " + marca);
        long chapa = criarChapa("Chapa orcamento " + marca, categoria, "10");
        long empresa = criarEmpresa("Cliente " + marca);
        long outroCliente = criarEmpresa("Outro " + marca);

        long orcamento = postar("/api/orcamentos", Map.of(
                "empresaId", empresa,
                "margemPercentual", new BigDecimal("30"),
                "itens", java.util.List.of(Map.of(
                        "nome", "Flange",
                        "produtoId", chapa,
                        "larguraPecaMm", new BigDecimal("200"),
                        "comprimentoPecaMm", new BigDecimal("100"),
                        "quantidade", 50))));

        // A listagem usa fromEntitySemItens de propósito, para não fazer N+1:
        // ela traz código, empresa e total, e as peças ficam para o detalhe.
        // Conferir o nome da peça aqui seria cobrar da tela algo que ela não
        // promete — a primeira versão deste teste fazia isso e falhava por
        // culpa da expectativa, não do sistema.
        assertThat(buscar("/api/orcamentos?empresaId=" + empresa))
                .as("o orçamento tem de aparecer para quem ele foi feito")
                .contains("ORC-")
                .contains("719.00");

        assertThat(buscar("/api/orcamentos/" + orcamento))
                .as("e o detalhe tem de trazer a peça orçada")
                .contains("Flange");
        assertThat(json.readTree(buscar("/api/orcamentos?empresaId=" + outroCliente))
                        .get("page").get("totalElements").asInt())
                .as("e não pode aparecer para outro cliente")
                .isZero();

        // O bloco por empresa tem de enxergar o mesmo orçamento.
        mockMvc.perform(get("/api/orcamentos/empresas/resumo").with(authentication(comoDono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.empresaId == " + empresa + ")].totalOrcamentos").value(1));
    }

    @Test
    @DisplayName("Saída maior que o estoque é recusada e não move nada")
    void saidaMaiorQueOEstoqueEhRecusada() throws Exception {
        String marca = String.valueOf(System.nanoTime());
        long categoria = criarCategoria("Chapas " + marca);
        long chapa = criarChapa("Chapa escassa " + marca, categoria, "3");

        mockMvc.perform(post("/api/movimentacoes")
                        .with(authentication(comoDono)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "produtoId", chapa, "tipo", "SAIDA",
                                "quantidade", new BigDecimal("10"),
                                "valorUnitario", new BigDecimal("695.00")))))
                .andExpect(status().isBadRequest());

        assertThat(estoqueDe(chapa))
                .as("recusar tem de deixar o estoque exatamente como estava")
                .isEqualByComparingTo(new BigDecimal("3"));
    }
}
