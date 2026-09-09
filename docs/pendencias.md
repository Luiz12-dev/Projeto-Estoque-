# Pendências

Levantamento de 30/08/2026. Cada item traz o que está errado, como reproduzir e
o que fazer — para que uma sessão nova consiga pegar qualquer um deles sem
precisar redescobrir o contexto.

Estado no momento do levantamento: backend com 125 testes unitários e 12 de
integração, frontend com 56. Ambos os repositórios com árvore limpa.

---

## 1. BUG — Devolver material faz o custo da OS subir

**Prioridade: alta.** É o único número de lucro que o sistema oferece hoje, e
ele está errado.

`MovimentacaoRepository.somarCustosPorOsIds` soma `quantidade * valorUnitario`
de **todas** as movimentações da OS, sem filtrar por tipo:

```sql
SELECT ... COALESCE(SUM(m.quantidade * m.valorUnitario), 0)
FROM Movimentacao m
WHERE m.ordemServico.id IN :osIds
GROUP BY m.ordemServico.id
```

Uma ENTRADA vinculada à OS — material que sobrou e voltou para o estoque — é
**somada** ao custo em vez de subtraída. E não é caminho teórico: a tela de
detalhe da OS oferece "↑ Entrada" no seletor de tipo.

### Reproduzido em 30/08/2026 contra a API

```
custo inicial da OS:        R$   0,00
consumiu 10 m a R$ 23,40:   R$ 234,00   correto
devolveu 4 m:               R$ 327,60   deveria ser R$ 140,40
```

A OS ficou com custo de 14 metros para um serviço que consumiu 6.

### Correção

Na query, subtrair ENTRADA em vez de somar:

```sql
SUM(CASE WHEN m.tipo = 'SAIDA' THEN m.quantidade * m.valorUnitario
         ELSE -(m.quantidade * m.valorUnitario) END)
```

Antes de codificar, decidir com o Luiz: uma devolução parcial **subtrai** do
custo (tratamento acima) ou o custo deve considerar apenas SAIDA e ignorar
ENTRADA? Subtrair é o correto quando a entrada é devolução de sobra; ignorar
seria correto se ENTRADA em OS significasse outra coisa. Hoje só existe um
significado plausível, então subtrair.

### Verificação exigida

Teste que reproduza a sequência acima — saída de 10, entrada de 4, custo
esperado de 140,40 — e que **falhe** com a query atual. Depois rodar o cenário
contra a API real, como foi feito no levantamento.

---

## 2. Armadilha — `fromEntitySimple` devolve custo sem material

`OrdemServicoResponse.fromEntitySimple(os)` chama `fromEntity(os, ZERO, 0)`, ou
seja, devolve `custoTotal` contando **apenas a mão de obra**, com material
zerado, sem nenhum aviso.

Não é bug hoje porque nenhuma tela usa essa versão para exibir custo. É bug
esperando alguém chamar o método errado.

**Correção sugerida:** renomear para algo que denuncie a limitação
(`fromEntitySemCustos`) e não preencher `custoTotal` com um número parcial —
devolver `null` deixa a ausência explícita em vez de disfarçá-la de zero.

---

## 2.1 RESOLVIDO — Erros do cliente viravam 500

Três casos caíam no tratamento genérico do `GlobalExceptionHandler` e viravam
erro interno: JSON malformado no corpo, parâmetro de URL com tipo errado (por
exemplo `?situacao=INEXISTENTE`) e rota inexistente.

Corrigido em 08/09/2026, junto com os testes de contrato que os encontraram.

---

## 3. RESOLVIDO — Testes de contrato nos controllers

Os 9 controllers passaram a ter teste: **83 testes** cobrindo formato do JSON
campo a campo, paginação, validação de entrada, tradução de exceção de domínio
em status HTTP e as regras de perfil.

O que eles protegem, e nenhum outro teste protegia: os modelos do frontend em
`core/models/*.model.ts` são mantidos em sincronia com 30 DTOs **à mão**.
Renomear um campo quebraria a tela em silêncio — testes de serviço não notam,
porque trabalham com objetos Java e não com o JSON da rede.

A base é `TesteDeControlador`, com `@WebMvcTest` e serviços mockados. Não toca
banco nem exige Docker.

