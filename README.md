# 🏭 Estoque Metalúrgica — Backend API

API RESTful completa para gestão de inventário industrial e ordens de serviço, construída com **Java 21** e **Spring Boot 4**. Sistema real de produção para metalúrgica de pequeno/médio porte, demonstrando domínio em arquitetura em camadas, segurança com JWT, integridade transacional de estoque, módulo de Ordens de Serviço com rastreio de custos por serviço, queries nativas otimizadas para PostgreSQL, edição inline com recálculo automático, paginação e testes automatizados.

---

## 🚀 Tecnologias

| Tecnologia | Versão | Descrição |
|-----------|--------|-----------|
| **Java** | 21 | Linguagem principal (LTS) — Records, Pattern Matching |
| **Spring Boot** | 4.0.6 | Framework principal |
| **Spring Security** | 6.x | Autenticação e autorização Stateless |
| **Spring Data JPA** | 3.x | Persistência ORM e queries derivadas |
| **PostgreSQL** | 16 | Banco de dados relacional (ACID) |
| **Flyway** | 10.x | Versionamento de migrations do schema |
| **Docker Compose** | — | Orquestração do PostgreSQL + Flyway |
| **Java JWT (Auth0)** | 4.5.2 | Geração e validação de tokens JWT (HMAC256) |
| **Lombok** | — | Redução de boilerplate (@Builder, @Getter) |
| **Bean Validation** | 3.x | Validação declarativa dos dados de entrada |
| **BCrypt** | — | Hash de senhas com salt automático |
| **JUnit 5 & Mockito** | — | Testes unitários isolados com mocks |
| **MockMvc** | — | Testes de integração das rotas HTTP |

---

## 📐 Arquitetura

O projeto segue a **Arquitetura em Camadas (Layered Architecture)** com separação rigorosa de responsabilidades:

```
src/main/java/com/metalurgica/estoque/
├── config/                    # Configuração de Segurança
│   ├── SecurityConfig.java    #   → SecurityFilterChain, CORS, BCrypt, Stateless
│   └── SecurityFilter.java    #   → Filtro JWT (OncePerRequestFilter)
├── controller/                # Camada REST
│   ├── AuthController.java    #   → POST /login (público)
│   ├── DashboardController.java  → GET /dashboard (métricas + contadores de OS)
│   ├── MovimentacaoController.java → CRUD + busca + edição inline
│   ├── OrdemServicoController.java → CRUD + filtro por data/status + movimentações
│   └── ProdutoController.java #   → CRUD + busca + estoque baixo
├── domain/
│   ├── entity/                # Entidades JPA
│   │   ├── Movimentacao.java  #   → ManyToOne(LAZY) → Produto, Usuario, OrdemServico
│   │   ├── OrdemServico.java  #   → OneToMany → Movimentacoes, status + prioridade
│   │   ├── Produto.java       #   → Lifecycle hooks (@PrePersist, @PreUpdate)
│   │   └── Usuario.java       #   → Implementa UserDetails (Spring Security)
│   ├── enums/
│   │   ├── PrioridadeOrdemServico.java → BAIXA | MEDIA | ALTA | URGENTE
│   │   ├── StatusOrdemServico.java     → ABERTA | EM_ANDAMENTO | CONCLUIDA | CANCELADA
│   │   └── TipoMovimentacao.java       → ENTRADA | SAIDA
│   └── repository/            # Spring Data JPA
│       ├── MovimentacaoRepository.java → JPQL com JOIN FETCH + batch queries por OS
│       ├── OrdemServicoRepository.java → Native SQL com CAST para PostgreSQL
│       ├── ProdutoRepository.java      → Busca case-insensitive + contagem estoque baixo
│       └── UsuarioRepository.java      → findByLogin()
├── dto/
│   ├── request/               # Validação de entrada (@NotNull, @NotBlank, @Positive)
│   │   ├── LoginRequest.java
│   │   ├── MovimentacaoRequest.java      → + ordemServicoId opcional
│   │   ├── MovimentacaoUpdateRequest.java  → DTO parcial para edição inline
│   │   ├── OrdemServicoRequest.java      → descricao, cliente, prioridade, observacao
│   │   ├── OrdemServicoUpdateRequest.java → status, descricao, cliente (patch parcial)
│   │   ├── ProdutoRequest.java
│   │   └── ProdutoUpdateRequest.java
│   └── response/              # Formatação de saída (Records imutáveis)
│       ├── DashboardResponse.java   → Métricas financeiras + contadores de OS
│       ├── ErrorResponse.java       → Padrão de erro unificado
│       ├── MovimentacaoResponse.java → fromEntity() com valorTotal + dados da OS
│       ├── OrdemServicoResponse.java → fromEntity() com custoTotal + totalMovimentacoes
│       ├── ProdutoResponse.java     → Flag computada isEstoqueBaixo
│       └── TokenResponse.java      → JWT token wrapper
├── exception/                 # Tratamento Global
│   ├── EstoqueInsuficienteException.java → 400 Bad Request
│   ├── GlobalExceptionHandler.java       → @RestControllerAdvice (6 handlers)
│   └── RecursoNaoEncontradoException.java → 404 Not Found
└── service/                   # Regras de Negócio
    ├── AuthService.java       #   → Autenticação + geração de token
    ├── MovimentacaoService.java → Registro, edição inline e recálculo transacional
    ├── OrdemServicoService.java → CRUD de OS + vinculação de materiais + custos
    ├── ProdutoService.java    #   → CRUD + geração automática de movimentação inicial
    └── TokenService.java      #   → HMAC256, expiração de 7 dias, issuer customizado
```

