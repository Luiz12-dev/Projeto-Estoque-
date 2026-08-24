# Módulo de Cortes — Design

**Data:** 24 de agosto de 2026
**Repositórios afetados:** `Projeto-Estoque-` (backend) e `Projeto-Estoque-FrontEnd` (frontend)
**Situação:** aprovado para implementação, com um risco de negócio em aberto (seção 11)

---

## 1. Problema

O módulo de corte a laser existe hoje, mas responde à pergunta errada e mora no lugar errado.

**Lugar errado:** registrar uma peça cortada só é possível de dentro da tela de detalhe de uma ordem de serviço. Não existe visão consolidada, e é impossível responder "tudo que já cortei para a empresa X".

**Pergunta errada:** o cálculo atual devolve `valor da chapa × área da peça ÷ área da chapa`, que é o **custo do material consumido**. Não é preço de venda. Faltam o tempo de máquina — principal fator de custo do corte a laser depois do material — e a margem. Se esse número for passado ao cliente, a oficina trabalha no prejuízo.

**Atrito de uso:** as dimensões e o valor da chapa são redigitados a cada corte, porque o cadastro de Produto não os armazena. Isso é retrabalho e cria uma segunda fonte de verdade que pode divergir do cadastro sem que nada acuse.

## 2. Objetivo

Criar um ambiente próprio de **Cortes** que permita:

1. **Orçar** — informar as medidas de uma ou mais peças e obter o preço a cobrar do cliente, com o orçamento ficando registrado no sistema.
2. **Registrar** — lançar cortes efetivamente realizados, debitando estoque e custando na OS (comportamento atual, mudando de lugar).
3. **Consultar** — ver as peças cortadas por empresa e por período.

## 3. Escopo

### Dentro

- Novos campos no cadastro de Produto para parametrizar a chapa
- Entidades `Orcamento` e `OrcamentoItem` com persistência
- Cálculo de preço incluindo material, comprimento de corte e margem
- Nova aba "Cortes" no menu, com seções de Orçamentos e Peças Cortadas
- Migração do formulário de registro de corte para a nova aba
- Endpoint de consulta de peças cortadas com filtro por empresa

### Fora

- **O débito de estoque no corte permanece exatamente como está.** Existe uma inconsistência conhecida (o custo rateia por área, o estoque debita uma chapa inteira por peça) cuja correção depende de decisão de negócio do cliente ainda não tomada. Este design não a resolve e não a agrava.
- Conversão de orçamento aprovado em ordem de serviço. É a evolução natural, mas fica para uma etapa seguinte.
- Multi-tenant, Kafka, Redis, camada de cache.
- Refatoração de Movimentação, OS, Empresa ou Usuário.

## 4. Mudanças no cadastro de Produto

Migration `V15__adicionar_parametros_corte_produto.sql` adiciona três colunas **opcionais** a `produto`:

| Coluna | Tipo | Significado |
|---|---|---|
| `largura_mm` | `DECIMAL(10,2)` | Largura da chapa |
| `comprimento_mm` | `DECIMAL(10,2)` | Comprimento da chapa |
| `preco_metro_corte` | `DECIMAL(19,2)` | Quanto se cobra por metro de corte nesta chapa |

O valor da chapa já existe em `valor_unitario`.

São opcionais porque só fazem sentido para chapa — parafuso e tinta não têm perímetro. A espessura **não** ganha campo próprio: cada espessura já é um Produto distinto no estoque, com preço e saldo próprios, então ela fica implícita em qual chapa foi escolhida.

O módulo de Cortes só oferece na seleção produtos que tenham `largura_mm`, `comprimento_mm`, `valor_unitario` e `preco_metro_corte` preenchidos. A tela de Produtos ganha os três campos, agrupados e rotulados como parâmetros de corte.

Consequência principal: no orçamento o usuário escolhe a chapa e digita **apenas as medidas da peça**.

## 5. Cálculo do preço

Todas as medidas em milímetros.

