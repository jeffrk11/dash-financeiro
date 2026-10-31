package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Uma despesa recorrente (tag "recorrente") que ainda não aconteceu neste mês, com o
 * valor estimado pela média histórica — não é um valor real, é uma previsão.
 */
@RegisterForReflection
public class PredictedExpenseDTO {
    public String name;
    public BigDecimal amount;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public PredictedExpenseDTO() {}

    public PredictedExpenseDTO(String name, BigDecimal amount) {
        this.name = name;
        this.amount = amount;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAmountFormatted() {
        return amount != null ? "€" + currencyFormatter.format(amount) : "€0,00";
    }
}