Cada fluxo segue: **Controller → Service → Repository → Entity → DTO Response**

---

## 🗄️ Diagrama de Entidades

```
┌──────────────────┐
│     USUARIO      │
│──────────────────│
│ id       BIGINT  │────────────────────┐
│ nome     VARCHAR │                    │
│ login    VARCHAR │ (unique)           │
│ senha    VARCHAR │ (BCrypt hash)      │
└──────────────────┘                    │
                                        │ N:1
┌──────────────────┐                    │
│  ORDEM_SERVICO   │                    │
│──────────────────│                    │
│ id         BIGINT│◄──────┐           │
│ codigo     VARCHAR│(unique)│           │
│ descricao  VARCHAR│        │           │
│ empresa_id  FK   │        │ (→ EMPRESA)│
│ status     ENUM  │        │           │
│ prioridade ENUM  │        │           │
│ data_abertura TS │        │           │
│ data_conclusao TS│        │ N:1       │
│ observacao TEXT  │        │(opcional)  │
│ usuario_id  FK  │────────┼───────────┘
└──────────────────┘        │
                             │
┌──────────────────┐       ┌┴─────────────────────────┐
│     PRODUTO      │       │       MOVIMENTACAO       │
│──────────────────│       │──────────────────────────│
│ id       BIGINT  │──────<│ id             BIGINT    │
│ nome     VARCHAR │  1:N  │ tipo           ENUM      │ ← ENTRADA | SAIDA
│ categoria VARCHAR│       │ quantidade     DEC(19,4) │
│ qt_atual  DEC    │       │ valor_unitario DEC(19,2) │
│ qt_minima DEC    │       │ data_hora      TIMESTAMP │
│ un_medida VARCHAR│       │ observacao     TEXT       │
│ valor_unit DEC   │       │ produto_id     FK        │
│ criado_em  TS    │       │ usuario_id     FK        │
│ atualizado_em TS │       │ ordem_servico_id FK      │ ← (opcional)
└──────────────────┘       └──────────────────────────┘
```

**Relação OS ↔ Movimentação:** Uma OS atua como "container de custos". Movimentações podem ser vinculadas opcionalmente a uma OS para rastrear o consumo de material por serviço.

---

## 📋 Endpoints

### 🔓 Autenticação (`/api/auth`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/login` | Autentica usuário e retorna JWT (7 dias) | Público |

### 📊 Dashboard (`/api/dashboard`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/` | Métricas: totais de produtos, financeiro, alertas, últimas movimentações, contadores de OS (ativas, concluídas, total) | Autenticado |

### 📦 Produtos (`/api/produtos`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=&page=&size=` | Listar produtos (paginado, filtro por nome/categoria) | Autenticado |
| GET | `/{id}` | Buscar produto por ID | Autenticado |
| GET | `/estoque-baixo` | Listar todos os produtos com estoque abaixo do mínimo | Autenticado |
| POST | `/` | Cadastrar novo produto (gera movimentação de entrada automática) | Autenticado |
| PUT | `/{id}` | Atualizar dados cadastrais do produto | Autenticado |