```
área da peça       = larguraPeca × comprimentoPeca
área da chapa      = larguraChapa × comprimentoChapa

custoMaterial      = valorChapa × (área da peça ÷ área da chapa)

perímetro          = 2 × (larguraPeca + comprimentoPeca)
corteFuros         = pi × diametroFuroMm × quantidadeFuros
corteExtraMm       = metrosExtras × 1000
comprimentoCorteMm = perímetro + corteFuros + corteExtraMm

custoCorte         = (comprimentoCorteMm ÷ 1000) × precoMetroCorte

subtotal           = custoMaterial + custoCorte
precoUnitario      = subtotal × (1 + margemPercentual ÷ 100)
precoTotal         = precoUnitario × quantidade
```

### Corte interno

O perímetro sozinho **não** distingue uma peça lisa de uma peça furada — uma chapa de 200×100 lisa e uma de 200×100 com doze furos têm o mesmo contorno externo, mas tempos de máquina muito diferentes. Por isso o item aceita dois campos opcionais, ambos zero por padrão:

- `quantidade_furos` e `diametro_furo_mm` — o comprimento de corte de um furo circular é `pi × diâmetro`
- `corte_extra_metros` — campo livre para recortes internos que não são furos redondos

Quem corta apenas retângulos não preenche nada além das medidas.

### Arredondamento

O arredondamento acontece **uma única vez, no final**, em 2 casas decimais com `RoundingMode.HALF_UP`. Arredondar cada parcela intermediária acumula erro. Os cálculos intermediários usam a precisão nativa de `BigDecimal`; a divisão de áreas usa escala de 10 casas antes da multiplicação.

### Validações

- A área da peça não pode exceder a área da chapa — `IllegalArgumentException`, mantendo a regra atual
- Quantidade, largura e comprimento devem ser positivos
- O produto escolhido precisa ter os quatro parâmetros de corte preenchidos (`largura_mm`, `comprimento_mm`, `valor_unitario`, `preco_metro_corte`), senão a requisição é rejeitada com mensagem explicando qual campo falta
- Margem aceita zero e não aceita negativo

### De onde vem a margem

A margem é informada **em cada orçamento** e gravada junto dele, não existindo entidade de configuração global. O formulário sugere um valor inicial fixo em código (30%), que o usuário sobrescreve à vontade. A decisão evita criar uma tabela de parâmetros e uma tela de configuração para um único número; se no futuro surgirem margens diferentes por cliente ou por tipo de serviço, aí sim vale a entidade própria.

## 6. Modelo de dados

Migration `V16__criar_tabelas_orcamento.sql`.

### `orcamento`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `BIGSERIAL` | |
| `codigo` | `VARCHAR(20)` | Único, sequencial `ORC-0001`, mesmo padrão da OS |
| `empresa_id` | `BIGINT` | FK obrigatória |
| `situacao` | `VARCHAR(20)` | `PENDENTE`, `APROVADO`, `RECUSADO` |
| `margem_percentual` | `DECIMAL(5,2)` | Margem aplicada a este orçamento |
| `valor_total` | `DECIMAL(19,2)` | Soma dos itens, recalculada pelo servidor a cada gravação |
| `observacao` | `TEXT` | |
| `usuario_id` | `BIGINT` | FK obrigatória |
| `criado_em` | `TIMESTAMP` | |
| `atualizado_em` | `TIMESTAMP` | |

A sequência de código segue o padrão já usado pela OS (`V7`): uma sequence do PostgreSQL consultada atomicamente, evitando dois `ORC-0007` simultâneos.

