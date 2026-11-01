package com.jeff.client;

import com.aayushatharva.brotli4j.common.annotations.Local;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@ApplicationScoped
public class FireflyClient {

    private static final Logger log = LoggerFactory.getLogger(FireflyClient.class);
    @ConfigProperty(name = "firefly.api.url")
    String fireflyApiUrl;

    @ConfigProperty(name = "firefly.api.token")
    String fireflyApiToken;

    private Client client;
    private ObjectMapper objectMapper;

    public FireflyClient() {
        this.client = ClientBuilder.newClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Busca todas as contas do Firefly
     */
    public List<Account> getAccounts() {
        String url = fireflyApiUrl + "/accounts?type=asset&limit=300";

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseAccounts(json);
        }
        return new ArrayList<>();
    }

    /**
     * Busca os passivos (empréstimos, financiamentos, dívidas) do Firefly.
     */
    public List<Account> getLiabilities() {
        String url = fireflyApiUrl + "/accounts?type=liability&limit=300";

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseAccounts(json);
        }
        return new ArrayList<>();
    }

    /**
     * Busca todas as transações de uma categoria (sem filtro de período — usado pra
     * calcular a média histórica das despesas "fixas" que não têm valor/data fixos,
     * tipo o cartão de crédito com pagamento em percentual).
     */
    public List<Transaction> getTransactionsByCategory(String categoryId) {
        return fetchAllTransactionPages(fireflyApiUrl + "/categories/" + categoryId + "/transactions");
    }

    /**
     * Busca transações num período (ex: o ano inteiro de uma vez — ver
     * DashboardService.getYearlyData, que fazia 12 chamadas separadas, uma por mês).
     */
    public List<Transaction> getTransactions(LocalDate startDate, LocalDate endDate) {
        return fetchAllTransactionPages(fireflyApiUrl + "/transactions?start=" + startDate + "&end=" + endDate);
    }

    /**
     * Segue a paginação da API do Firefly até a última página, em vez de confiar num
     * `limit` alto fixo — um `limit=300` já se mostrou insuficiente uma vez (o ano
     * inteiro de withdrawals sozinho passa de 500 lançamentos), e qualquer limite fixo
     * volta a quebrar silenciosamente assim que o volume de dados crescer de novo.
     */
    private List<Transaction> fetchAllTransactionPages(String baseUrl) {
        List<Transaction> all = new ArrayList<>();
        String sep = baseUrl.contains("?") ? "&" : "?";
        int page = 1;
        int totalPages = 1;
        do {
            String url = baseUrl + sep + "limit=300&page=" + page;
            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + fireflyApiToken)
                    .get();

            if (response.getStatus() != 200) break;

            try {
                JsonNode root = objectMapper.readTree(response.readEntity(String.class));
                all.addAll(parseTransactionsFromNode(root));
                totalPages = root.path("meta").path("pagination").path("total_pages").asInt(1);
            } catch (Exception e) {
                log.error("Erro ao paginar transações de " + baseUrl, e);
                break;
            }
            page++;
        } while (page <= totalPages);
        return all;
    }

    /**
     * Busca todos os budgets
     */
    public List<Budget> getBudgets() {
        String url = fireflyApiUrl + "/budgets?limit=300";
        
        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseBudgets(json);
        }
        return new ArrayList<>();
    }

    /**
     * Busca todas as ocorrências de recorrências dentro de um período — uma chamada só
     * cobrindo o ano inteiro, em vez de uma chamada por mês (as regras de recorrência em
     * si são poucas; o que varia por mês é só quais ocorrências caem dentro do período
     * pedido, e isso agora é filtrado aqui, não refeito via nova chamada por mês).
     */
    public List<Transaction> getRecurrences(LocalDate startDate, LocalDate endDate) {
        String url = fireflyApiUrl + "/recurrences?limit=300";

        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseRecurrenceOccurrences(json, startDate, endDate);
        }
        return new ArrayList<>();
    }

    /**
     * Busca os limites (spend limits) de um budget específico
     */
    public BudgetLimit getBudgetLimit(String budgetId) {
        return getBudgetLimit(budgetId, null, null);
    }

    /**
     * Busca os limites (spend limits) de um budget específico para um período
     */
    public BudgetLimit getBudgetLimit(String budgetId, LocalDate startDate, LocalDate endDate) {
        String url = fireflyApiUrl + "/budgets/" + budgetId + "/limits";
        
        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseBudgetLimits(json, startDate, endDate);
        }
        return null;
    }

    /**
     * Parser para Budgets - converte JSON em objetos Budget
     */
    private List<Budget> parseBudgets(String json) {
        List<Budget> budgets = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    JsonNode attributes = item.get("attributes");
                    if (attributes != null) {
                        String id = item.get("id").asText();
                        String name = attributes.get("name").asText();
                        BigDecimal budgeted = new BigDecimal(
                            attributes.get("auto_budget_amount").asText("0.00")
                        );
                        BigDecimal spent = BigDecimal.ZERO;
                        
                        // Tenta pegar o campo "spent" se existir
                        if (attributes.has("spent") && !attributes.get("spent").isNull()) {
                            spent = new BigDecimal(attributes.get("spent").asText("0.00"));
                        }
                        
                        budgets.add(new Budget(id, name, spent, budgeted));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear budgets: " + e.getMessage());
            e.printStackTrace();
        }
        return budgets;
    }

    /**
     * Parser para Budget Limits - extrai o limite do período especificado
     */
    private BudgetLimit parseBudgetLimits(String json, LocalDate startDate, LocalDate endDate) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            
            if (dataArray != null && dataArray.isArray()) {
                // Se não foi especificado um período, procura pelo mês atual
                if (startDate == null || endDate == null) {
                    LocalDate now = LocalDate.now();
                    startDate = now.withDayOfMonth(1);
                    endDate = now.withDayOfMonth(now.lengthOfMonth());
                }
                
                String startPrefix = startDate.toString().substring(0, 7); // YYYY-MM
                String endPrefix = endDate.toString().substring(0, 7);     // YYYY-MM
                
                for (JsonNode item : dataArray) {
                    JsonNode attributes = item.get("attributes");
                    if (attributes != null) {
                        String start = attributes.get("start").asText("");
                        String period = attributes.has("period") ? attributes.get("period").asText() : null;
                        
                        // Verifica se o limite está dentro do período solicitado
                        if (start.startsWith(startPrefix) && "monthly".equals(period)) {
                            String id = item.get("id").asText();
                            BigDecimal amount = new BigDecimal(attributes.get("amount").asText("0.00"));
                            
                            // Extrai o valor gasto (está dentro de um array "spent")
                            BigDecimal spent = BigDecimal.ZERO;
                            JsonNode spentArray = attributes.get("spent");
                            if (spentArray != null && spentArray.isArray() && spentArray.size() > 0) {
                                String spentStr = spentArray.get(0).get("sum").asText("0.00");
                                // O valor vem negativo na API (ex: "-400.53"), precisamos converter para positivo
                                spent = new BigDecimal(spentStr).abs();
                            }
                            
                            return new BudgetLimit(id, amount, spent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear budget limits: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Parser para Budget Limits - extrai o limite do mês atual
     */
    private BudgetLimit parseBudgetLimits(String json) {
        return parseBudgetLimits(json, null, null);
    }

    /**
     * Parser para Transactions - converte JSON (já parseado) em objetos Transaction
     */
    private List<Transaction> parseTransactionsFromNode(JsonNode root) {
        List<Transaction> transactions = new ArrayList<>();
        try {
            JsonNode dataArray = root.get("data");

            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    JsonNode attributes = item.get("attributes");
                    if (attributes != null) {
                        // A API do Firefly retorna um array de transações dentro de cada item
                        JsonNode transactionsArray = attributes.get("transactions");

                        if (transactionsArray != null && transactionsArray.isArray()) {
                            for (JsonNode tx : transactionsArray) {
                                String id = item.get("id").asText();
                                String description = tx.get("description").asText("");
                                String typeStr = tx.get("type").asText("withdrawal");
                                String dateStr = tx.get("date").asText();
                                String category = tx.get("category_name").asText("Sem categoria");
                                List<String> tags=  objectMapper.convertValue(tx.get("tags"), new TypeReference<List<String>>() {});
                                String sourceName = tx.get("source_name").asText(null);
                                String destinationName = tx.get("destination_name").asText(null);

                                // Parsear valor
                                BigDecimal amount = BigDecimal.ZERO;
                                if (tx.has("amount") && !tx.get("amount").isNull()) {
                                    String amountStr = tx.get("amount").asText("0.00");
                                    amount = new BigDecimal(amountStr);
                                }

                                // Parsear data - formato ISO 8601: "2026-03-15T14:50:59+00:00"
                                LocalDate date = LocalDate.parse(dateStr.substring(0, 10)); // YYYY-MM-DD

                                String status = "ok"; // Firefly não tem campo status, assumir ok como padrão

                                transactions.add(new Transaction(id, description, amount, date, typeStr, status, category, tags, sourceName, destinationName));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear transações: " + e.getMessage());
            e.printStackTrace();
        }
        return transactions;
    }

    /**
     * Parser para Accounts - converte JSON em objetos Account
     */
    private List<Account> parseAccounts(String json) {
        List<Account> accounts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    JsonNode attributes = item.get("attributes");
                    if (attributes != null) {
                        String id = item.get("id").asText();
                        String name = attributes.get("name").asText();
                        String type = attributes.get("type").asText();
                        BigDecimal balance = BigDecimal.ZERO;
                        
                        if (attributes.has("current_balance") && !attributes.get("current_balance").isNull()) {
                            balance = new BigDecimal(attributes.get("current_balance").asText("0.00"));
                        }

                        Account account = new Account(id, name, type, balance);
                        if (attributes.has("virtual_balance") && !attributes.get("virtual_balance").isNull()) {
                            account.virtualBalance = new BigDecimal(attributes.get("virtual_balance").asText("0.00"));
                        }
                        if (attributes.has("opening_balance") && !attributes.get("opening_balance").isNull()) {
                            account.openingBalance = new BigDecimal(attributes.get("opening_balance").asText("0.00"));
                        }
                        if (attributes.has("opening_balance_date") && !attributes.get("opening_balance_date").isNull()) {
                            String dateStr = attributes.get("opening_balance_date").asText();
                            if (dateStr != null && dateStr.length() >= 10) {
                                account.openingBalanceDate = LocalDate.parse(dateStr.substring(0, 10));
                            }
                        }
                        if (attributes.has("liability_direction") && !attributes.get("liability_direction").isNull()) {
                            account.liabilityDirection = attributes.get("liability_direction").asText();
                        }
                        accounts.add(account);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear contas: " + e.getMessage());
            e.printStackTrace();
        }
        return accounts;
    }

    /**
     * Uma ocorrência (de qualquer uma das repetições da regra, não só a primeira) por
     * transação da recorrência, pra cada data dentro de [startDate, endDate] — achata
     * direto em Transaction porque é exatamente a forma que o resto do código já espera
     * (igual a uma transação normal, só que prevista/agendada).
     */
    private List<Transaction> parseRecurrenceOccurrences(String json, LocalDate startDate, LocalDate endDate) {
        List<Transaction> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray == null || !dataArray.isArray()) return result;

            for (JsonNode item : dataArray) {
                JsonNode attributes = item.get("attributes");
                if (attributes == null) continue;

                String type = attributes.get("type").asText("withdrawal");
                JsonNode transactionsArray = attributes.get("transactions");
                if (transactionsArray == null || !transactionsArray.isArray()) continue;

                Set<LocalDate> occurrenceDates = new TreeSet<>();
                JsonNode repetitionsArray = attributes.get("repetitions");
                if (repetitionsArray != null && repetitionsArray.isArray()) {
                    for (JsonNode repetition : repetitionsArray) {
                        JsonNode occurrencesArray = repetition.get("occurrences");
                        if (occurrencesArray == null || !occurrencesArray.isArray()) continue;
                        for (JsonNode occurrence : occurrencesArray) {
                            LocalDate date = LocalDate.parse(occurrence.asText().substring(0, 10));
                            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                                occurrenceDates.add(date);
                            }
                        }
                    }
                }

                for (LocalDate date : occurrenceDates) {
                    for (JsonNode tx : transactionsArray) {
                        String description = tx.get("description").asText("defaultValue");
                        String category = tx.get("category_name").asText("Sem categoria");
                        BigDecimal amount = new BigDecimal(tx.get("amount").asText("0"));
                        List<String> tags = objectMapper.convertValue(tx.get("tags"), new TypeReference<List<String>>() {});
                        result.add(new Transaction(item.get("id").asText(), description, amount, date, type, "ok", category, tags));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao parsear recurrences", e);
        }
        return result;
    }

    // DTOs simples
    public static class Account {
        public String id;
        public String name;
        public String type;
        public BigDecimal balance;
        /** Pra contas de cartão de crédito: usamos esse campo como "limite" (truque, já que o Firefly não tem campo nativo de limite). */
        public BigDecimal virtualBalance;
        /** Pra passivos (empréstimos): ponto de partida do saldo devedor, usado pra reconstruir a evolução mês a mês. */
        public BigDecimal openingBalance;
        public LocalDate openingBalanceDate;
        /**
         * "debit" (padrão, ex: empréstimo) = balance É o quanto devemos. "credit" = o
         * oposto — usado deliberadamente num cartão de crédito modelado como passivo
         * onde balance representa crédito DISPONÍVEL (sobe ao pagar, desce ao comprar),
         * não o quanto se deve.
         */
        public String liabilityDirection;

        public Account() {}
        public Account(String id, String name, String type, BigDecimal balance) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.balance = balance;
        }
    }

    public static class Transaction {
        public String id;
        public String description;
        public BigDecimal amount;
        public LocalDate date;
        public String type; // "withdrawal" ou "deposit"
        public String status;
        public String category; // categoria da transação
        public List<String> tags;
        public String sourceName;
        public String destinationName;

        public Transaction() {}
        public Transaction(String id, String description, BigDecimal amount, LocalDate date, String type, String status) {
            this(id, description, amount, date, type, status, "Sem categoria", new ArrayList<>());
        }

        public Transaction(String id, String description, BigDecimal amount, LocalDate date, String type, String status, String category, List<String> tags) {
            this(id, description, amount, date, type, status, category, tags, null, null);
        }

        public Transaction(String id, String description, BigDecimal amount, LocalDate date, String type, String status, String category, List<String> tags, String sourceName, String destinationName) {
            this.id = id;
            this.description = description;
            this.amount = amount;
            this.date = date;
            this.type = type;
            this.status = status;
            this.category = category;
            this.tags = tags;
            this.sourceName = sourceName;
            this.destinationName = destinationName;
        }
    }

    public static class Budget {
        public String id;
        public String name;
        public BigDecimal spent;
        public BigDecimal budgeted;
        public BigDecimal currentMonthSpent; // Gasto no mês atual (do limite)

        public Budget() {}
        public Budget(String id, String name, BigDecimal spent, BigDecimal budgeted) {
            this.id = id;
            this.name = name;
            this.spent = spent;
            this.budgeted = budgeted;
            this.currentMonthSpent = BigDecimal.ZERO;
        }
    }

    public static class BudgetLimit {
        public String id;
        public BigDecimal amount; // Limite do orçamento
        public BigDecimal spent;  // Gasto neste período

        public BudgetLimit() {}
        public BudgetLimit(String id, BigDecimal amount, BigDecimal spent) {
            this.id = id;
            this.amount = amount;
            this.spent = spent;
        }
    }
}