### 🔄 Movimentações (`/api/movimentacoes`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=&tipo=&page=&size=` | Listar histórico com filtros compostos (nome do produto + tipo) | Autenticado |
| GET | `/produto/{produtoId}` | Listar movimentações de um produto específico | Autenticado |
| POST | `/` | Registrar entrada ou saída (recalcula estoque, aceita `ordemServicoId` opcional) | Autenticado |
| PUT | `/{id}` | Edição inline de movimentação (recálculo compensatório do estoque) | Autenticado |

### 📋 Ordens de Serviço (`/api/ordens-servico`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=&status=&dataInicio=&dataFim=&page=&size=` | Listar OS com filtros por texto (código/descrição/empresa), status e período | Autenticado |
| GET | `/{id}` | Buscar OS por ID (com custo total e total de movimentações) | Autenticado |
| GET | `/{id}/movimentacoes?page=&size=` | Listar movimentações vinculadas a uma OS | Autenticado |
| GET | `/{id}/pdf` | Baixar o PDF gerado ao concluir a OS | Autenticado |
| POST | `/` | Criar nova OS (gera código sequencial automático: OS-0001, OS-0002...) | Autenticado |
| PUT | `/{id}` | Atualizar OS: status, descrição, empresa, prioridade, observação | Autenticado |

### 🏢 Empresas (`/api/empresas`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=&page=&size=` | Listar empresas (paginado, filtro por nome/CNPJ) | Autenticado |
| GET | `/{id}` | Buscar empresa por ID | Autenticado |
| POST | `/` | Cadastrar nova empresa | Autenticado |
| PUT | `/{id}` | Atualizar dados da empresa | Autenticado |

### 👤 Usuários (`/api/usuarios`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/` | Listar usuários do sistema | ROLE_ADMIN |
| POST | `/` | Criar novo usuário (nome, login, senha, role) | ROLE_ADMIN |
| PUT | `/{id}` | Atualizar nome/role e, opcionalmente, redefinir a senha | ROLE_ADMIN |

### ✂️ Corte a Laser (`/api/pecas-cortadas`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/api/pecas-cortadas` | Registrar peça cortada (calcula valor proporcional à área da chapa e debita o estoque via Movimentação) | Autenticado |
| GET | `/api/ordens-servico/{id}/pecas-cortadas` | Listar peças cortadas de uma OS | Autenticado |

---

## 🧠 Regras de Negócio

### Estoque
- **Impedimento de Estoque Negativo**: toda saída é validada contra o saldo atual. Tentativas de ultrapassar o limite disparam `EstoqueInsuficienteException`.
- **Recálculo Transacional (Registro)**: ao criar uma movimentação, o `quantidadeAtual` do produto é atualizado atomicamente na mesma transação (`@Transactional`).
- **Recálculo Compensatório (Edição Inline)**: ao alterar a quantidade de uma movimentação já registrada, o sistema calcula a diferença e aplica a compensação correta no estoque.
- **Geração Automática de Saldo Inicial**: ao cadastrar um produto com `quantidadeAtual > 0`, cria automaticamente uma movimentação de ENTRADA.
- **Valor Unitário com Fallback**: se o valor unitário não for informado na movimentação, usa o valor cadastrado no produto.
- **Auditoria**: toda movimentação registra o usuário responsável e timestamp via `SecurityContextHolder`.

### Ordens de Serviço
- **Código Sequencial Automático**: gerado via `MAX(codigo) + 1` no formato `OS-0001`.
- **Status Livre**: transições livres entre ABERTA, EM_ANDAMENTO, CONCLUIDA e CANCELADA (permite reabertura).
- **Data de Conclusão Automática**: definida ao mover para CONCLUIDA/CANCELADA, limpa ao reabrir para ABERTA/EM_ANDAMENTO.
- **Custo por Serviço**: calculado em batch via queries agregadas (`SUM(quantidade * valorUnitario)` por OS).
- **Validação de Vínculo**: só permite vincular movimentações a OS que estejam ABERTA ou EM_ANDAMENTO.
- **Filtro por Período**: endpoint de listagem aceita `dataInicio` e `dataFim` para filtrar por data de abertura.

### Corte a Laser
- **Rateio Proporcional por Área**: o valor de cada peça é calculado como `valorChapa × (área da peça / área da chapa)` — a peça "paga" a fração da chapa que ocupa.
- **Rejeição de Peça Maior que a Chapa**: dimensões da peça maiores que as da chapa disparam `IllegalArgumentException` (400).
- **Reaproveita o Fluxo de Estoque**: o registro de um corte gera uma Movimentação de SAIDA normal (mesma validação de estoque insuficiente e de status da OS) — não existe um caminho paralelo de baixa de estoque.

---

