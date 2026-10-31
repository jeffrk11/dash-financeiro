package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Uma entrada já lançada no Firefly com data futura (ex: salário/prestação do dia 1)
 * que ainda não aconteceu neste mês — valor real, não é uma estimativa.
 */
@RegisterForReflection
public class FutureIncomeDTO {
    public String name;
    public BigDecimal amount;
    /** true quando o valor é uma estimativa (categoria salário/rendimentos, mês anterior), não um lançamento real. */
    public boolean predicted;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public FutureIncomeDTO() {}

    public FutureIncomeDTO(String name, BigDecimal amount, boolean predicted) {
        this.name = name;
        this.amount = amount;
        this.predicted = predicted;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean getPredicted() {
        return predicted;
    }

    public String getAmountFormatted() {
        String formatted = amount != null ? "€" + currencyFormatter.format(amount) : "€0,00";
        return predicted ? "~" + formatted : formatted;
    }
}
