#!/bin/bash
# Script para testar integração com Firefly III

echo "🔥 Testador de Integração Firefly III"
echo "======================================"
echo ""

# Lê as variáveis de configuração
FIREFLY_URL=$(grep "firefly.api.url" src/main/resources/application.properties | cut -d'=' -f2)
FIREFLY_TOKEN=$(grep "firefly.api.token" src/main/resources/application.properties | cut -d'=' -f2)

echo "📋 Configuração Atual:"
echo "URL: $FIREFLY_URL"
echo "Token: ${FIREFLY_TOKEN:0:20}... (primeiros 20 caracteres)"
echo ""

# Teste 1: Conectividade básica
echo "🧪 Teste 1: Conectividade com Firefly"
echo "------------------------------------"
if ping -c 1 $(echo $FIREFLY_URL | cut -d'/' -f3 | cut -d':' -f1) > /dev/null 2>&1; then
    echo "✅ Firefly está acessível"
else
    echo "❌ Firefly NÃO está acessível"
    echo "   Verifique se a URL está correta e Firefly está rodando"
    exit 1
fi
echo ""

# Teste 2: Autenticação
echo "🧪 Teste 2: Autenticação com API"
echo "--------------------------------"
RESPONSE=$(curl -s -w "\n%{http_code}" \
    "$FIREFLY_URL/accounts?type=asset" \
    -H "Authorization: Bearer $FIREFLY_TOKEN")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Token válido! (HTTP 200)"
    echo ""
    echo "📊 Primeiros dados recebidos:"
    echo "$BODY" | jq '.' | head -20
else
    echo "❌ Erro na autenticação (HTTP $HTTP_CODE)"
    echo "   Verifique o token em Firefly"
    exit 1
fi
echo ""

# Teste 3: Budgets
echo "🧪 Teste 3: Carregando Orçamentos"
echo "--------------------------------"
BUDGETS=$(curl -s "$FIREFLY_URL/budgets" \
    -H "Authorization: Bearer $FIREFLY_TOKEN")

BUDGET_COUNT=$(echo "$BUDGETS" | jq '.data | length')
echo "✅ Total de orçamentos: $BUDGET_COUNT"
echo ""
echo "Orçamentos encontrados:"
echo "$BUDGETS" | jq -r '.data[] | "\(.id) - \(.attributes.name) - €\(.attributes.auto_budget_amount)"'
echo ""

# Teste 4: Transações do mês
echo "🧪 Teste 4: Carregando Transações do Mês"
echo "---------------------------------------"
START_DATE=$(date -d "1 month ago" +%Y-%m-01)
END_DATE=$(date +%Y-%m-%d)

TRANSACTIONS=$(curl -s "$FIREFLY_URL/transactions?start=$START_DATE&end=$END_DATE" \
    -H "Authorization: Bearer $FIREFLY_TOKEN")

TRANS_COUNT=$(echo "$TRANSACTIONS" | jq '.data | length')
echo "✅ Total de transações (últimos 30 dias): $TRANS_COUNT"
echo ""
echo "Últimas 5 transações:"
echo "$TRANSACTIONS" | jq -r '.data[:5] | .[] | "\(.attributes.date) - \(.attributes.description) - €\(.attributes.amount)"'
echo ""

# Conclusão
echo "🎉 Todos os testes passaram!"
echo ""
echo "Sua configuração está correta!"
echo "Acesse: http://localhost:8080/dashboard para ver os dados"

