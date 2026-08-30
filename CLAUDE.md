# CLAUDE.md — Backend

Orientações para o Claude Code ao trabalhar neste repositório.

## Contexto

Sistema de gestão da **Metalúrgica Fantineli**, uma oficina de ~15 pessoas com
5 usuários de sistema: 4 sócios e 1 funcionário do escritório. O cliente final
é o Leo, dono da metalúrgica.

Cada decisão de interface e de funcionalidade se justifica pelo que o Leo
realmente faria com o sistema, não pela elegância técnica. Controle que existe
para tranquilizar quem escreveu o código não pertence à tela.

## Dois repositórios independentes

Não existe repositório na raiz. Este é o backend; o frontend Angular fica em
`../Projeto-Estoque-FrontEnd`, com histórico e remoto próprios. Comandos de git
e build precisam rodar dentro de um dos dois.

Os DTOs daqui e os modelos de `core/models/*.model.ts` do frontend são mantidos
em sincronia **à mão** — não há schema compartilhado nem geração de código. Ao
mudar a forma de um request ou response, atualize o modelo e o serviço do outro
lado.

## Comandos

```bash
docker compose up -d      # Postgres 16 na porta 5433 + Flyway
mvn spring-boot:run       # API na 8080 (exige JWT_SECRET)
mvn test                  # testes unitários, sem Docker
mvn verify                # acrescenta os de integração, exige Docker
mvn test -Dtest=Classe#metodo
```

Não há Checkstyle nem Spotless configurados.

## Arquitetura

Camadas estritas: **Controller → Service → Repository → Entity → DTO Response**.
Requests em `dto/request`, responses são `record` imutáveis em `dto/response`
com fábrica estática `fromEntity()`. Exceções de domínio
(`EstoqueInsuficienteException`, `RecursoNaoEncontradoException`) viram resposta
HTTP no `GlobalExceptionHandler`.

Domínio central: `Produto` ↔ `Movimentacao` (ENTRADA/SAIDA, N:1 para Produto e
Usuario), opcionalmente ligada a uma `OrdemServico`, que pertence a uma
`Empresa`. `Usuario` implementa `UserDetails` e carrega um `Role`.

### Regras que não podem ser quebradas

- Estoque nunca fica negativo: toda SAIDA é validada.
- Registrar movimentação atualiza `Produto.quantidadeAtual` no mesmo
  `@Transactional`.
- Editar uma movimentação aplica delta **compensatório**, não sobrescreve.
- Produto criado com quantidade > 0 gera ENTRADA inicial automática.
- Códigos de OS e orçamento são sequenciais, gerados por sequence do Postgres.
- Movimentação só se liga a OS `ABERTA` ou `EM_ANDAMENTO`.

## Módulo de Cortes

`CalculadoraCorte` é a **fonte autoritativa** da fórmula. O frontend reproduz a
mesma conta para dar retorno instantâneo enquanto se digita, e a duplicação é
travada por `src/test/resources/casos-corte.json`, byte a byte idêntico a
`core/testing/casos-corte.json` do frontend. **Ao alterar um, altere o outro e
rode os testes dos dois lados.**

O preço tem duas parcelas: **material** (fração da chapa que a peça ocupa) e
**corte** (contorno + furos + recortes internos, ao preço por metro). Sem a
parcela de corte, uma peça lisa e uma cheia de furos custariam o mesmo.

`Orcamento` congela os parâmetros da chapa no momento da emissão: reajuste
posterior não altera valor já informado ao cliente.

`PecaCortada` registra corte executado. Debita **a fração de chapa consumida**,
não o número de peças, e usa a chapa como unidade da movimentação (valor
unitário = preço da chapa). Só o rateio de material entra aí — tempo de máquina
não é estoque.

Uma peça cabe na chapa se as **dimensões** couberem em alguma das duas
orientações (giro de 90° é válido). Comparar só áreas aceitaria peças
impossíveis de cortar.

## Segurança

JWT stateless (HMAC256, Auth0 `java-jwt`, validade de 2 horas). Pipeline:
`RateLimitFilter` (10 tentativas de login / 15 min por IP) → `SecurityFilter` →
controller.

Três perfis, desenhados sobre quem pode ver **margem de lucro**:

| Perfil | Quem | Margem | Usuários |
|---|---|---|---|
| `ADMIN` | os 4 sócios | sim | sim |
| `ESCRITORIO` | quem monta orçamento | sim | não |
| `OPERADOR` | chão de fábrica | não | não |

`/api/orcamentos/**` e `/api/dashboard/**` exigem ADMIN ou ESCRITORIO;
`/api/usuarios/**` é exclusivo de ADMIN. A simulação de preço entra na mesma
regra: bloquear só a listagem deixaria a margem acessível por outro caminho.

### Configuração obrigatória

`JWT_SECRET` (mínimo 32 caracteres) — sem ela a aplicação **recusa subir**,
verificada por `VerificadorDeConfiguracao` antes de qualquer bean existir.

`ADMIN_LOGIN` / `ADMIN_SENHA` — necessárias apenas quando a tabela de usuários
está vazia fora de desenvolvimento. Em `dev`/`default`/`test` há o atalho
`cadu`/`123`. Ver `.env.example` e `BootstrapAdministrador`.

`CORS_ORIGINS` precisa conter o endereço real do frontend, ou o navegador leva
403 no login enquanto `curl` funciona — sintoma que já custou tempo.

## Banco

Esquema inteiramente gerido por Flyway (`db/migration`, V1…V17). **Nunca edite
migration existente**; adicione a próxima `V{n}__descricao.sql`. `ddl-auto` é
`none` em produção.

O `pom.xml` usa `spring-boot-starter-flyway`, não `flyway-core` cru: o Spring
Boot 4 separou as autoconfigurações em módulos, e sem o starter as migrations
são ignoradas em silêncio.

`docker compose up -d` roda o Flyway uma vez ao subir o container. Se você
adicionar migration com o Postgres já rodando, rode `docker compose up flyway`
de novo e reinicie a aplicação.

## Testes

Unitários com Mockito não tocam banco e rodam em `mvn test`. Os que sobem
contexto herdam de `TesteDeIntegracao`, ganham `@Tag("integracao")` e rodam em
`mvn verify` contra **PostgreSQL 16 em container**, com Flyway ligado e
`ddl-auto=validate`.

Atenção ao limite conhecido: `validate` confere existência e tipo de coluna,
**não confere nulidade nem default**. Divergências desse tipo precisam de
comparação manual contra o esquema real.

## Trabalho assíncrono

PDF da OS concluída (`PdfService.gerarPdfAsync`, OpenPDF) roda fora da thread de
request via `@Async`. Não há broker nem cache — decisão deliberada para uma
oficina desse porte. **Não reintroduza Kafka ou Redis** sem um problema de
escala concreto que justifique a superfície de operação.

## Pendências conhecidas

**`docs/pendencias.md` é a lista completa**, com reprodução, correção sugerida e
verificação exigida para cada item. Leia antes de começar trabalho novo.

Os dois de maior urgência:

- **Bug**: devolver material ao estoque faz o custo da OS **subir**. A query
  `somarCustosPorOsIds` não filtra por tipo, então ENTRADA é somada em vez de
  subtraída. Reproduzido contra a API.
- Nenhum controller tem teste: o contrato HTTP não é verificado, e os modelos do
  frontend são mantidos em sincronia à mão com 30 DTOs.

Há também três perguntas de precificação abertas com o cliente (aproveitamento
da chapa, valor mínimo de serviço e custo de perfuração) que não devem ser
respondidas por chute — o resultado seria um número errado com aparência de
precisão.
