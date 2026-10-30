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
    public SankeyDTO sankey;
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
     * O número que realmente importa: saldo das contas do dia a dia, menos o que já se
     * sabe que vai sair até o fim do mês (contas recorrentes, parcelas) e o que se espera
     * que saia mas ainda não tem valor/data exatos (média da categoria "fixas"), mais o
     * que já se sabe que vai entrar (reembolsos, etc. — não o salário, só lançado dia 1).
     */
    public BigDecimal getSafeToSpend() {
        if (summary == null) return getSpendableBalance();
        BigDecimal spentFuture = summary.spentFuture != null ? summary.spentFuture : BigDecimal.ZERO;
        BigDecimal receivedFuture = summary.receivedFuture != null ? summary.receivedFuture : BigDecimal.ZERO;
        return getSpendableBalance().subtract(spentFuture).subtract(getTotalPredictedRecurring()).add(receivedFuture);
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

    private BigDecimal getFlowScale() {
        BigDecimal scale = getIncomeTotal().max(getExpenseTotal()).max(getSpendableBalance());
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
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal withdraw = BigDecimal.ZERO;

        for (MonthlyFinancialData month : monthlyFinancialData){
            deposit  = deposit.add(month.getReceivedNow());//.add(month.getReceivedFuture());
            withdraw = withdraw.add(month.getSpentNow());//.add(month.getSpentFuture());
        }

        return deposit.subtract(withdraw);
    }
}

