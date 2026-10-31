package com.jeff.mapper;

import com.aayushatharva.brotli4j.common.annotations.Local;
import com.jeff.client.MonthlyFinancialData;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
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
import com.jeff.dto.FutureIncomeDTO;
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
            List<FireflyClient.Transaction> fixasHistory,
            List<FireflyClient.Transaction> incomeHistory) {
        today = LocalDate.now();

        LocalDate startOfMonth = referenceDate.withDayOfMonth(1);
         // Use sempre o dia de hoje, não a data de referência

        SummaryDTO summary = buildSummary(transactions);
        List<BudgetDTO> budgetDTOs = buildBudgets(budgets, transactions);
        SankeyDTO sankey = buildSankey(transactions, startOfMonth);
        List<AccountDTO> accountDTOs = buildAccounts(accounts);
        List<DebtDTO> debtDTOs = buildDebts(liabilities);
        List<PredictedExpenseDTO> predictedDTOs = buildPredictedRecurring(fixasHistory, YearMonth.from(startOfMonth));
        List<FutureIncomeDTO> futureIncomeDTOs = buildFutureIncome(transactions, incomeHistory, YearMonth.from(startOfMonth));

        DashboardDTO dto = new DashboardDTO();
        dto.summary = summary;
        dto.budgets = budgetDTOs;
        dto.sankey = sankey;
        dto.monthlyFinancialData = monthlyFinancialData;
        dto.yearProjectedBalance = dto.calculateYearBalance();
        dto.accounts = accountDTOs;
        dto.debts = debtDTOs;
        dto.predictedRecurring = predictedDTOs;
        dto.futureIncome = futureIncomeDTOs;

        return dto;
    }

    /**
     * Entradas já lançadas no Firefly com data futura (ex: salário/prestação do dia 1)
     * que ainda não aconteceram no mês visualizado — valores reais (não previsão),
     * agrupados por descrição pra identificar de onde vem o "Será Recebido".
     */
    private List<FutureIncomeDTO> buildFutureIncome(List<FireflyClient.Transaction> transactions,
                                                      List<FireflyClient.Transaction> incomeHistory,
                                                      YearMonth viewedMonth) {
        Map<String, BigDecimal> byDescription = new LinkedHashMap<>();

        for (FireflyClient.Transaction tx : transactions) {
            if (tx.date == null || tx.type.equals("transfer")) continue;
            if ("withdrawal".equals(tx.type)) continue;
            if (tx.date.isBefore(today) || tx.date.isEqual(today)) continue;

            byDescription.merge(tx.description, tx.amount, BigDecimal::add);
        }

        List<FutureIncomeDTO> result = byDescription.entrySet().stream()
                .map(e -> new FutureIncomeDTO(e.getKey(), e.getValue(), false))
                .collect(Collectors.toList());

        // Entradas da categoria salário/rendimentos que aconteceram no mês anterior mas
        // ainda não neste (ex: salário alimentação, rendimentos de poupança) — mesma
        // regra do mês-anterior-só usada pros gastos fixos, ver buildPredictedRecurring.
        for (Map.Entry<String, BigDecimal> entry : buildPredictedAmountsByDescription(incomeHistory, viewedMonth, "deposit").entrySet()) {
            result.add(new FutureIncomeDTO(entry.getKey(), entry.getValue(), true));
        }

        result.sort((a, b) -> b.amount.compareTo(a.amount));
        return result;
    }

    /**
     * Despesas da categoria "fixas" que aconteceram no mês ANTERIOR mas ainda não
     * aconteceram no mês visualizado — estimadas pelo valor do mês anterior.
     * Só existem gastos recorrentes nessa categoria (sem pontual), por isso dá pra usar
     * ela direto sem precisar de uma tag separada.
     *
     * Deliberadamente olha só pro mês anterior (não a média de toda a história): uma
     * conta semestral/anual (ex: seguro do carro) só aparece no histórico de vez em
     * quando, e se contássemos qualquer ocorrência passada ela seria "prevista" em todo
     * mês em que não cai de verdade, gerando gasto fantasma a maior parte do tempo.
     * Olhando só o mês anterior, ela só é prevista (incorretamente) no mês seguinte ao
     * em que realmente ocorreu — bem mais raro.
     */
    private List<PredictedExpenseDTO> buildPredictedRecurring(List<FireflyClient.Transaction> fixasHistory, YearMonth viewedMonth) {
        List<PredictedExpenseDTO> predicted = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : buildPredictedAmountsByDescription(fixasHistory, viewedMonth, "withdrawal").entrySet()) {
            predicted.add(new PredictedExpenseDTO(entry.getKey(), entry.getValue()));
        }
        predicted.sort((a, b) -> b.amount.compareTo(a.amount));
        return predicted;
    }

    /**
     * Valores por descrição que aconteceram no mês ANTERIOR mas ainda não no mês
     * visualizado, pro tipo de transação pedido (withdrawal ou deposit) — usada tanto
     * pra prever gastos fixos quanto entradas de salário/rendimentos ainda não lançadas.
     * Ver doc de {@link #buildPredictedRecurring} pro motivo de olhar só o mês anterior.
     */
    private Map<String, BigDecimal> buildPredictedAmountsByDescription(List<FireflyClient.Transaction> history, YearMonth viewedMonth, String txType) {
        if (history == null) return new LinkedHashMap<>();

        // Mês já encerrado: o que aconteceu já aconteceu, não faz sentido "prever" nada
        // nele — só previsão daqui pra frente (mês atual em diante).
        if (viewedMonth.isBefore(YearMonth.from(today))) return new LinkedHashMap<>();

        YearMonth previousMonth = viewedMonth.minusMonths(1);
        Map<String, BigDecimal> lastMonthByDescription = new LinkedHashMap<>();
        Set<String> happenedThisMonth = new HashSet<>();

        for (FireflyClient.Transaction tx : history) {
            if (tx.date == null || tx.description == null || !txType.equals(tx.type)) continue;
            String desc = normalizeRecurringDescription(tx.description);
            YearMonth txMonth = YearMonth.from(tx.date);
            if (txMonth.equals(viewedMonth)) {
                happenedThisMonth.add(desc);
            } else if (txMonth.equals(previousMonth)) {
                lastMonthByDescription.merge(desc, tx.amount.abs(), BigDecimal::add);
            }
        }

        Map<String, BigDecimal> predicted = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : lastMonthByDescription.entrySet()) {
            if (!happenedThisMonth.contains(entry.getKey())) predicted.put(entry.getKey(), entry.getValue());
        }
        return predicted;
    }

    /**
     * Junta descrições que são a mesma conta recorrente escrita diferente — confirmado
     * com o usuário que "internet" e "internet casa" são a mesma fatura (2026-10-30), e
     * que "seguro vida" e "seguro vida - financiamento casa" também são a mesma conta,
     * só com nome inconsistente mês a mês (2026-10-31) — sem isso, a conta já paga num
     * mês com uma grafia não "cancela" a previsão feita a partir da grafia usada no mês
     * anterior, e ela acaba contada duas vezes (uma real, uma prevista).
     * Também normaliza por case (ex: "salario" vs "Salario" em meses diferentes), pelo
     * mesmo motivo — descoberto com "Salario"/"salario" reaparecendo como previsão
     * fantasma em outubro (2026-10-31).
     */
    private String normalizeRecurringDescription(String description) {
        if (description == null) return null;
        String normalized = description.trim().toLowerCase(new Locale("pt", "BR"));
        if ("internet casa".equals(normalized)) return "internet";
        if ("seguro vida - financiamento casa".equals(normalized)) return "seguro vida";
        return normalized;
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

        // Orçamentos sem nenhuma transação tagueada ainda este mês (ex: mês futuro que
        // ainda não começou) não aparecem no loop acima, mas existem de verdade no
        // Firefly com um valor orçado — sem isso, "Livre pra Gastar" quebra ao olhar um
        // mês sem gasto variável lançado (descoberto em 2026-10-31).
        budgets.forEach((name, budget) -> {
            if (!budgetMap.containsKey(name) && budget.budgeted.compareTo(BigDecimal.ZERO) > 0) {
                BudgetDTO dto = new BudgetDTO(name);
                dto.inverse = name.toLowerCase().contains("reserva") || name.toLowerCase().contains("invest");
                budgetMap.put(name, dto);
            }
        });

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