## 🔐 Segurança

A API utiliza **JWT (JSON Web Token)** com algoritmo **HMAC256** em modo totalmente **Stateless**:

1. Faça login via `POST /api/auth/login` com `login` e `senha`
2. O token JWT é retornado com validade de **2 horas**
3. Inclua o token em todas as requisições subsequentes:
```
Authorization: Bearer <seu_token_aqui>
```

### Pipeline de Segurança
```
Request → SecurityFilter (OncePerRequestFilter)
              ↓
         Extrai token do header Authorization
              ↓
         TokenService.validarToken() (HMAC256 + issuer check)
              ↓
         Busca Usuario no banco → SecurityContextHolder
              ↓
         Controller recebe request autenticado
```

### Tratamento de Exceções (GlobalExceptionHandler)
| Exceção | HTTP Status | Cenário |
|---------|-------------|---------|
| `EstoqueInsuficienteException` | 400 | Saída maior que estoque disponível |
| `RecursoNaoEncontradoException` | 404 | Produto, Movimentação ou OS inexistente |
| `DataIntegrityViolationException` | 409 | Nome de produto ou código de OS duplicado |
| `MethodArgumentNotValidException` | 400 | Campos obrigatórios faltando |
| `IllegalArgumentException` | 400 | Parâmetros inválidos (ex: vincular mov a OS fechada) |
| `Exception` (genérico) | 500 | Erro inesperado (sem expor stack trace) |

---

## 🧪 Testes Automatizados

### Testes Unitários — `MovimentacaoServiceTest`
| Teste | O que valida |
|-------|-------------|
| `deveLancarExcecaoAoRegistrarSaidaMaiorQueEstoque` | Impede saída com estoque insuficiente |
| `deveRecalcularEstoqueAoRegistrarEntrada` | Soma correta ao registrar entrada |
| `deveRecalcularEstoqueAoAtualizarQuantidadeDaMovimentacao` | Compensação matemática na edição inline |
| `deveLancarExcecaoAoAtualizarSaidaMaiorQueEstoque` | Impede edição que causaria estoque negativo |

### Testes Unitários — `ProdutoServiceTest`
| Teste | O que valida |
|-------|-------------|
| `deveCriarMovimentacaoDeEntradaAoCriarProdutoComQuantidadeInicial` | Gera movimentação automática ao cadastrar |
| `naoDeveCriarMovimentacaoAoCriarProdutoComQuantidadeZero` | Não gera movimentação desnecessária |

### Testes de Integração — `SecurityIntegrationTest`
| Teste | O que valida |
|-------|-------------|
| `deveRetornar403AoSolicitarEndpointProtegidoSemToken` | Rotas privadas bloqueadas |
| `devePermitirAcessoAEndpointPublicoSemToken` | Rota de login acessível |

```bash
mvn clean test    # Executa toda a suíte
```

---

## 📁 Migrations (Flyway)

| Versão | Arquivo | Descrição |
|--------|---------|-----------|
| V1 | `V1__criar_tabela_usuario.sql` | Tabela `usuario` (id, nome, login, senha) |
| V2 | `V2__criar_tabela_produto.sql` | Tabela `produto` (8 colunas com timestamps) |
| V3 | `V3__criar_tabela_movimentacao.sql` | Tabela `movimentacao` (FKs + índices em produto_id e data_hora) |
| V4 | `V4__adicionar_valor_unitario_produto.sql` | Adição da coluna `valor_unitario` ao produto |
| V5 | `V5__criar_tabela_ordem_servico.sql` | Tabela `ordem_servico` (código, status, prioridade, datas, FK usuario) |
| V6 | `V6__adicionar_ordem_servico_movimentacao.sql` | Coluna `ordem_servico_id` (FK) na tabela movimentacao |
| V7 | `V7__criar_sequence_ordem_servico.sql` | Sequence do PostgreSQL para gerar código da OS atomicamente |
| V8 | `V8__adicionar_version_produto.sql` | Coluna `version` (optimistic locking) na tabela produto |
| V9 | `V9__adicionar_version_ordem_servico.sql` | Coluna `version` (optimistic locking) na tabela ordem_servico |
| V10 | `V10__adicionar_mao_de_obra_os.sql` | Coluna `valor_mao_de_obra` na tabela ordem_servico |
| V11 | `V11__adicionar_role_usuario.sql` | Coluna `role` (ADMIN/OPERADOR) na tabela usuario |
| V12 | `V12__criar_tabela_empresa.sql` | Tabela `empresa` (nome, CNPJ, telefone, e-mail, endereço) |
| V13 | `V13__migrar_cliente_para_empresa.sql` | Migra o campo livre `ordem_servico.cliente` para `empresa_id` (FK); remove a coluna antiga |
| V14 | `V14__criar_tabela_peca_cortada.sql` | Tabela `peca_cortada` (corte a laser: dimensões, valor calculado, vínculo com produto/OS/movimentação) |
| V15 | `V15__adicionar_parametros_corte_produto.sql` | Colunas `largura_mm`, `comprimento_mm` e `preco_metro_corte` no produto — parâmetros da chapa usados no orçamento |
| V16 | `V16__criar_tabelas_orcamento.sql` | Tabelas `orcamento` e `orcamento_item`, mais a sequence do código — orçamento de corte com valores congelados |