**Armadilha registrada:** o `MockMvc` precisa ser montado à mão com
`springSecurity()`. No slice do Boot 4 o suporte de segurança dos testes não
vem ligado sozinho, e sem ele o `@WithMockUser` não chega ao filtro: toda rota
responde 403 e os testes "provam" proteção sem exercitar nada.

---

## 4. Três perguntas de negócio para o Leo

O modelo de preço do corte é internamente coerente, mas parte de premissas que
a oficina real não cumpre. **Nenhuma destas pode ser respondida por chute** — o
resultado seria um número errado com aparência de precisão.

### 4.1 Aproveitamento da chapa

O sistema cobra e dá baixa pela fração de área que a peça ocupa, supondo que o
resto da chapa continua integralmente aproveitável. Na prática, cortar uma peça
pequena de uma chapa nova queima a chapa: o que sobra é retalho, útil só se
aparecer outro serviço com a medida certa.

Cobrando 0,55% de uma chapa de R$ 695, o Leo recebe **R$ 3,86** por um trabalho
que tirou uma chapa do galpão.

> *Quando você corta uma peça pequena de uma chapa nova, cobra a fração ou a
> chapa toda?*

Caminhos possíveis conforme a resposta: fator de aproveitamento (cobrar sobre
75–80% da área útil), ou pedido pequeno paga a chapa inteira.

### 4.2 Valor mínimo de serviço

Não existe nada de mínimo, setup ou taxa fixa no código. Uma peça de 50×50 mm
com 4 furos sairia por cerca de R$ 3,00 — nenhum metalúrgico aceita o serviço
por esse preço, porque programar o desenho, buscar a chapa e posicionar na
máquina custa o mesmo independentemente do tamanho.

> *Qual o valor mínimo de um serviço de corte, por menor que seja a peça?*

### 4.3 Custo da perfuração

`calcularComprimentoCorteMetros` soma a **circunferência** de cada furo
(`π × diâmetro × quantidade`). Está certo para a distância percorrida, mas
ignora a perfuração: antes de recortar cada furo o laser fura a chapa parado, e
isso leva o mesmo tempo num furo de 5 mm e num de 50 mm.

Uma peça com 200 furos de 4 mm tem só 2,5 m de circunferência, mas ocupa a
máquina muito mais que isso sugere.

> *Quanto custa cada furo, além do contorno?*

### 4.4 Base de precificação (pergunta antiga, ainda aberta)

> *Quando você orça um corte, pensa em quanto pesa a peça ou em quanto de chapa
> ela ocupa?*

Hoje é por área.

---

## 5. O que falta para o sistema servir a um dono de metalúrgica

Ordenado pelo que o Leo sentiria falta primeiro.

### 5.1 Não existe controle de pagamento

O maior buraco funcional, e não é técnico. Uma OS é concluída e acabou — não
existe "pago", "a receber", "vencido". Cobrar é trabalho diário numa oficina; um
sistema que não resolve isso deixa o caderno em cima da mesa.

### 5.2 O dashboard não fecha o mês

Mostra `totalProdutos`, `produtosAbaixoMinimo`, `movimentacoesMes`,
`totalInvestido`, `totalSaidas`, `valorTotalEstoque` e contagens de OS. Não
mostra **resultado**: faturamento, custo e lucro do mês. É o primeiro número que
um dono procura. Os dados já existem — falta somar e exibir. **Depende do item 1
estar corrigido**, senão o número sai errado.

### 5.3 Orçamento aprovado não vira OS

Emite-se `ORC-0001` por R$ 1.136, o cliente aprova, e alguém redigita tudo numa
OS. Trabalho dobrado, e impossível responder "orcei mil e duzentos, quanto
custou de verdade?".

A decisão de não converter foi tomada quando o módulo nem existia. Vale
reabrir.

### 5.4 Não existe fornecedor

`Empresa` são só clientes. Uma ENTRADA de material não registra de quem foi
comprado, então "de qual fornecedor a chapa 3mm sai mais barata?" não tem
resposta.

### 5.5 Não existe prazo de entrega

A OS tem prioridade mas não tem data prometida. "Falei que sai sexta" não está
no sistema, e prazo é o que gera reclamação de cliente.

### 5.6 Orçamento não tem validade

