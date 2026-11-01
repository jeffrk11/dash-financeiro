package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@RegisterForReflection
public class AccountDTO {
    public String name;
    public BigDecimal balance;
    /** Dinheiro guardado de propósito (poupança/investimento) — não entra na conta de "livre pra gastar". */
    public boolean reserve;
    /** Limite (cartão de crédito) guardado no campo virtual_balance do Firefly, por falta de campo nativo pra isso. */
    public BigDecimal limit;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public AccountDTO() {}

    public AccountDTO(String name, BigDecimal balance) {
        this.name = name;
        this.balance = balance;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getBalanceFormatted() {
        if (balance == null) return "0,00";
        String sign = balance.compareTo(BigDecimal.ZERO) < 0 ? "-" : "";
        return sign + "€" + currencyFormatter.format(balance.abs());
    }

    public boolean getIsNegative() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean getReserve() {
        return reserve;
    }

    public void setReserve(boolean reserve) {
        this.reserve = reserve;
    }

    public boolean getHasLimit() {
        return limit != null && limit.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getLimitFormatted() {
        return limit != null ? "€" + currencyFormatter.format(limit) : "€0,00";
    }

    /** Quanto já está "gasto" no cartão (saldo negativo da conta), sempre positivo. */
    public BigDecimal getOwed() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) < 0 ? balance.abs() : BigDecimal.ZERO;
    }

    public String getOwedFormatted() {
        return "€" + currencyFormatter.format(getOwed());
    }

    /** Limite menos o quanto já está "gasto" (saldo negativo da conta). */
    public BigDecimal getAvailable() {
        if (limit == null) return BigDecimal.ZERO;
        BigDecimal available = limit.subtract(getOwed());
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }

    public String getAvailableFormatted() {
        return "€" + currencyFormatter.format(getAvailable());
    }

    /** % do limite já usado, pra desenhar a barra (0-100, nunca passa de 100 mesmo se estourar o limite). */
    public double getUsedPercentage() {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) return 0;
        double pct = getOwed().doubleValue() / limit.doubleValue() * 100;
        return Math.min(100, Math.max(0, pct));
    }
}
