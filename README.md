<div align="center">
  <h1>🏭 Estoque API — Metalúrgica</h1>
  <p>API RESTful completa para gestão inteligente de inventário e controle de fluxo de caixa, construída com <b>Java 21</b> e <b>Spring Boot 4</b>. Projeto focado na integridade transacional das movimentações físicas, segurança JWT e performance.</p>
</div>

---

## 🚀 Tecnologias

| Tecnologia | Finalidade | Descrição |
|-----------|-----------|-----------|
| **Java** | Core | Linguagem principal (Versão 21 LTS) |
| **Spring Boot** | Framework | Base da aplicação web e injeção de dependências |
| **Spring Security** | Segurança | Autenticação, autorização e filtros |
| **Spring Data JPA** | Persistência | ORM avançado e comunicação com BD |
| **PostgreSQL** | Banco de Dados | Banco de dados relacional robusto |
| **Flyway** | Migrations | Versionamento e controle do esquema do banco |
| **Java JWT (Auth0)** | Segurança | Geração e validação de tokens JWT |
| **Lombok** | Produtividade | Redução de código boilerplate |
| **JUnit 5 & Mockito** | Testes | Suíte de testes automatizados (unitários e integração) |

---

## 📐 Arquitetura

O projeto segue a **Arquitetura em Camadas (Layered Architecture)** com separação rigorosa de responsabilidades:

```text
src/main/java/com/metalurgica/estoque/
├── controller/            # Controladores REST (Exposição dos endpoints)
├── domain/
│   ├── entity/            # Entidades mapeadas do banco de dados
│   ├── enums/             # Enums de domínio (TipoMovimentacao, etc)
│   └── repository/        # Interfaces do Spring Data JPA
├── dto/
│   ├── request/           # Objetos de entrada de dados
│   └── response/          # Objetos de formatação de saída
├── exception/             # Tratamento global de erros (ControllerAdvice)
├── security/              # Filtros JWT e UserDetails
└── service/               # Lógica core e regras de negócio
```

---

## 🗄️ Diagrama de Entidades

```text
┌───────────────┐       ┌─────────────────┐
│    USUARIO    │       │     PRODUTO     │
│───────────────│       │─────────────────│
│ id (BIGINT)   │       │ id (BIGINT)     │
│ nome          │       │ nome            │
│ login         │       │ categoria       │
│ senha         │       │ qt_atual        │
│               │       │ qt_minima       │
└──────┬────────┘       │ un_medida       │
       │                │ valor_unit      │
       │ 1:N            │ criado_em       │
       │                └────────┬────────┘
       │                         │ 1:N
       └───────────┐   ┌─────────┘
                   │   │
            ┌──────┴───┴───────┐
            │   MOVIMENTACAO   │
            │──────────────────│
            │ id (BIGINT)      │
            │ tipo (ENUM)      │
            │ quantidade       │
            │ valor_unit       │
            │ data_hora        │
            │ observacao       │
            │ produto_id (FK)  │
            │ usuario_id (FK)  │
            └──────────────────┘
```

---

## 📋 Endpoints

### 🔓 Autenticação (`/api/auth`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| POST | `/login` | Realiza o login e retorna o token JWT | Público |

### 📊 Dashboard (`/api/dashboard`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/` | Retorna métricas totais (Investimentos, Gastos, Alertas) | Autenticado |

### 📦 Produtos (`/api/produtos`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=` | Lista produtos (paginado), opcionalmente filtrando por nome/categoria | Autenticado |
| GET | `/estoque-baixo` | Retorna todos os produtos abaixo da cota mínima | Autenticado |
| GET | `/{id}` | Retorna detalhes de um produto específico | Autenticado |
| POST | `/` | Cadastra um novo produto | Autenticado |
| PUT | `/{id}` | Atualiza dados cadastrais do produto | Autenticado |

### 🔄 Movimentações (`/api/movimentacoes`)
| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET | `/?busca=&tipo=` | Lista o histórico (Entradas/Saídas) com filtros compostos | Autenticado |
| POST | `/` | Registra uma nova movimentação | Autenticado |
| PUT | `/{id}` | Edição Inline de movimentação antiga | Autenticado |

---

## 🧠 Regras de Negócio e Integridade

A API possui mecanismos robustos de consistência matemática:

- **Impedimento de Estoque Negativo**: O sistema avalia o impacto de qualquer requisição. É impossível registrar ou alterar uma `SAIDA` se a ação for deixar o saldo do produto menor que zero.
- **Recálculo Transacional Automático**: Ao registrar uma movimentação, o estoque atual do produto alvo é subtraído ou adicionado no mesmo instante, garantindo a integridade.
- **Edição Inline Compensatória**: Se um usuário errar a digitação (Ex: registrou saída de 10, mas era 3), ao alterar a movimentação via `PUT`, o backend calcula a diferença (devolve 7) e ajusta o saldo do produto automaticamente.
- **Automatização de Saldo Inicial**: Ao cadastrar um produto novo com `quantidadeAtual > 0`, o sistema automaticamente forja uma movimentação histórica de `ENTRADA` para justificar a origem do material.

---

## 🔐 Autenticação e Segurança

- Todas as requisições (exceto Login) passam pelo `JwtAuthenticationFilter`.
- O Token deve ser enviado no cabeçalho: `Authorization: Bearer <seu_token>`
- Proteção testada automaticamente contra requisições anônimas (`403 Forbidden`).

---

## ⚙️ Como Executar

### Pré-requisitos
- Java 21+
- Maven 3.8+
- PostgreSQL rodando

### 1. Subir o banco de dados
Crie o banco de dados localmente:
```sql
CREATE DATABASE estoque_metalurgica;
```
As tabelas serão criadas automaticamente pelo Flyway.

### 2. Executar a aplicação
```bash
mvn clean install
mvn spring-boot:run
```

### 3. Primeiro Acesso
A aplicação inclui um *Seeder*. Ao rodar pela primeira vez com o banco vazio, o sistema cria o usuário master automaticamente:
- **Login:** `cadu`
- **Senha:** `123`

---

## 👨‍💻 Autor

Desenvolvido por **Luiz Otávio** como sistema principal para gestão de inventário industrial de metalúrgicas.
