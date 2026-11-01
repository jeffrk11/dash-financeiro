package com.jeff.service;

import com.jeff.client.MonthlyFinancialData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jeff.client.FireflyClient;
import com.jeff.dto.DashboardDTO;
import com.jeff.dto.BudgetDTO;
import com.jeff.dto.SankeyDTO;
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

            // Se um mês foi especificado, usa ele, senão usa o mês atual
            if (month != null && !month.isEmpty()) {
                String[] parts = month.split("-");
                if (parts.length == 2) {
                    int year = Integer.parseInt(parts[0]);
                    int monthValue = Integer.parseInt(parts[1]);
                    today = LocalDate.of(year, monthValue, 1);
                }
            }

            YearMonth currentSelection = YearMonth.from(today);

            // Calcula as datas do mês especificado
            LocalDate startOfMonth = today.withDayOfMonth(1);
            LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
            
            List<FireflyClient.Budget> budgetsList = fireflyClient.getBudgets();
            // Busca os limites (spend limits) para cada budget e atualiza os dados
            for (FireflyClient.Budget budget : budgetsList) {
                FireflyClient.BudgetLimit limit = fireflyClient.getBudgetLimit(budget.id, startOfMonth, endOfMonth);
                if (limit != null) {
                    budget.currentMonthSpent = limit.spent;
                    log.info("Budget " + budget.name + " - Gasto no período: €" + limit.spent + " / €" + limit.amount);
                }
            }


            // Busca o ano inteiro (transações reais + ocorrências de recorrência) de uma
            // vez só, em vez de uma chamada por mês — isso alimenta tanto o mês visualizado
            // quanto a Visão Anual inteira, reduzindo ~26 chamadas (12 meses × transações +
            // recorrências, mais o mês visualizado em separado) pra só 2. Precisa ser o ano
            // civil inteiro (não só até hoje) porque entradas já lançadas com data futura
            // (ex: salário do dia 1 do mês seguinte) alimentam o "Será Recebido" e os meses
            // futuros da Visão Anual.
            LocalDate startOfYear = LocalDate.of(currentSelection.getYear(), 1, 1);
            LocalDate endOfYear = LocalDate.of(currentSelection.getYear(), 12, 31);
            log.info("Buscando o ano inteiro de " + currentSelection.getYear() + " de uma vez (" + startOfYear + " a " + endOfYear + ")");
            List<FireflyClient.Transaction> yearTransactions = fireflyClient.getTransactions(startOfYear, endOfYear);
            yearTransactions.addAll(fireflyClient.getRecurrences(startOfYear, endOfYear));

            List<FireflyClient.Transaction> transactions = yearTransactions.stream()
                    .filter(tx -> tx.date != null && YearMonth.from(tx.date).equals(currentSelection))
                    .collect(Collectors.toList());
            List<FireflyClient.Account> accounts = fireflyClient.getAccounts();
            List<FireflyClient.Account> liabilities = fireflyClient.getLiabilities();
            // Transações que tocam uma conta de passivo (ex: amortização de capital de
            // financiamento) são só contabilidade interna pra mexer o saldo da dívida —
            // nunca renda/gasto real, mesmo quando o Firefly as classifica como
            // deposit/withdrawal (não dá pra usar transfer quando uma ponta é passivo).
            Set<String> liabilityAccountNames = liabilities.stream().map(a -> a.name).collect(Collectors.toSet());
            List<MonthlyFinancialData> monthlyFinancialData = getYearlyData(currentSelection.getYear(), yearTransactions, liabilityAccountNames);
            // Categoria "fixas" (id 3) = só gastos recorrentes, sem pontual nela — serve
            // de histórico pra prever os que ainda não aconteceram este mês.
            List<FireflyClient.Transaction> fixasHistory = fireflyClient.getTransactionsByCategory("3");
            // Categorias "salario" (id 8) e "rendimentos" (id 20) — mesma lógica de
            // previsão pro lado das entradas (ex: salário alimentação, juros poupança).
            List<FireflyClient.Transaction> incomeHistory = new ArrayList<>();
            incomeHistory.addAll(fireflyClient.getTransactionsByCategory("8"));
            incomeHistory.addAll(fireflyClient.getTransactionsByCategory("20"));
            // Categoria "investimento" (id 12) — histórico do ano inteiro pro tooltip do
            // total "Investido" na Visão Anual, pra ver quais investimentos específicos
            // compõem aquele valor.
            List<FireflyClient.Transaction> investmentHistory = fireflyClient.getTransactionsByCategory("12");

            return dashboardMapper.mapFromFirefly(
                    transactions,
                    budgetsList.stream()
                        .collect(Collectors.toMap(b -> b.name, b -> b)),
                    today, monthlyFinancialData, accounts, liabilities, fixasHistory, incomeHistory, investmentHistory,
                    yearTransactions, startOfYear);

        } catch (Exception e) {
            System.err.println("Erro ao buscar dados do Firefly: " + e.getMessage());
            e.printStackTrace();
            // Retorna dados fictícios em caso de erro
            return buildDashboard();
        }
    }

    private List<MonthlyFinancialData> getYearlyData(int year, List<FireflyClient.Transaction> yearTransactions, Set<String> liabilityAccountNames){
        List<MonthlyFinancialData> yearlyData =  new ArrayList<>();
        IntStream.rangeClosed(1, 12).forEach(month -> {
            YearMonth ym = YearMonth.of(year, month);
            MonthlyFinancialData monthlyFinancialData = new MonthlyFinancialData(ym);

            List<FireflyClient.Transaction> monthTransactions = yearTransactions.stream()
                    .filter(tx -> tx.date != null && YearMonth.from(tx.date).equals(ym))
                    .collect(Collectors.toList());
            monthlyFinancialData.buildMonthlyFinancialData(monthTransactions, liabilityAccountNames);
            yearlyData.add(monthlyFinancialData);
        });

        return yearlyData;
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
        dto.sankey = new SankeyDTO(BigDecimal.ZERO, List.of(), List.of());
        dto.sankeyAnnual = new SankeyDTO(BigDecimal.ZERO, List.of(), List.of());
        dto.monthlyFinancialData = new ArrayList<>();

        return dto;
    }
}