`Orcamento` não tem campo de validade. Aço muda de preço; um orçamento de 60
dias atrás pode ser cobrado do Leo pelo cliente.

### 5.7 Margem única sobre material e corte

Oficinas costumam repassar material quase a custo e concentrar lucro no tempo de
máquina. A margem única é mais simples, mas se o Leo pensa em duas, o número
sai errado. Confirmar com ele.

### 5.8 Aproveitamento de sobra (retalho)

Não há controle de retalho. Foi mapeado como a opção mais correta para o débito
de estoque e descartada por custo: exigiria entidade nova, tela para escolher de
qual chapa sai a peça, e identificação física das chapas no galpão.

---

## 6. Infraestrutura e processo

### 6.1 Não há CI

128 testes que só rodam quando alguém digita o comando. Um workflow que rode
`mvn verify` e `ng test` a cada push fecha isso.

### 6.2 O frontend é quase todo não testado

9 das 10 páginas e todos os 9 serviços sem teste. Os 56 testes cobrem o cálculo
de corte e as regras do formulário de orçamento.

### 6.3 Implantação não está definida

O sistema roda apenas na máquina do desenvolvedor, com dois terminais. Não há
Dockerfile da aplicação; o `docker-compose` sobe só Postgres e Flyway.

Decisão em aberto: container, `.jar` num servidor da oficina, ou continuar
assim. As variáveis de ambiente já cobrem os três cenários (ver
`.env.example`), então nada do que existe precisa mudar quando a escolha for
feita.

**Lembrete que já custou tempo:** `CORS_ORIGINS` precisa conter o endereço real
do frontend. Sem isso o navegador leva 403 no login enquanto `curl` funciona,
porque `curl` não envia o cabeçalho `Origin`.

---

## 7. Acabamento visual — "parece tudo muito largado"

Levantado pelo Luiz em 09/09/2026, olhando a tela de detalhe da OS:

> "as coisas estão muito soltas dentro do projeto, e esse é o principal
> problema, parece tudo muito largado, sem espaçamento detalhado... textos
> soltos na tela... quero mais capricho nesse visual e no de todos"

A queixa é geral, não daquela tela. O que segue são as causas concretas
encontradas no código a partir do exemplo que ele mostrou.

### 7.1 Estado vazio degradado nas telas de Ordem de Serviço

`.empty-state` está definido no `styles.css` com ícone, respiro de 60px e
tipografia própria. Duas páginas o **redefinem localmente**, mais pobre:

```
app/pages/ordem-servico-detalhe/ordem-servico-detalhe.css:281
app/pages/ordens-servico/ordens-servico.css:199
    .loading-state, .empty-state { text-align: center; padding: 40px; ... }
```

Como estilo de componente ganha por especificidade, a versão fraca vence. E o
markup nem tenta usar o padrão — é uma `div` só com texto:

```html
<div class="empty-state">Nenhuma movimentação vinculada a esta OS.</div>
<div class="empty-state">Nenhuma peça cortada registrada nesta OS.</div>
```

Enquanto Produtos, Empresas e Orçamentos usam a versão completa, com ícone SVG
dentro de `<div class="icon">` e o texto em `<p>`.

**É a origem literal do "texto solto na tela"**: duas frases centralizadas no
vazio, sem cartão, sem borda e sem ícone.

**Correção:** apagar as duas redefinições locais e usar a estrutura completa do
markup, como nas outras telas.

### 7.2 Cartão órfão quebrando a grade

A tela de detalhe da OS mostra quatro cartões numa linha (Custo Total, Mão de
Obra, Movimentações, Abertura) e um quinto — Conclusão — **sozinho na linha de
baixo**. Numa OS sem data de conclusão ele exibe apenas um travessão, ocupando
uma faixa inteira para não dizer nada.

**Correção:** ou a grade acomoda cinco, ou Conclusão sai de cartão e vira uma
linha junto de Abertura.

### 7.3 Faixas de largura total empilhadas

`Observação:` e `Status:` aparecem como duas barras consecutivas de largura
total, com aparência de sobra de layout. São informações de natureza diferente
(uma é texto livre, outra é um controle) tratadas com o mesmo peso visual.

### 7.4 Seções sem contenção

`Materiais Utilizados` e `Peças Cortadas — Corte a Laser` são apenas títulos
soltos seguidos do conteúdo, sem cartão em volta, enquanto o resto do sistema
agrupa conteúdo em `.card`.

