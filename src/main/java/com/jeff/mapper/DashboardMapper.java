package com.jeff.mapper;

import com.aayushatharva.brotli4j.common.annotations.Local;
import com.jeff.client.MonthlyFinancialData;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import com.jeff.client.FireflyClient;
import com.jeff.dto.AccountDTO;
import com.jeff.dto.BudgetDTO;
import com.jeff.dto.CategoryDTO;
import com.jeff.dto.DashboardDTO;
import com.jeff.dto.DebtDTO;
import com.jeff.dto.PredictedExpenseDTO;
import com.jeff.dto.SankeyDTO;
import com.jeff.dto.SummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DashboardMapper {
    private static final Logger log = LoggerFactory.getLogger(DashboardMapper.class);
    private LocalDate today;
    public DashboardMapper() {
        today = LocalDate.now();
    }

    /**
     * Mapeia dados do Firefly para DashboardDTO
     */
    public DashboardDTO mapFromFirefly(
            List<FireflyClient.Transaction> transactions,
            Map<String, FireflyClient.Budget> budgets,
            LocalDate referenceDate,
            List<MonthlyFinancialData> monthlyFinancialData,
            List<FireflyClient.Account> accounts,
            List<FireflyClient.Account> liabilities,
            List<FireflyClient.Transaction> fixasHistory) {
        today = LocalDate.now();

        LocalDate startOfMonth = referenceDate.withDayOfMonth(1);
         // Use sempre o dia de hoje, não a data de referência

        SummaryDTO summary = buildSummary(transactions);
        List<BudgetDTO> budgetDTOs = buildBudgets(budgets, transactions);
        SankeyDTO sankey = buildSankey(transactions, startOfMonth);
        List<AccountDTO> accountDTOs = buildAccounts(accounts);
        List<DebtDTO> debtDTOs = buildDebts(liabilities);
        List<PredictedExpenseDTO> predictedDTOs = buildPredictedRecurring(fixasHistory, YearMonth.from(startOfMonth));

        DashboardDTO dto = new DashboardDTO();
        dto.summary = summary;
        dto.budgets = budgetDTOs;
        dto.sankey = sankey;
        dto.monthlyFinancialData = monthlyFinancialData;
        dto.yearProjectedBalance = dto.calculateYearBalance();
        dto.accounts = accountDTOs;
        dto.debts = debtDTOs;
        dto.predictedRecurring = predictedDTOs;

        return dto;
    }

    /**
     * Despesas da categoria "fixas" que normalmente acontecem todo mês mas ainda não
     * aconteceram no mês visualizado — estimadas pela média dos meses anteriores.
     * Só existem gastos recorrentes nessa categoria (sem pontual), por isso dá pra usar
     * ela direto sem precisar de uma tag separada.
     */
    private List<PredictedExpenseDTO> buildPredictedRecurring(List<FireflyClient.Transaction> fixasHistory, YearMonth viewedMonth) {
        if (fixasHistory == null) return new ArrayList<>();

        Map<String, List<BigDecimal>> historyByDescription = new HashMap<>();
        Map<String, Set<YearMonth>> monthsSeenByDescription = new HashMap<>();
        Set<String> happenedThisMonth = new HashSet<>();

        for (FireflyClient.Transaction tx : fixasHistory) {
            if (tx.date == null || tx.description == null || !"withdrawal".equals(tx.type)) continue;
            YearMonth txMonth = YearMonth.from(tx.date);
            if (txMonth.equals(viewedMonth)) {
                happenedThisMonth.add(tx.description);
            } else if (txMonth.isBefore(viewedMonth)) {
                historyByDescription.computeIfAbsent(tx.description, k -> new ArrayList<>()).add(tx.amount.abs());
                monthsSeenByDescription.computeIfAbsent(tx.description, k -> new HashSet<>()).add(txMonth);
            }
        }

        // Só prevê quem já apareceu em pelo menos 2 meses diferentes — descrições que só
        // aparecem uma vez costumam ser rótulo pontual/variado (ex: "seguro casa março"),
        // não uma recorrência de verdade, e previam gasto fantasma se contadas.
        List<PredictedExpenseDTO> predicted = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : historyByDescription.entrySet()) {
            if (happenedThisMonth.contains(entry.getKey())) continue;
            if (monthsSeenByDescription.get(entry.getKey()).size() < 2) continue;
            List<BigDecimal> amounts = entry.getValue();
            BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);
            predicted.add(new PredictedExpenseDTO(entry.getKey(), avg));
        }
        predicted.sort((a, b) -> b.amount.compareTo(a.amount));
        return predicted;
    }

    /**
     * Mapeia as contas do Firefly, ordenadas por saldo decrescente.
     */
    private List<AccountDTO> buildAccounts(List<FireflyClient.Account> accounts) {
        if (accounts == null) return new ArrayList<>();
        return accounts.stream()
                .filter(a -> !"Carteira de dinheiro".equals(a.name))
                .map(a -> {
                    AccountDTO dto = new AccountDTO(a.name, a.balance);
                    String lowerName = a.name.toLowerCase();
                    dto.reserve = lowerName.contains("poupança") || lowerName.contains("poupanca")
                            || lowerName.contains("reserva") || lowerName.contains("invest")
                            || lowerName.contains("trade republic");
                    dto.limit = a.virtualBalance;
                    return dto;
                })
                .sorted((a, b) -> b.balance.compareTo(a.balance))
                .collect(Collectors.toList());
    }

    /**
     * Mapeia os passivos (empréstimos/financiamentos) do Firefly, maior dívida primeiro.
     */
    private List<DebtDTO> buildDebts(List<FireflyClient.Account> liabilities) {
        if (liabilities == null) return new ArrayList<>();
        return liabilities.stream()
                .map(a -> new DebtDTO(a.name, a.balance))
                .sorted((a, b) -> b.owed.compareTo(a.owed))
                .collect(Collectors.toList());
    }

    /**
     * Constrói o resumo financeiro a partir das transações
     */
    private SummaryDTO buildSummary(List<FireflyClient.Transaction> transactions) {
        SummaryDTO summary = new SummaryDTO();
        
        BigDecimal spentNow = BigDecimal.ZERO;
        BigDecimal receivedNow = BigDecimal.ZERO;
        BigDecimal spentFuture = BigDecimal.ZERO;
        BigDecimal receivedFuture = BigDecimal.ZERO;
        
        for (FireflyClient.Transaction tx : transactions) {
            if (tx.date == null) continue;
            
            boolean isPast = tx.date.isBefore(today) || tx.date.isEqual(today);
            boolean isWithdrawal = "withdrawal".equals(tx.type);

            if(tx.type.equals("transfer"))
                continue;

            if (isPast) {
                if (isWithdrawal) {
                    spentNow = spentNow.add(tx.amount);
                } else  {
                    receivedNow = receivedNow.add(tx.amount);
                }
            } else {
                if (isWithdrawal) {
                    log.info("today: {}", today.toString());
                    log.info("Transação futura de saída: {} - {} - {}", tx.description, tx.amount, tx.date);
                    spentFuture = spentFuture.add(tx.amount);
                } else {
                    receivedFuture = receivedFuture.add(tx.amount);
                }
            }
        }
        
        summary.spentNow = spentNow;
        summary.receivedNow = receivedNow;
        summary.spentFuture = spentFuture;
        summary.receivedFuture = receivedFuture;
        
        // Calcula saldo projetado
        BigDecimal totalReceived = receivedNow.add(receivedFuture);
        BigDecimal totalSpent = spentNow.add(spentFuture);
        summary.projectedBalance = totalReceived.subtract(totalSpent);
        
        return summary;
    }

    /**
     * Constrói os orçamentos a partir dos dados do Firefly
     */
    private List<BudgetDTO> buildBudgets(Map<String ,FireflyClient.Budget> budgets,
                                         List<FireflyClient.Transaction> transactions) {

        Map<String, BudgetDTO> budgetMap = new HashMap<>();

        for(FireflyClient.Transaction tx : transactions) {
            Optional<String> budgetName =  tx.tags.stream().filter(t -> t.contains("budget")).findFirst();
            if(budgetName.isEmpty()) continue;

            if(!budgetMap.containsKey(budgetName.get())) {
                BudgetDTO dto = new BudgetDTO(budgetName.get());
                String lowerName = budgetName.get().toLowerCase();
                dto.inverse = lowerName.contains("reserva") || lowerName.contains("invest");
                budgetMap.put(budgetName.get(), dto);
            }

            BudgetDTO dto = budgetMap.get(budgetName.get());

            boolean isPast = tx.date.isBefore(today) || tx.date.isEqual(today);

            switch (tx.type) {
                case "withdrawal":
                        if (isPast)
                            dto.spent = dto.spent.add(tx.amount);
                        else
                            dto.future = dto.future.add(tx.amount);
                    break;
                case "deposit":
                        if (isPast)
                            dto.spent = dto.spent.subtract(tx.amount);
                        else
                            dto.future = dto.future.subtract(tx.amount);
                    break;
                case "transfer":
                    if (tx.tags.contains("saving")) {
                        if (isPast)
                            dto.spent = dto.spent.add(tx.amount);
                        else
                            dto.future = dto.future.add(tx.amount);

                    }else if (tx.tags.contains("expending")) {
                        if (isPast)
                            dto.spent = dto.spent.subtract(tx.amount);
                        else
                            dto.future = dto.future.subtract(tx.amount);
                    }
                    break;
            }
        }

        budgetMap.forEach((name, dto) -> {
            FireflyClient.Budget budget = budgets.get(name);
            if (budget != null) {
                dto.total = budget.budgeted;
                dto.available = dto.total.subtract(dto.spent).subtract(dto.future);
                dto.available = dto.available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : dto.available;
            }
        });

        return new ArrayList<>(budgetMap.values());
    }

    /**
     * Constrói o Sankey com dados de entradas e categorias de despesas
     * Fluxo: Entradas por categoria → Renda Total → Despesas por categoria
     */
    private SankeyDTO buildSankey(List<FireflyClient.Transaction> transactions, LocalDate startOfMonth) {
        // Calcula total de entradas (depósitos) do mês
        BigDecimal totalIncome = BigDecimal.ZERO;
        
        // Agrupa entradas por categoria
        Map<String, BigDecimal> incomeByCategory = new HashMap<>();
        
        // Agrupa despesas por categoria
        Map<String, BigDecimal> expenseByCategory = new HashMap<>();
        
        for (FireflyClient.Transaction tx : transactions) {
            if (tx.date == null || tx.date.isBefore(startOfMonth)) continue;
            
            boolean isDeposit = "deposit".equals(tx.type);
            boolean isWithdrawal = "withdrawal".equals(tx.type);
            
            // Agrupar entradas por categoria
            if (isDeposit) {
                totalIncome = totalIncome.add(tx.amount);
                String category = tx.category != null ? tx.category : "Outras Entradas";
                BigDecimal currentAmount = incomeByCategory.getOrDefault(category, BigDecimal.ZERO);
                incomeByCategory.put(category, currentAmount.add(tx.amount));
            }
            
            // Agrupar despesas por categoria
            if (isWithdrawal) {
                String category = tx.category != null ? tx.category : "Sem categoria";
                BigDecimal currentAmount = expenseByCategory.getOrDefault(category, BigDecimal.ZERO);
                expenseByCategory.put(category, currentAmount.add(tx.amount));
            }
        }
        
        // Converter para listas de CategoryDTO e ordenar por valor descrescente
        List<CategoryDTO> incomeCategories = incomeByCategory.entrySet().stream()
                .map(entry -> new CategoryDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> b.amount.compareTo(a.amount))
                .collect(Collectors.toList());
        
        List<CategoryDTO> expenseCategories = expenseByCategory.entrySet().stream()
                .map(entry -> new CategoryDTO(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> b.amount.compareTo(a.amount))
                .collect(Collectors.toList());
        
        return new SankeyDTO(totalIncome, incomeCategories, expenseCategories);
    }
}