---

## ⚙️ Como Executar

### Pré-requisitos
- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### 1. Subir o banco de dados
```bash
docker compose up -d
```
> O Docker Compose sobe o PostgreSQL 16 na porta **5433** e executa as migrations via Flyway automaticamente.

### 2. Configurar as variáveis de ambiente

Copie `.env.example` e preencha. **`JWT_SECRET` é obrigatória** — sem ela a
aplicação recusa subir, e é de propósito: uma chave conhecida permite que
qualquer pessoa forje um token de administrador.

```powershell
# PowerShell — gere um valor novo e único por ambiente
$env:JWT_SECRET = -join ((48..57)+(97..122) | Get-Random -Count 64 | % {[char]$_})
```

```bash
# Linux ou macOS
export JWT_SECRET=$(openssl rand -hex 32)
```

### 3. Compilar e executar
```bash
mvn clean install
mvn spring-boot:run
```

### 4. Primeiro acesso

Em **desenvolvimento** (sem perfil ativo, ou com `dev`), a aplicação cria
sozinha um administrador de conveniência quando o banco está vazio:

- **Login:** `cadu`  ·  **Senha:** `123`

Em **produção** esse atalho não existe. Com o banco vazio, defina também:

```bash
export ADMIN_LOGIN=nome.do.responsavel
export ADMIN_SENHA='uma senha forte'
export ADMIN_NOME='Nome Completo'      # opcional
```

Sem elas a aplicação recusa subir — sem nenhum usuário ninguém autentica, e
como só um ADMIN cria usuário, o sistema ficaria trancado por fora. Depois que
o primeiro administrador existir, essas variáveis são ignoradas e os demais
logins saem da tela de Usuários.

---

## 🚢 Implantação

A aplicação lê toda a configuração de ambiente por variáveis, então o mesmo
`.jar` serve para qualquer forma de execução. Veja `.env.example` para a lista
completa.

| Variável | Obrigatória | Para quê |
|----------|-------------|----------|
| `JWT_SECRET` | sempre | Assina os tokens. Mínimo de 32 caracteres |
| `ADMIN_LOGIN` / `ADMIN_SENHA` | só com banco vazio fora de dev | Cria o primeiro administrador |
| `ADMIN_NOME` | não | Nome exibido do primeiro administrador |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | não | Padrão aponta para o Postgres do compose |
| `CORS_ORIGINS` | não | Endereços autorizados do frontend |

As migrations rodam **dentro da aplicação** na subida, então um banco vazio é
migrado sozinho — o container `flyway` do compose é conveniência de
desenvolvimento, não requisito de produção.

---

## 🛠️ Melhorias Futuras

- [x] Optimistic Locking (`@Version`) no Produto para prevenir race conditions
- [x] Sequence do PostgreSQL para geração atômica de códigos de OS
- [x] Cadastro de Empresas vinculado à OS (substitui o campo livre "cliente")
- [x] Geração de PDF da Ordem de Serviço para impressão
- [x] Cálculo de mão de obra por OS
- [x] Suporte a múltiplos perfis de usuário (ADMIN / OPERADOR) com gestão de usuários pela própria aplicação
- [ ] Índices adicionais no banco (status, tipo) — `ordem_servico_id` e `empresa_id` já indexados
- [ ] JOIN FETCH em todas as listagens para eliminar N+1
- [ ] Perfil de produção (`application-prod.properties`)
- [x] Módulo de corte a laser: registro de peças cortadas por empresa/OS com calculadora de valor proporcional à chapa

---

## 👨‍💻 Autor

Desenvolvido por **Luiz Otávio** — sistema construído para uso real em metalúrgica de pequeno/médio porte.
