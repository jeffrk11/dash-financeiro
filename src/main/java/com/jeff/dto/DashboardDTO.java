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

    public BigDecimal getYearProjectedBalance() {
        return yearProjectedBalance;
    }

    public String getYearProjectedBalanceFormatted() {
        if (yearProjectedBalance == null) return "0,00";
        String sign = yearProjectedBalance.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        return sign + "R$ " + currencyFormatter.format(yearProjectedBalance.abs());
    }

    public BigDecimal getTotalBudgeted() {
        return sumBudgets(BudgetDTO::getTotal);
    }

    public String getTotalBudgetedFormatted() {
        return "R$ " + currencyFormatter.format(getTotalBudgeted());
    }

    public BigDecimal getTotalSpent() {
        return sumBudgets(BudgetDTO::getSpent);
    }

    public String getTotalSpentFormatted() {
        return "R$ " + currencyFormatter.format(getTotalSpent());
    }

    /** Quanto ainda sobra pra gastar nos orçamentos definidos, sem contar o que já ficou pra trás (gasto + futuro já lançado). */
    public BigDecimal getTotalFree() {
        BigDecimal free = getTotalBudgeted().subtract(getTotalSpent()).subtract(sumBudgets(BudgetDTO::getFuture));
        return free.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : free;
    }

    public String getTotalFreeFormatted() {
        return "R$ " + currencyFormatter.format(getTotalFree());
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

