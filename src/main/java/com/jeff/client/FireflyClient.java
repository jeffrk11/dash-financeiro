package com.jeff.client;

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
        String url = fireflyApiUrl + "/accounts?type=asset";
        
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
     * Busca todas as transações do mês
     */
    public List<Transaction> getTransactions(LocalDate startDate, LocalDate endDate) {
        String url = fireflyApiUrl + "/transactions?start=" + startDate + "&end=" + endDate;
        
        Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fireflyApiToken)
                .get();

        if (response.getStatus() == 200) {
            String json = response.readEntity(String.class);
            return parseTransactions(json);
        }
        return new ArrayList<>();
    }

    /**
     * Busca todos os budgets
     */
    public List<Budget> getBudgets() {
        String url = fireflyApiUrl + "/budgets";
        
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
     * Parser para Transactions - converte JSON em objetos Transaction
     */
    private List<Transaction> parseTransactions(String json) {
        List<Transaction> transactions = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
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

                                // Parsear valor
                                BigDecimal amount = BigDecimal.ZERO;
                                if (tx.has("amount") && !tx.get("amount").isNull()) {
                                    String amountStr = tx.get("amount").asText("0.00");
                                    amount = new BigDecimal(amountStr);
                                }
                                
                                // Parsear data - formato ISO 8601: "2026-03-15T14:50:59+00:00"
                                LocalDate date = LocalDate.parse(dateStr.substring(0, 10)); // YYYY-MM-DD
                                
                                String status = "ok"; // Firefly não tem campo status, assumir ok como padrão
                                
                                transactions.add(new Transaction(id, description, amount, date, typeStr, status, category, tags));
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
                        
                        accounts.add(new Account(id, name, type, balance));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear contas: " + e.getMessage());
            e.printStackTrace();
        }
        return accounts;
    }

    // DTOs simples
    public static class Account {
        public String id;
        public String name;
        public String type;
        public BigDecimal balance;

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

        public Transaction() {}
        public Transaction(String id, String description, BigDecimal amount, LocalDate date, String type, String status) {
            this(id, description, amount, date, type, status, "Sem categoria", new ArrayList<>());
        }
        
        public Transaction(String id, String description, BigDecimal amount, LocalDate date, String type, String status, String category, List<String> tags) {
            this.id = id;
            this.description = description;
            this.amount = amount;
            this.date = date;
            this.type = type;
            this.status = status;
            this.category = category;
            this.tags = tags;
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
