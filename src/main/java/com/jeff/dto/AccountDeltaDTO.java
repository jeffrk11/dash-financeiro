package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Variação líquida de saldo de uma conta real (não a "Cash account" virtual) num mês —
 * tooltip do "Saldo Projetado do Mês", pra mostrar pra onde o superávit/déficit do mês
 * realmente foi distribuído entre as contas do usuário.
 */
@RegisterForReflection
public class AccountDeltaDTO {
    public String name;
    public BigDecimal delta;

    private static final DecimalFormat currencyFormatter =
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public AccountDeltaDTO() {}

    public AccountDeltaDTO(String name, BigDecimal delta) {
        this.name = name;
        this.delta = delta;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public boolean isPositive() {
        return delta != null && delta.signum() >= 0;
    }

    public String getDeltaFormatted() {
        BigDecimal value = delta != null ? delta : BigDecimal.ZERO;
        String sign = value.signum() >= 0 ? "+" : "−";
        return sign + "€" + currencyFormatter.format(value.abs());
    }
}
