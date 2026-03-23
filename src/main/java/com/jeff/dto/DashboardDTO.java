package com.jeff.dto;

import com.jeff.client.MonthlyFinancialData;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public class DashboardDTO {
    public SummaryDTO summary;
    public List<BudgetDTO> budgets;
    public SankeyDTO sankey;
    public List<MonthlyFinancialData> monthlyFinancialData;
    public BigDecimal yearProjectedBalance;

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

