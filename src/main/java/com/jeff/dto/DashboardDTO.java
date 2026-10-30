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

