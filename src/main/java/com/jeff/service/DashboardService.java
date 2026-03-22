package com.jeff.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jeff.client.FireflyClient;
import com.jeff.dto.DashboardDTO;
import com.jeff.dto.BudgetDTO;
import com.jeff.dto.SummaryDTO;
import com.jeff.mapper.DashboardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    @Inject
    FireflyClient fireflyClient;

    @Inject
    DashboardMapper dashboardMapper;

    /**
     * Busca dados em tempo real do Firefly para um mês específico
     */
    public DashboardDTO getDashboard(String month) {
        try {
            LocalDate today = LocalDate.now();
            YearMonth currentSelection = YearMonth.parse(month);
            
            // Se um mês foi especificado, usa ele, senão usa o mês atual
            if (month != null && !month.isEmpty()) {
                String[] parts = month.split("-");
                if (parts.length == 2) {
                    int year = Integer.parseInt(parts[0]);
                    int monthValue = Integer.parseInt(parts[1]);
                    today = LocalDate.of(year, monthValue, 1);
                }
            }
            
            // Calcula as datas do mês especificado
            LocalDate startOfMonth = today.withDayOfMonth(1);
            LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
            
            log.info("Buscando dados para o período: " + startOfMonth + " até " + endOfMonth);

            List<FireflyClient.Recurrence> recurrences = fireflyClient.getRecurrences(currentSelection);

            List<FireflyClient.Transaction> transactions = 
                    fireflyClient.getTransactions(startOfMonth, endOfMonth);

            transactions.addAll(recurrences.stream().map(r -> r.transactions).flatMap(List::stream).toList());
            
            List<FireflyClient.Budget> budgetsList = fireflyClient.getBudgets();
            // Busca os limites (spend limits) para cada budget e atualiza os dados
            for (FireflyClient.Budget budget : budgetsList) {
                FireflyClient.BudgetLimit limit = fireflyClient.getBudgetLimit(budget.id, startOfMonth, endOfMonth);
                if (limit != null) {
                    budget.currentMonthSpent = limit.spent;
                    log.info("Budget " + budget.name + " - Gasto no período: €" + limit.spent + " / €" + limit.amount);
                }
            }

            
            return dashboardMapper.mapFromFirefly(
                    transactions,
                    budgetsList.stream()
                        .collect(Collectors.toMap(b -> b.name, b -> b)),
                    today);

        } catch (Exception e) {
            System.err.println("Erro ao buscar dados do Firefly: " + e.getMessage());
            e.printStackTrace();
            // Retorna dados fictícios em caso de erro
            return buildDashboard();
        }
    }

    /**
     * Busca dados em tempo real do Firefly (usa o mês atual como padrão)
     */
    public DashboardDTO getDashboard() {
        return getDashboard(null);
    }

    /**
     * Retorna dados fictícios para testes/demo
     */
    public DashboardDTO buildDashboard() {

        SummaryDTO summary = new SummaryDTO();

        summary.spentNow = new BigDecimal("850");
        summary.receivedNow = new BigDecimal("1200");
        summary.spentFuture = new BigDecimal("300");
        summary.receivedFuture = new BigDecimal("500");

        summary.projectedBalance =
                summary.receivedNow.add(summary.receivedFuture)
                        .subtract(summary.spentNow.add(summary.spentFuture));

        BudgetDTO food = new BudgetDTO();
        food.name = "Alimentação";
        food.total = new BigDecimal("500");
        food.spent = new BigDecimal("200");
        food.future = new BigDecimal("100");
        food.available = new BigDecimal("200");

        BudgetDTO transport = new BudgetDTO();
        transport.name = "Transporte";
        transport.total = new BigDecimal("300");
        transport.spent = new BigDecimal("150");
        transport.future = new BigDecimal("30");
        transport.available = new BigDecimal("120");

        DashboardDTO dto = new DashboardDTO();
        dto.summary = summary;
        dto.budgets = List.of(food, transport);

        return dto;
    }
}

