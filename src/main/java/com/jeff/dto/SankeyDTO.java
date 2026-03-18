package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
public class SankeyDTO {
    public BigDecimal totalIncome;
    public List<CategoryDTO> incomeCategories;  // Categorias de entrada (esquerda)
    public List<CategoryDTO> expenseCategories; // Categorias de despesa (direita)

    public SankeyDTO() {}
    public SankeyDTO(BigDecimal totalIncome, List<CategoryDTO> incomeCategories, List<CategoryDTO> expenseCategories) {
        this.totalIncome = totalIncome;
        this.incomeCategories = incomeCategories;
        this.expenseCategories = expenseCategories;
    }

    public String getTotalIncomeFormatted() {
        return String.format("%.2f", totalIncome);
    }
}
