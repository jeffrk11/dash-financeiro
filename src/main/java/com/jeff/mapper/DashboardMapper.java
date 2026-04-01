package com.jeff.mapper;

import com.aayushatharva.brotli4j.common.annotations.Local;
import com.jeff.client.MonthlyFinancialData;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.jeff.client.FireflyClient;
import com.jeff.dto.BudgetDTO;
import com.jeff.dto.CategoryDTO;
import com.jeff.dto.DashboardDTO;
import com.jeff.dto.SankeyDTO;
import com.jeff.dto.SummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DashboardMapper {
    private static final Logger log = LoggerFactory.getLogger(DashboardMapper.class);
    private final LocalDate today;
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
            List<MonthlyFinancialData> monthlyFinancialData) {
        today = LocalDate.now();
        
        LocalDate startOfMonth = referenceDate.withDayOfMonth(1);
         // Use sempre o dia de hoje, não a data de referência
        
        SummaryDTO summary = buildSummary(transactions);
        List<BudgetDTO> budgetDTOs = buildBudgets(budgets, transactions);
        SankeyDTO sankey = buildSankey(transactions, startOfMonth);
        
        DashboardDTO dto = new DashboardDTO();
        dto.summary = summary;
        dto.budgets = budgetDTOs;
        dto.sankey = sankey;
        dto.monthlyFinancialData = monthlyFinancialData;
        dto.yearProjectedBalance = dto.calculateYearBalance();
        
        return dto;
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
