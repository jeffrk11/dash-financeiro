package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@RegisterForReflection
public class DebtDTO {
    public String name;
    /** Quanto ainda falta pagar, sempre como valor positivo (independente do sinal que o Firefly usa internamente). */
    public BigDecimal owed;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public DebtDTO() {}

    public DebtDTO(String name, BigDecimal owed) {
        this.name = name;
        this.owed = owed != null ? owed.abs() : BigDecimal.ZERO;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getOwed() {
        return owed;
    }

    public String getOwedFormatted() {
        return "€" + currencyFormatter.format(owed);
    }
}
