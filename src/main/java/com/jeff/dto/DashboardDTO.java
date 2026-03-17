package com.jeff.dto;

import java.util.List;

public class DashboardDTO {
    public SummaryDTO summary;
    public List<BudgetDTO> budgets;
    public SankeyDTO sankey;

    public DashboardDTO() {}

    public DashboardDTO(SummaryDTO summary, List<BudgetDTO> budgets) {
        this.summary = summary;
        this.budgets = budgets;
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
}