### 7.5 Cabeçalho sem hierarquia

`← Voltar`, os selos (`OS-0002`, `EM ANDAMENTO`, `BAIXA`), o título `Esteira` e
o seletor de empresa ficam empilhados sem agrupamento nem alinhamento comum.

### 7.6 Auditoria das dez telas

Os itens acima vieram de **uma** tela. A cobrança foi sobre todas. Antes de
considerar o assunto encerrado, passar pelas dez páginas procurando o mesmo
padrão de defeito: classe compartilhada redefinida localmente, elemento órfão
em grade, texto sem contenção, faixas empilhadas.

**Atalho de diagnóstico** — antes de inventar estilo novo, procurar se a classe
foi redefinida na página:

```bash
grep -rn "^\.<classe>" --include=*.css src/
```

Foi assim que a duplicação de `.search-box` (seis cópias) e de `.numero` /
`.codigo` (duas, já divergentes) apareceu na consolidação de 09/09.

---

## 8. Categorias de produto — a listagem é uma fila só

Levantado junto com o item 7:

> "podemos criar dentro de produtos, categorias para não ficar tudo enfileirado
> [...] para poder ter uma separação dos discos de corte que ela teve que
> comprar ou no caso, uma separação de cada item e as diversidades que ele tem
> de cada item"

**Como está hoje:** `Produto.categoria` é um `String` livre
(`domain/entity/Produto.java:29`). A tela de Produtos mostra a categoria como
**mais uma coluna** da tabela — não agrupa, não filtra por ela, não hierarquiza.
Com trinta itens vira uma fila única onde chapa, disco de corte e tubo se
misturam.

**O que o Luiz descreveu tem dois níveis:**

1. **Agrupar a listagem por categoria** — chapas juntas, discos juntos, tubos
   juntos. Resolve a maior parte da queixa e não exige mudança de schema:
   `categoria` já existe e já é preenchida.
2. **Variações de um mesmo item** ("as diversidades que ele tem de cada item")
   — disco de corte de 4½", de 7", de 12" como variações do mesmo produto, em
   vez de três produtos sem relação. Isso **exige decisão de modelagem** e
   provavelmente entidade nova.

**Recomendação:** fazer o nível 1 primeiro e mostrar ao Luiz. É barato, resolve
a dor imediata, e o nível 2 pode nem ser necessário depois que a listagem
estiver agrupada.

**Antes do nível 2, decidir com ele:** variação é produto separado com estoque
próprio (um disco de 7" acaba independente do de 4½") ou é atributo de um
produto único? O estoque é por variação, quase certamente — o que empurra para
manter produtos separados e apenas agrupá-los melhor na tela.


---

# Checklist — o que está aberto

Atualizada em 09/09/2026, depois da instalação de ensaio. Marque aqui ao
concluir; o detalhe de cada item está na seção indicada.

## Não dependem de ninguém — dá para fazer a qualquer momento

- [ ] **Aplicativo executável** — servidor como tarefa com `javaw` (sem janela
      preta) + atalho abrindo em janela limpa. Combinado com o Luiz, ainda não
      iniciado. Hoje o sistema morre se alguém fechar o terminal.
- [ ] **Bug do custo da OS** (seção 1) — devolver material aumenta o custo.
      A decisão é do Luiz, não do Leo: devolução subtrai. Único número de
      lucro do sistema, e está errado.
- [ ] **Testes de integração de fluxo** (seção 6.2) — os 15 existentes são 14
      de segurança e 1 de subida. Nenhum percorre o negócio contra banco real.
      Pegariam o bug acima automaticamente.
- [ ] **CI** (seção 6.1) — nenhum dos dois repositórios tem `.github/workflows`.
      `mvn verify` + `ng test` a cada push.
- [ ] **Acabamento visual** (seção 7) — feitas as telas de OS (7.1 a 7.5) e
      as quatro abas que ganharam blocos. Falta a auditoria de 7.6 nas
      demais: Dashboard, Empresas, Usuários, Estoque Baixo.
- [x] **Blocos por categoria/empresa em todas as abas** (seção 8) — feito em
      09/09, indo além do nível 1: categoria virou entidade, com criar,
      renomear e apagar pela tela.
