# Conclusão do módulo de Cortes e preparo para produção

**Data:** 26/08/2026
**Escopo aprovado:** aba Cortes no frontend + tarefas T2, T3 e T4 do plano pós-auditoria.

## Ponto de partida verificado

Backend do módulo de Cortes concluído e testado (92 testes). Base de cálculo do
frontend concluída e testada (19 testes). O que falta é a interface que consome
esse backend, e três pendências que impedem o sistema de rodar fora da máquina
do desenvolvedor.

## 1. A aba Cortes

### Estrutura de arquivos

```
shared/components/
  toast/toast.ts                       aviso de sucesso/erro reutilizável
  paginacao/paginacao.ts               controle de página reutilizável

pages/cortes/
  cortes.ts|html|css                   casca: título, abas, toast
  orcamentos/
    orcamentos.ts|html|css             lista, filtros, detalhe
    orcamento-form.ts|html|css         a calculadora
    orcamento-form.model.ts            funções puras
    orcamento-form.model.spec.ts       testes das funções puras
  pecas-cortadas/
    pecas-cortadas.ts|html|css         histórico + registro de corte
```

Quatro componentes em vez de um. Um único `cortes.ts` passaria de 800 linhas
entre TypeScript e template — grande demais para editar com segurança e grande
demais para localizar qualquer coisa depois.

`toast` e `paginacao` viram componentes compartilhados porque hoje o mesmo bloco
está copiado em seis páginas. As páginas existentes **não** são alteradas neste
lote; podem adotar os componentes de forma incremental depois. Extrair agora
evita que a sétima e a oitava cópia nasçam.

### Rota e navegação

Rota plana `/cortes`, carregada por `loadComponent()`, como todas as outras. A
aba ativa é refletida em query param (`/cortes?aba=pecas`) para permitir link
direto. Sem `router-outlet` aninhado: nenhuma página do projeto usa isso e não
compensa introduzir a máquina por duas abas.

Item novo na sidebar (e na navegação mobile) logo abaixo de Movimentações, com o
ícone de tesoura do Feather. O selo "Chapa" da tela de Produtos passa a usar o
mesmo ícone, seguindo a convenção do projeto de um desenho por conceito.

### Fluxo de dados

A casca carrega **empresas** e **chapas parametrizadas** uma vez e entrega aos
filhos por `input()`. Se cada filho buscasse por conta, seriam quatro requisições
para os mesmos dois conjuntos de dados. Cada filho é dono da própria listagem,
paginação e filtros.

Erros sobem por `output()` até a casca, que exibe **um** toast. Nenhum filho tem
toast próprio: dois avisos empilhados é defeito que só aparece em produção.

Componentes novos usam `ChangeDetectionStrategy.OnPush`. Com Signals isso é
seguro e reduz verificação desnecessária conforme a lista cresce.

### A calculadora de orçamento

Empresa e margem são escolhidas uma vez, valendo para o orçamento inteiro. Cada
peça informa: chapa, nome, largura, comprimento, quantidade e — em seção
recolhida — furos, diâmetro e recorte extra.

O preço aparece enquanto se digita, calculado localmente por `calcularCorte`,
que os testes da tabela compartilhada já travam contra o backend.

Três decisões de interface que existem para quem atende o cliente pelo telefone:

- Faltando dado, o painel de preço fica **vazio**, nunca em R$ 0,00. Zero parece
  preço válido e alguém acabaria falando esse número em voz alta.
- Peça maior que a chapa avisa **nomeando as medidas da chapa**, em vez de
  apenas recusar.
- Adicionar uma peça **mantém a chapa selecionada**: a peça seguinte quase
  sempre sai da mesma chapa.

Não existe botão de conferir cálculo no servidor. Verificação é
responsabilidade dos testes automatizados, e o `POST` já recalcula tudo no
servidor — o que a tela mostra depois de emitir são os valores gravados, não os
da prévia.

### Detalhe do orçamento e entrega ao cliente

O detalhe mostra o preço aberto em parcelas (material, corte, margem) por peça.

Uma **folha de estilo de impressão** transforma esse detalhe em documento: com
`Ctrl+P` sai uma página limpa com o cabeçalho da Metalúrgica Fantineli, o
código, a empresa, as peças e o total, e o próprio navegador salva em PDF.

Sem isso o orçamento não sai do sistema, e um orçamento que não chega ao cliente
é meio produto. Um PDF gerado no servidor entregaria pouco a mais por muito mais
trabalho; se um dia for necessário, o endpoint entra sem alterar esta tela.

### Registro de corte

Sai do detalhe da Ordem de Serviço e passa a viver na seção de Peças Cortadas.
Escolhe-se a chapa, a OS, o nome e as medidas da peça.

