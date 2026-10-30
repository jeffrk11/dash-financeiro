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
}