- [ ] **`fromEntitySimple`** (seção 2) — devolve custo com material zerado sem
      avisar. Dez minutos.
- [ ] **Frontend sem testes** (seção 6.2) — 9 das 10 páginas e os 9 serviços.
- [ ] **`restaurar.bat`** — o procedimento de restauração foi executado e
      funciona, mas não está em script nem no manual. Adiado a pedido do Luiz.

## Dependem de conversa com o Leo

- [ ] **Preço do corte** (seção 4) — aproveitamento de chapa, valor mínimo,
      custo do furo, área ou peso. **Ele começa a cortar só na segunda**, então
      não tem como responder ainda: as regras dele não existem. O caminho é
      registrar as primeiras semanas e extrair os números de lá.
- [ ] **Barras e tubos** — o sistema hoje só orça o que tem largura E
      comprimento E preço por metro de corte (`Produto.chapaParametrizada`).
      Barra não entra na calculadora. Confirmar antes se a oficina já vende
      serviço de barra hoje ou se é planejamento junto com o laser.
- [ ] **Variações de produto** (seção 8, nível 2) — agora que categoria é
      entidade, decidir com o Leo se variação tem estoque próprio.
- [ ] Controle de pagamento, resultado do mês, orçamento virando OS,
      fornecedor, prazo de entrega, validade do orçamento, margem separada
      (seção 5).

## Operação — antes de encerrar o acesso à máquina do Leo

- [ ] **Reserva de IP no roteador** — o endereço muda sozinho e todo mundo diz
      que "o sistema parou". É a falha mais provável das próximas semanas.
- [ ] **Plano de energia: nunca suspender** — o PC virou o servidor do
      escritório.
- [ ] **Segundo usuário ADMIN** — não existe tela de recuperação de senha; o
      bootstrap só age com a tabela de usuários vazia
      (`BootstrapAdministrador:75`). Sem um segundo admin, senha esquecida
      significa mexer no banco.
- [ ] **Guardar as credenciais** do Leo em lugar seguro.

## Já resolvido nesta rodada (09/09)

- [x] Telas de OS: estado vazio, cartão órfão, faixas empilhadas, cabeçalho
- [x] Categoria virou entidade (V18), com criar/renomear/apagar na tela
- [x] Blocos em Produtos, Movimentações e nas duas abas de Cortes
- [x] Acento no config.properties corrompia nome e senha do administrador

- [x] Locale pt-BR, dinheiro em formato brasileiro nas nove telas
- [x] `.search-box` e as classes de tabela consolidadas no `styles.css`
- [x] Instalador não compilava no PowerShell 5.1 (faltava BOM)
- [x] `wmic` removido do Windows 11 quebrava o nome do arquivo de backup
- [x] Senha com `&` chegava truncada no `pg_dump`
- [x] `config.properties` saía com todos os campos em branco
- [x] Instalador aceitava qualquer Java, não só o 21
- [x] Backup agendado, com execução de teste durante a instalação
- [x] Backup e restauração verificados de ponta a ponta contra PostgreSQL real
---

## Como usar este documento

Os itens 1 e 2 são bugs e não dependem de ninguém: podem ser feitos a qualquer
momento. O item 3 reduz o risco de todo o resto. O item 4 depende de conversa
com o Leo. O item 5 depende do 4 estar respondido para não construir sobre
premissa errada.

---

## Armadilhas de verificação (para não perder tempo de novo)

Anotadas depois de tirarem conclusão errada durante os testes.

**`DROP DATABASE` falha em silêncio com conexão aberta.** O PostgreSQL recusa
derrubar um banco enquanto houver conexão ativa, e se a saída do comando for
descartada o erro passa despercebido. O teste seguinte roda contra dados
antigos e "prova" algo falso. Use `DROP DATABASE ... WITH (FORCE)` e sempre
confira o retorno.

**`curl` não envia o cabeçalho `Origin`.** Um endpoint pode responder 200 no
`curl` e 403 no navegador por causa de CORS. Ao testar login, envie o `Origin`
explicitamente — e envie o mesmo endereço do destino, senão o teste simula um
cenário que não acontece.

**Injetar token direto no `localStorage` não exercita o login.** Serve para
fotografar telas, não para provar que a autenticação funciona.