As medidas e o valor da chapa passam a vir do **cadastro do Produto**, em vez de
serem redigitados. Digitar 1200 no lugar de 1220 hoje produz um custo errado que
ninguém percebe.

Consequência aceita: o formulário passa a oferecer apenas chapas parametrizadas,
igual ao orçamento, com aviso apontando para a tela de Produtos. É a mesma regra
nos dois lugares.

**A entidade `PecaCortada`, as dimensões gravadas e o débito de estoque não são
alterados.** Muda apenas a origem dos valores que o formulário preenche.

### Detalhe da OS

Perde o formulário de registro de corte e mantém a listagem das peças já
cortadas, em somente leitura, com link para a aba Cortes.

### Testes

A conta já está coberta pelos 19 testes da tabela compartilhada. Além deles,
`orcamento-form.model.ts` concentra a montagem do request e a regra de "esta
peça pode ser adicionada" como funções puras, com testes próprios. Sem TestBed:
extrair em vez de montar componente é a escolha que já se provou correta na T5.

## 2. T2 — Postgres real nos testes

Hoje os testes rodam em H2 com o esquema gerado pelo Hibernate e o Flyway
desligado, enquanto a produção recebe o esquema das migrations. São duas fontes
distintas e nada verifica que concordam.

Separação por tag:

- Os testes Mockito continuam em `mvn test`, sem Docker.
- `EstoqueApplicationTests` e `SecurityIntegrationTest` recebem
  `@Tag("integracao")` e rodam contra **Postgres 16 em container**, com Flyway
  ligado, `ddl-auto=validate` e as migrations aplicadas na ordem real.
- `mvn verify` roda tudo; `mvn test` permanece como hoje.

Divergência já conhecida: a `V11` cria `usuario.role` sem `NOT NULL` enquanto a
entidade declara `@Column(nullable = false)`. O `validate` do Hibernate **não**
detecta nulidade, apenas existência e tipo — a comparação precisa ser manual.
Correção por migration nova; migration existente nunca é editada.

O que quebrar é apresentado ao Luiz antes de qualquer correção.

## 3. T3 — Chave JWT

O `application.properties` afirma em comentário que a aplicação falha ao subir
sem `JWT_SECRET`. Ela não falha: há um valor padrão logo abaixo, versionado no
Git. Quem clonar o repositório consegue forjar token de qualquer usuário.

O padrão é removido, a subida falha com mensagem dizendo qual variável falta e
como gerar um valor, e o comentário passa a descrever o comportamento real. Um
comentário que mente é pior que a ausência dele: alguém implanta confiando.

## 4. T4 — Primeiro administrador em produção

O seed `cadu/123` roda apenas nos perfis `dev` e `default`. Em produção não
existe caminho: o sistema sobe, ninguém consegue autenticar, e como
`/api/usuarios` exige ADMIN não há como criar o primeiro usuário.

Bootstrap por variáveis de ambiente, senha em BCrypt, executado **somente
quando a tabela de usuários está vazia**:

- Tabela vazia e variáveis presentes: cria o administrador.
- Tabela vazia e variáveis ausentes: falha na subida dizendo o que configurar.
- Tabela com usuários: não faz nada e não reclama — é o restart normal.

## Ordem de execução

1. Aba Cortes
2. Registro de corte migra; detalhe da OS vira somente leitura
3. T3 — chave JWT
4. T4 — bootstrap de administrador
5. T2 — Testcontainers

A aba vem primeiro porque destrava o módulo para o cliente. T2 vem por último
porque depende do Docker e pode revelar achados que merecem discussão própria.

Ao fim de cada etapa: suíte completa dos dois lados, saída apresentada, commit e
push.

## Fora deste escopo, registrado para não se perder

- **`/api/orcamentos` não é restrito a ADMIN.** Cai em
  `anyRequest().authenticated()`, então qualquer operador enxerga a margem de
  lucro. Comparado a `/api/dashboard/**` e `/api/usuarios/**`, que são
  ADMIN-only, isso parece descuido e não decisão. É o item de maior prioridade
  do backlog.
- PDF de orçamento gerado no servidor.
- Métricas de orçamento no dashboard.

## Bloqueado por decisão do cliente

- **Débito de estoque no corte:** cortar duas peças pequenas dá baixa de duas
  chapas inteiras. Opções mapeadas dependem de como o Leo compra e guarda
  material.
- **Base de precificação:** área ocupada na chapa (implementado) ou peso da
  peça. Pergunta sugerida: "quando você orça um corte, você pensa em quanto pesa
  a peça ou em quanto de chapa ela ocupa?"
- **Aproveitamento de sobra:** o rateio ignora retalho reaproveitado.