### `orcamento_item`

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `BIGSERIAL` | |
| `orcamento_id` | `BIGINT` | FK obrigatória, com índice |
| `nome` | `VARCHAR(150)` | Descrição ou desenho da peça |
| `produto_id` | `BIGINT` | FK obrigatória — a chapa |
| `largura_peca_mm` | `DECIMAL(10,2)` | |
| `comprimento_peca_mm` | `DECIMAL(10,2)` | |
| `quantidade` | `INTEGER` | |
| `quantidade_furos` | `INTEGER` | Padrão 0 |
| `diametro_furo_mm` | `DECIMAL(10,2)` | Padrão 0 |
| `corte_extra_metros` | `DECIMAL(10,3)` | Padrão 0 |
| `largura_chapa_mm` | `DECIMAL(10,2)` | **Cópia congelada** |
| `comprimento_chapa_mm` | `DECIMAL(10,2)` | **Cópia congelada** |
| `valor_chapa` | `DECIMAL(19,2)` | **Cópia congelada** |
| `preco_metro_corte` | `DECIMAL(19,2)` | **Cópia congelada** |
| `custo_material_unitario` | `DECIMAL(19,2)` | Calculado |
| `custo_corte_unitario` | `DECIMAL(19,2)` | Calculado |
| `preco_unitario` | `DECIMAL(19,2)` | Calculado, com margem |
| `preco_total` | `DECIMAL(19,2)` | `preco_unitario × quantidade` |

### Por que congelar os valores

Cada item guarda uma cópia das dimensões, do valor da chapa e do preço por metro **no momento em que o orçamento foi feito**. Se o preço da chapa for reajustado no mês seguinte, um orçamento antigo não pode mudar sozinho: ele é um compromisso já comunicado ao cliente e precisa continuar mostrando o número que foi passado. Sem o congelamento, uma consulta a um orçamento de três meses atrás devolveria um valor que nunca existiu.

## 7. Onde vive a fórmula

O cálculo é implementado **duas vezes**, deliberadamente:

- **Backend** — `CalculadoraCorte`, uma classe de domínio sem estado, é a **fonte autoritativa**. Toda gravação recalcula do zero a partir dos parâmetros; valores enviados pelo cliente nunca são confiados.
- **Frontend** — uma função pura em `core/`, usada apenas para o preview instantâneo enquanto o usuário digita.

A duplicação é intencional porque a tela é uma calculadora: o Leo a usa com o cliente ao telefone, e resposta instantânea vale mais aqui do que em uma caixa de busca. Um preview via rede introduziria latência no exato momento em que a fluidez importa, e deixaria a calculadora dependente da conexão.

O risco da duplicação é controlado em duas camadas:

1. **O servidor recalcula ao salvar.** Uma divergência entre as implementações pode momentaneamente exibir um número errado na tela, mas nunca grava dado errado.
2. **Tabela de casos compartilhada.** Um conjunto de casos de teste — entradas e saídas esperadas — é mantido em um único arquivo de referência e exercitado pelos testes dos dois lados. Se as implementações divergirem, o teste falha.

A tabela cobre obrigatoriamente: o caso base verificado (chapa 1000×2000 mm a R$ 500,00, peça 200×100 mm), peça com furos, margem zero, margem não trivial, e ao menos um caso de arredondamento de meio exato — ponto flutuante do JavaScript e `BigDecimal` divergem justamente aí, e é o cenário que motiva a tabela existir.

## 8. Diferença entre orçar e registrar

O registro de um corte realizado **continua calculando apenas o custo do material**, sem custo de corte e sem margem. Isso é deliberado:

| | Responde a | Valor usado |
|---|---|---|
| **Orçamento** | Quanto cobrar do cliente | Material + corte + margem |
| **Registro** | Quanto de material saiu do estoque | Somente material |

O registro alimenta uma `Movimentacao`, que é o livro-caixa de material. Tempo de máquina e margem não são estoque; incluí-los inflaria o valor do inventário e o custo de material da OS.

O `PecaCortadaService` passa a reaproveitar o método de custo de material da `CalculadoraCorte`, eliminando a fórmula duplicada dentro do backend, mas sem alterar o valor que produz nem a forma como debita estoque.

## 9. API

