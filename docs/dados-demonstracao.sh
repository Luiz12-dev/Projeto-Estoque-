#!/usr/bin/env bash
# Popula um banco vazio com dados plausíveis de uma metalúrgica, para que as
# telas tenham o que mostrar numa apresentação. Sistema vazio demonstra mal:
# a aba Cortes abre dizendo "nenhuma chapa pronta para orçar" e a calculadora,
# que é a melhor parte, não tem o que calcular.
# Tudo aqui é EXEMPLO — nomes de empresa e preços são fictícios e devem ser
# substituídos pelos números reais do Leo antes de qualquer decisão.
#
# Uso: bash dados-demonstracao.sh [url-da-api]
set -e

API="${1:-http://localhost:8081/api}"

token() {
  curl -s --max-time 10 -X POST "$API/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"login":"cadu","senha":"123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])"
}

TOKEN=$(token)
[ -z "$TOKEN" ] && { echo "Nao consegui autenticar em $API"; exit 1; }
H="Authorization: Bearer $TOKEN"

criar() { # $1 = rota, $2 = json, $3 = campo para exibir
  curl -s -X POST "$API/$1" -H "$H" -H "Content-Type: application/json" -d "$2" \
  | python -c "
import sys,json
try:
    d=json.load(sys.stdin)
    print('  ok  ', d.get('$3') or d.get('nome') or d.get('codigo'))
except Exception:
    print('  falhou')
"
}

echo "Empresas (clientes)"
criar empresas '{"nome":"Serralheria Silva","telefone":"44 99811-2200","cnpj":"12.345.678/0001-90"}' nome
criar empresas '{"nome":"Construtora Horizonte","telefone":"44 99755-1180"}' nome
criar empresas '{"nome":"Agropecuaria Sao Jose","telefone":"44 99622-4471"}' nome

echo
echo "Chapas com parametros de corte"
# Formatos comuns no mercado brasileiro. Os valores sao ilustrativos.
criar produtos '{"nome":"Chapa Aco Carbono 1,2mm","categoria":"Chapas","quantidadeAtual":18,"quantidadeMinima":4,"unidadeMedida":"UN","valorUnitario":286.00,"larguraMm":1200,"comprimentoMm":3000,"precoMetroCorte":7.50}' nome
criar produtos '{"nome":"Chapa Aco Carbono 3mm","categoria":"Chapas","quantidadeAtual":12,"quantidadeMinima":3,"unidadeMedida":"UN","valorUnitario":695.00,"larguraMm":1200,"comprimentoMm":3000,"precoMetroCorte":12.00}' nome
criar produtos '{"nome":"Chapa Aco Carbono 6mm","categoria":"Chapas","quantidadeAtual":6,"quantidadeMinima":2,"unidadeMedida":"UN","valorUnitario":1380.00,"larguraMm":1200,"comprimentoMm":3000,"precoMetroCorte":22.00}' nome
criar produtos '{"nome":"Chapa Inox 304 1,5mm","categoria":"Chapas","quantidadeAtual":4,"quantidadeMinima":2,"unidadeMedida":"UN","valorUnitario":1120.00,"larguraMm":1000,"comprimentoMm":2000,"precoMetroCorte":18.00}' nome
criar produtos '{"nome":"Chapa Galvanizada 2mm","categoria":"Chapas","quantidadeAtual":9,"quantidadeMinima":3,"unidadeMedida":"UN","valorUnitario":512.00,"larguraMm":1200,"comprimentoMm":3000,"precoMetroCorte":10.00}' nome

echo
echo "Outros materiais, para o estoque nao ser so chapa"
criar produtos '{"nome":"Tubo Redondo 1.1/2 x 1,5mm","categoria":"Tubos","quantidadeAtual":40,"quantidadeMinima":10,"unidadeMedida":"M","valorUnitario":23.40}' nome
criar produtos '{"nome":"Barra Chata 2 x 1/4","categoria":"Barras","quantidadeAtual":25,"quantidadeMinima":8,"unidadeMedida":"M","valorUnitario":31.90}' nome
criar produtos '{"nome":"Eletrodo 6013 3,25mm","categoria":"Consumiveis","quantidadeAtual":3,"quantidadeMinima":5,"unidadeMedida":"KG","valorUnitario":42.00}' nome
criar produtos '{"nome":"Disco de Corte 7","categoria":"Consumiveis","quantidadeAtual":2,"quantidadeMinima":10,"unidadeMedida":"UN","valorUnitario":9.80}' nome

echo
echo "Ordens de servico"
criar ordens-servico '{"descricao":"Portao basculante 3,5m com pintura","empresaId":1,"prioridade":"ALTA"}' codigo
criar ordens-servico '{"descricao":"Guarda-corpo escada externa","empresaId":2,"prioridade":"MEDIA"}' codigo
criar ordens-servico '{"descricao":"Comedouro bovino 2m em inox","empresaId":3,"prioridade":"BAIXA"}' codigo

echo
echo "Pronto. Dois materiais ficam abaixo do minimo de proposito,"
echo "para a tela de Estoque Baixo ter o que mostrar."
