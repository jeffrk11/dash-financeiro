package com.jeff.dto;

import com.jeff.client.MonthlyFinancialData;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@RegisterForReflection
public class DashboardDTO {
    public SummaryDTO summary;
    public List<BudgetDTO> budgets;
    public List<AccountDTO> accounts;
    public List<DebtDTO> debts;
    public List<PredictedExpenseDTO> predictedRecurring;
    public List<FutureIncomeDTO> futureIncome;
    public List<FutureIncomeDTO> investmentBreakdown;
    public List<AccountDeltaDTO> accountDeltas;
    public SankeyDTO sankey;
    public SankeyDTO sankeyAnnual;
    public List<MonthlyFinancialData> monthlyFinancialData;
    public BigDecimal yearProjectedBalance;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public DashboardDTO() {
        yearProjectedBalance = BigDecimal.ZERO;
    }

    public SummaryDTO getSummary() {
        return summary;
    }

    public void setSummary(SummaryDTO summary) {
        this.summary = summary;
    }

    public List<BudgetDTO> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<BudgetDTO> budgets) {
        this.budgets = budgets;
    }

    public List<AccountDTO> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountDTO> accounts) {
        this.accounts = accounts;
    }

    public List<DebtDTO> getDebts() {
        return debts;
    }

    public void setDebts(List<DebtDTO> debts) {
        this.debts = debts;
    }

    public boolean getHasDebts() {
        return debts != null && !debts.isEmpty();
    }

    public List<PredictedExpenseDTO> getPredictedRecurring() {
        return predictedRecurring;
    }

    public void setPredictedRecurring(List<PredictedExpenseDTO> predictedRecurring) {
        this.predictedRecurring = predictedRecurring;
    }

    public boolean getHasPredictedRecurring() {
        return predictedRecurring != null && !predictedRecurring.isEmpty();
    }

    public BigDecimal getTotalPredictedRecurring() {
        if (predictedRecurring == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (PredictedExpenseDTO p : predictedRecurring) {
            if (p.amount != null) total = total.add(p.amount);
        }
        return total;
    }

    public String getTotalPredictedRecurringFormatted() {
        return "€" + currencyFormatter.format(getTotalPredictedRecurring());
    }

    public List<FutureIncomeDTO> getFutureIncome() {
        return futureIncome;
    }

    public void setFutureIncome(List<FutureIncomeDTO> futureIncome) {
        this.futureIncome = futureIncome;
    }

    public boolean getHasFutureIncome() {
        return futureIncome != null && !futureIncome.isEmpty();
    }

    public List<FutureIncomeDTO> getInvestmentBreakdown() {
        return investmentBreakdown;
    }

    public void setInvestmentBreakdown(List<FutureIncomeDTO> investmentBreakdown) {
        this.investmentBreakdown = investmentBreakdown;
    }

    public List<AccountDeltaDTO> getAccountDeltas() {
        return accountDeltas;
    }

    public void setAccountDeltas(List<AccountDeltaDTO> accountDeltas) {
        this.accountDeltas = accountDeltas;
    }

    public boolean getHasAccountDeltas() {
        return accountDeltas != null && !accountDeltas.isEmpty();
    }

    public boolean getHasInvestmentBreakdown() {
        return investmentBreakdown != null && !investmentBreakdown.isEmpty();
    }

    public BigDecimal getTotalDebt() {
        if (debts == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DebtDTO d : debts) {
            if (d.owed != null) total = total.add(d.owed);
        }
        return total;
    }

    public String getTotalDebtFormatted() {
        return "€" + currencyFormatter.format(getTotalDebt());
    }

    public BigDecimal getTotalBalance() {
        if (accounts == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (AccountDTO a : accounts) {
            if (a.balance != null) total = total.add(a.balance);
        }
        return total;
    }

    public String getTotalBalanceFormatted() {
        BigDecimal total = getTotalBalance();
        String sign = total.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        return sign + "€" + currencyFormatter.format(total.abs());
    }

    /** Saldo só das contas do dia a dia — exclui poupança/investimento/reserva. */
    public BigDecimal getSpendableBalance() {
        if (accounts == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (AccountDTO a : accounts) {
            if (!a.reserve && a.balance != null) total = total.add(a.balance);
        }
        return total;
    }

    public String getSpendableBalanceFormatted() {
        return formatSigned(getSpendableBalance());
    }

    /**
     * O número que realmente importa: quanto ainda cabe gastar no orçamento de
     * "variáveis" este mês. Deliberadamente NÃO é saldo de conta — o usuário joga todo
     * o dinheiro na poupança e vai puxando de lá conforme precisa, então saldo de conta
     * "disponível" sempre ia mostrar milhares de euros, o que não significa nada como
     * sinal de "posso gastar agora" (2026-10-31). As fixas já têm orçamento próprio
     * (budget-fixas) e teoricamente já estão cobertas, por isso ficam de fora daqui —
     * esse número é só o teto de gasto variável menos o que já foi gasto/está agendado.
     * Não clampa em zero (ao contrário do "Disponível" mostrado no card do orçamento):
     * estourar o orçamento de variáveis deve aparecer como negativo, não sumir em €0.
     */
    public BigDecimal getSafeToSpend() {
        BudgetDTO variable = getVariableBudget();
        if (variable == null) return BigDecimal.ZERO;
        BigDecimal total = variable.total != null ? variable.total : BigDecimal.ZERO;
        BigDecimal spent = variable.spent != null ? variable.spent : BigDecimal.ZERO;
        BigDecimal future = variable.future != null ? variable.future : BigDecimal.ZERO;
        return total.subtract(spent).subtract(future);
    }

    public BudgetDTO getVariableBudget() {
        if (budgets == null) return null;
        for (BudgetDTO b : budgets) {
            if ("budget-variaveis".equals(b.name)) return b;
        }
        return null;
    }

    public String getSafeToSpendFormatted() {
        return formatSigned(getSafeToSpend());
    }

    public boolean getSafeToSpendIsNegative() {
        return getSafeToSpend().compareTo(BigDecimal.ZERO) < 0;
    }

    /** % do saldo do dia a dia já comprometido com gastos futuros conhecidos (barra, igual ao orçamento). */
    public double getKnownExpensePercentage() {
        BigDecimal base = getSpendableBalance();
        if (base.compareTo(BigDecimal.ZERO) <= 0 || summary == null || summary.spentFuture == null) return 0;
        return summary.spentFuture.doubleValue() / base.doubleValue() * 100;
    }

    /** % do saldo comprometido com a estimativa de fixas que ainda não chegaram. */
    public double getPredictedExpensePercentage() {
        BigDecimal base = getSpendableBalance();
        if (base.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return getTotalPredictedRecurring().doubleValue() / base.doubleValue() * 100;
    }

    /** % do saldo que sobra livre depois dos dois acima. */
    public double getFreePercentage() {
        double free = 100 - getKnownExpensePercentage() - getPredictedExpensePercentage();
        return free < 0 ? 0 : free;
    }

    /**
     * Entradas, saídas e saldo disponível agora, todos na mesma escala — pra dar pra
     * comparar visualmente "quanto entra/sai esse mês" com "quanto eu realmente tenho
     * na mão agora", lado a lado, em vez de dois números soltos que parecem contradizer.
     */
    public BigDecimal getIncomeTotal() {
        if (summary == null) return BigDecimal.ZERO;
        BigDecimal now = summary.receivedNow != null ? summary.receivedNow : BigDecimal.ZERO;
        BigDecimal future = summary.receivedFuture != null ? summary.receivedFuture : BigDecimal.ZERO;
        return now.add(future);
    }

    public BigDecimal getExpenseTotal() {
        if (summary == null) return BigDecimal.ZERO;
        BigDecimal now = summary.spentNow != null ? summary.spentNow : BigDecimal.ZERO;
        BigDecimal future = summary.spentFuture != null ? summary.spentFuture : BigDecimal.ZERO;
        return now.add(future);
    }

    /** Saídas conhecidas + a estimativa de fixas pendentes — o que a barra de Saídas representa inteira. */
    public BigDecimal getExpenseTotalWithPredicted() {
        return getExpenseTotal().add(getTotalPredictedRecurring());
    }

    private BigDecimal getFlowScale() {
        BigDecimal scale = getIncomeTotal().max(getExpenseTotalWithPredicted()).max(getSpendableBalance());
        return scale.compareTo(BigDecimal.ZERO) > 0 ? scale : BigDecimal.ONE;
    }

    public double getIncomeNowPct() {
        return pctOfScale(summary != null ? summary.receivedNow : null);
    }

    public double getIncomeFuturePct() {
        return pctOfScale(summary != null ? summary.receivedFuture : null);
    }

    public double getExpenseNowPct() {
        return pctOfScale(summary != null ? summary.spentNow : null);
    }

    public double getExpenseFuturePct() {
        return pctOfScale(summary != null ? summary.spentFuture : null);
    }

    public double getExpensePredictedPct() {
        return pctOfScale(getTotalPredictedRecurring());
    }

    public double getBalanceBarPct() {
        return pctOfScale(getSpendableBalance());
    }

    private double pctOfScale(BigDecimal value) {
        if (value == null) return 0;
        double p = value.doubleValue() / getFlowScale().doubleValue() * 100;
        return p < 0 ? 0 : p;
    }

    private String formatSigned(BigDecimal value) {
        String sign = value.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        return sign + "€" + currencyFormatter.format(value.abs());
    }

    public BigDecimal getYearProjectedBalance() {
        return yearProjectedBalance;
    }

    public String getYearProjectedBalanceFormatted() {
        if (yearProjectedBalance == null) return "0,00";
        String sign = yearProjectedBalance.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        return sign + "€" + currencyFormatter.format(yearProjectedBalance.abs());
    }

    public BigDecimal getTotalBudgeted() {
        return sumBudgets(BudgetDTO::getTotal);
    }

    public String getTotalBudgetedFormatted() {
        return "€" + currencyFormatter.format(getTotalBudgeted());
    }

    public BigDecimal getTotalSpent() {
        return sumBudgets(BudgetDTO::getSpent);
    }

    public String getTotalSpentFormatted() {
        return "€" + currencyFormatter.format(getTotalSpent());
    }

    /** Quanto ainda sobra pra gastar nos orçamentos definidos, sem contar o que já ficou pra trás (gasto + futuro já lançado). */
    public BigDecimal getTotalFree() {
        BigDecimal free = getTotalBudgeted().subtract(getTotalSpent()).subtract(sumBudgets(BudgetDTO::getFuture));
        return free.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : free;
    }

    public String getTotalFreeFormatted() {
        return "€" + currencyFormatter.format(getTotalFree());
    }

    /** "ok" / "warning" / "over", com base no gasto somado de todos os orçamentos vs. o total orçado. */
    public String getTotalStatus() {
        BigDecimal budgeted = getTotalBudgeted();
        if (budgeted.compareTo(BigDecimal.ZERO) == 0) return "ok";
        double pct = getTotalSpent().doubleValue() / budgeted.doubleValue() * 100;
        if (pct > 100) return "over";
        if (pct >= 90) return "warning";
        return "ok";
    }

    private BigDecimal sumBudgets(java.util.function.Function<BudgetDTO, BigDecimal> field) {
        if (budgets == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (BudgetDTO b : budgets) {
            BigDecimal v = field.apply(b);
            if (v != null) total = total.add(v);
        }
        return total;
    }

    public BigDecimal calculateYearBalance() {
        return getYearTotalReceived().subtract(getYearTotalSpent());
    }

    /** Soma de tudo recebido de verdade no ano (mesma base do calculateYearBalance, sem futuro/previsão). */
    public BigDecimal getYearTotalReceived() {
        BigDecimal total = BigDecimal.ZERO;
        if (monthlyFinancialData == null) return total;
        for (MonthlyFinancialData month : monthlyFinancialData) {
            total = total.add(month.getReceivedNow());
        }
        return total;
    }

    public String getYearTotalReceivedFormatted() {
        return "€" + currencyFormatter.format(getYearTotalReceived());
    }

    /** Soma de tudo gasto de verdade no ano (mesma base do calculateYearBalance, sem futuro/previsão). */
    public BigDecimal getYearTotalSpent() {
        BigDecimal total = BigDecimal.ZERO;
        if (monthlyFinancialData == null) return total;
        for (MonthlyFinancialData month : monthlyFinancialData) {
            total = total.add(month.getSpentNow());
        }
        return total;
    }

    public String getYearTotalSpentFormatted() {
        return "€" + currencyFormatter.format(getYearTotalSpent());
    }

    public BigDecimal getYearSavedReserve() {
        if (monthlyFinancialData == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinancialData month : monthlyFinancialData) total = total.add(month.getSavedReserve());
        return total;
    }

    public String getYearSavedReserveFormatted() {
        return formatSigned(getYearSavedReserve());
    }

    public BigDecimal getYearSavedInvest() {
        if (monthlyFinancialData == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (MonthlyFinancialData month : monthlyFinancialData) total = total.add(month.getSavedInvest());
        return total;
    }

    public String getYearSavedInvestFormatted() {
        return formatSigned(getYearSavedInvest());
    }
}