| Método | Rota | Acesso | Função |
|---|---|---|---|
| `POST` | `/api/orcamentos/simular` | autenticado | Calcula um orçamento completo sem persistir nada |
| `POST` | `/api/orcamentos` | autenticado | Cria o orçamento com seus itens |
| `GET` | `/api/orcamentos` | autenticado | Lista paginada, filtros de empresa, situação e período |
| `GET` | `/api/orcamentos/{id}` | autenticado | Detalhe com os itens |
| `PUT` | `/api/orcamentos/{id}` | autenticado | Atualiza situação e observação |
| `GET` | `/api/pecas-cortadas` | autenticado | Lista paginada com filtro de empresa e período |

`POST /api/orcamentos/simular` existe apesar do cálculo no frontend: ele permite validar o preço por outro canal e serve de contrato verificável entre as duas implementações.

`GET /api/pecas-cortadas` exige uma consulta nova em `PecaCortadaRepository`, com `JOIN FETCH` até `OrdemServico` e `Empresa` para evitar N+1, seguindo o padrão já usado nas listagens de OS.

Os DTOs seguem o padrão do projeto: requests em `dto/request` com Bean Validation, responses como `record` imutáveis em `dto/response` construídos por `fromEntity()`. As exceções de domínio são traduzidas no `GlobalExceptionHandler` já existente.

## 10. Telas

Novo item na barra lateral, **"Cortes"**, com ícone SVG no padrão Feather usado no resto do sistema. A rota `/cortes` é protegida pelo `authGuard`, como as demais.

### Seção Orçamentos

Formulário com empresa, margem percentual e uma lista de itens que pode crescer. Cada item tem descrição, seleção da chapa, largura, comprimento, quantidade e — recolhidos por padrão — os campos de corte interno. Ao lado de cada item aparece o valor unitário e o total, atualizados enquanto se digita. O rodapé mostra o total do orçamento.

Abaixo, a listagem de orçamentos com filtros de empresa, situação e período, e a troca de situação feita direto na linha, seguindo o padrão de edição inline já adotado no projeto.

### Seção Peças Cortadas

Histórico consolidado de cortes realizados, filtrável por empresa e período. Atende ao requisito de ver as peças cortadas de cada empresa.

O formulário de registro de corte **muda para cá**, com a OS escolhida em uma lista. A tela de detalhe da OS mantém a lista das peças daquela OS, mas apenas para leitura, para o custo do trabalho continuar visível ali.

Padrões obrigatórios: busca com debounce de 400 ms, filtros compostos enviados ao servidor como `HttpParams`, componentes standalone, Signals para estado, CSS puro com as variáveis de tema existentes.

## 11. Risco em aberto

**O modelo de cobrança não foi confirmado com o cliente final.**

Este design assume que o material é cobrado por **área**. Metalúrgicas frequentemente compram e vendem aço por **peso**. Se o Leo raciocina em quilos, todo valor exibido parecerá estranho para ele e o módulo nascerá desalinhado com a operação.

A estrutura absorve a troca sem reescrita — muda apenas como o custo do material é derivado, e a chapa passaria a ter densidade e espessura em vez de, ou além de, dimensões. Mas a mudança é barata agora e cara depois que houver migration aplicada e telas construídas.

**Ação:** confirmar com o Leo antes do início da implementação. O restante do design não depende dessa resposta.

## 12. Testes

### Backend

Testes da `CalculadoraCorte` isolada: custo de material, perímetro, corte por furos, corte extra livre, margem zero, margem não trivial, arredondamento de meio exato, e rejeição de peça maior que a chapa. Todos os casos da tabela compartilhada da seção 7 são exercitados aqui.

Testes de `OrcamentoService`: criação com múltiplos itens, geração do código sequencial, congelamento dos valores da chapa no item, soma do total, recusa de produto sem parâmetros de corte, transições de situação.

Testes de `PecaCortadaService`: confirmação de que o valor produzido **não mudou** após passar a usar a calculadora compartilhada — é um teste de regressão sobre comportamento existente.

Teste da consulta de peças cortadas por empresa.

### Frontend

