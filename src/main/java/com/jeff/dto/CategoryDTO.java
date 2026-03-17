package com.jeff.dto;

import java.math.BigDecimal;

public class CategoryDTO {
    public String name;
    public BigDecimal amount;

    public CategoryDTO() {}
    public CategoryDTO(String name, BigDecimal amount) {
        this.name = name;
        this.amount = amount;
    }

    public String getAmountFormatted() {
        return String.format("%.2f", amount);
    }
}

