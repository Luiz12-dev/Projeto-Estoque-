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

## 2.1 Rota de API inexistente devolve 500 em vez de 404

**Prioridade: baixa.** Não afeta o uso, atrapalha o diagnóstico.

Com token válido, `GET /api/nao-existe` responde **500**. Sem token responde
403, o que está certo (não revelar quais rotas existem). Com token deveria ser
404.

Provável causa: a exceção de handler não encontrado cai no tratamento genérico
do `GlobalExceptionHandler` e vira erro interno. Um erro de digitação numa
chamada passa a parecer defeito do servidor.

Encontrado em 08/09/2026, ao conferir o pacote de instalação.

---

## 3. Nenhum controller tem teste

Os 9 controllers não têm um único teste. As regras de negócio estão bem
cobertas, mas **o contrato HTTP não é verificado**: código de status, validação
de entrada, formato do JSON.

Isso importa mais aqui do que em outros projetos porque os modelos do frontend
(`core/models/*.model.ts`) são mantidos em sincronia **à mão** com 30 DTOs.
Renomear um campo no backend quebraria a tela em silêncio.

O bug do item 1 só apareceu porque alguém exercitou a API manualmente. É
exatamente o tipo de defeito que um teste de contrato pegaria.

**Sugestão de ordem:** começar pelos controllers que a aba Cortes consome
(`OrcamentoController`, `PecaCortadaController`, `ProdutoController`), que são
os mais novos e os menos rodados em produção.

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

## Como usar este documento

Os itens 1 e 2 são bugs e não dependem de ninguém: podem ser feitos a qualquer
momento. O item 3 reduz o risco de todo o resto. O item 4 depende de conversa
com o Leo. O item 5 depende do 4 estar respondido para não construir sobre
premissa errada.
