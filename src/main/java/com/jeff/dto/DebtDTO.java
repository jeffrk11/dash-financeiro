package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@RegisterForReflection
public class DebtDTO {
    public String name;
    /** Quanto ainda falta pagar, sempre como valor positivo (independente do sinal que o Firefly usa internamente). */
    public BigDecimal owed;
    /**
     * Saldo devedor no fim de cada mês do ano (Jan-Dez), sempre positivo — null pros
     * meses antes da conta existir (opening_balance_date). Alimenta o gráfico anual da
     * dívida na Visão Anual.
     */
    public List<BigDecimal> monthlyBalances;
    /** Limite (cartão de crédito como passivo): mesmo truque do virtual_balance usado nas contas asset. */
    public BigDecimal limit;

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

    public List<BigDecimal> getMonthlyBalances() {
        return monthlyBalances;
    }

    public void setMonthlyBalances(List<BigDecimal> monthlyBalances) {
        this.monthlyBalances = monthlyBalances;
    }

    public boolean getHasLimit() {
        return limit != null && limit.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getLimitFormatted() {
        return limit != null ? "€" + currencyFormatter.format(limit) : "€0,00";
    }

    public BigDecimal getAvailable() {
        if (limit == null) return BigDecimal.ZERO;
        BigDecimal available = limit.subtract(owed);
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }

    public String getAvailableFormatted() {
        return "€" + currencyFormatter.format(getAvailable());
    }

    public double getUsedPercentage() {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) return 0;
        double pct = owed.doubleValue() / limit.doubleValue() * 100;
        return Math.min(100, Math.max(0, pct));
    }
}