Requer `npm install` no caminho novo antes de qualquer coisa; `vitest` e `jsdom` já constam em `devDependencies` e o alvo `test` já usa `@angular/build:unit-test`.

Testes da função pura de cálculo, exercitando a mesma tabela de casos do backend. Teste da montagem e remoção de itens da lista do orçamento.

### Critério de conclusão

A suíte do backend passa inteira, incluindo os 63 testes existentes. Nenhum teste existente é alterado para acomodar o novo código; se algum quebrar, é um achado a ser investigado antes de prosseguir.

## 13. Ordem de implementação

1. `V15` e os campos de corte no Produto, com a tela de Produtos atualizada
2. `CalculadoraCorte` e sua bateria de testes, incluindo a tabela compartilhada
3. `PecaCortadaService` passa a usar a calculadora, com teste de regressão provando que o valor não mudou
4. `V16`, entidades, repositórios, DTOs e `OrcamentoService`
5. `OrcamentoController` e a consulta de peças cortadas por empresa
6. Aba "Cortes" no frontend, seção de Orçamentos
7. Seção de Peças Cortadas e migração do formulário de registro
8. Detalhe da OS passa a somente leitura

Cada etapa termina com a suíte completa executada e o resultado apresentado.

---

## Ponto de parada — 24/08/2026

Suítes no momento da parada: **backend 92/92**, **frontend 19/19**, `ng build` limpo.

### Concluído

| Etapa | Situação |
|---|---|
| 1. Parâmetros de corte no Produto (migration V15, entidade, DTOs) | pronto |
| 2. `CalculadoraCorte` + tabela de casos compartilhada | pronto |
| 3. `PecaCortadaService` passa a usar a calculadora | pronto |
| 4. Orçamento persistido (migration V16, entidades, serviço) | pronto |
| 5. Controller de orçamento + consulta de peças por empresa | pronto |
| 6a. Base de cálculo no frontend + testes | pronto |
| 6b. Parâmetros de chapa na tela de Produtos | pronto |

O frontend ganhou `core/calculo/decimal.ts` (decimal exato sobre BigInt) e
`core/calculo/calculo-corte.ts` (espelho da `CalculadoraCorte`). A tabela
`core/testing/casos-corte.json` é byte a byte igual à de
`src/test/resources/casos-corte.json` no backend; os dois lados a executam.

Verificação executada: uma implementação ingênua em `float` com `Math.round`
foi rodada contra a mesma tabela e divergiu no caso "arredondamento de meio
exato", devolvendo R$ 1,00 onde o backend grava R$ 1,01. É a prova de que o
teste pega o erro que diz pegar, e não apenas acompanha a implementação.

Também neste ponto: a tela de Produtos passou a ter a seção recolhível
"Parâmetros de corte a laser" (largura, comprimento, preço por metro) e a
listagem marca com um selo as chapas prontas para orçar. Sem isso o Leo nunca
conseguiria preencher os dados que o orçamento exige, e a mensagem de erro do
backend ("Preencha os parâmetros de corte na tela de Produtos") apontaria para
uma tela que não tinha os campos.

### O que falta

7. **Aba Cortes** (`pages/cortes/`, rota `/cortes`, item na sidebar com SVG
   Feather). Duas seções: Orçamentos (lista com filtros de empresa e situação,
   formulário-calculadora com prévia instantânea, detalhe com o preço aberto em
   parcelas) e Peças Cortadas (histórico consolidado com filtro por empresa e
   período). O componente ainda não foi escrito — nada foi criado em
   `src/app/pages/cortes/`.
8. **Detalhe da OS vira somente leitura** quanto a cortes: o formulário de
   registro sai de lá e passa a viver na aba Cortes; a OS continua listando as
   peças já cortadas.

### Decisão ainda em aberto com o cliente

Confirmar com o Leo se ele orça por **área ocupada na chapa** (o que está
implementado) ou por **peso da peça**. Pergunta sugerida: "quando você orça um
corte, você pensa em quanto pesa a peça ou em quanto de chapa ela ocupa?"
