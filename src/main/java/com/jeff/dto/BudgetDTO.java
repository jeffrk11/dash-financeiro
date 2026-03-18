package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@RegisterForReflection
public class BudgetDTO {
    public String name;
    public BigDecimal total;
    public BigDecimal spent;
    public BigDecimal future;
    public BigDecimal available;

    private static final DecimalFormat currencyFormatter = 
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public BudgetDTO() {}

    public BudgetDTO(String name) {
        this.name = name;
        this.total = BigDecimal.ZERO;
        this.spent = BigDecimal.ZERO;
        this.future = BigDecimal.ZERO;
        this.available = total;
    }

    public BudgetDTO(String name, BigDecimal total, BigDecimal spent, BigDecimal future, BigDecimal available) {
        this.name = name;
        this.total = total;
        this.spent = spent;
        this.future = future;
        this.available = available;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getTotalFormatted() {
        return total != null ? currencyFormatter.format(total) : "0,00";
    }

    public BigDecimal getSpent() {
        return spent;
    }

    public void setSpent(BigDecimal spent) {
        this.spent = spent;
    }

    public String getSpentFormatted() {
        return spent != null ? currencyFormatter.format(spent) : "0,00";
    }

    public BigDecimal getFuture() {
        return future;
    }

    public void setFuture(BigDecimal future) {
        this.future = future;
    }

    public String getFutureFormatted() {
        return future != null ? currencyFormatter.format(future) : "0,00";
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public void setAvailable(BigDecimal available) {
        this.available = available;
    }

    public String getAvailableFormatted() {
        return available != null ? currencyFormatter.format(available) : "0,00";
    }

    public double getSpentPercentage() {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0;
        return spent.doubleValue() / total.doubleValue() * 100;
    }

    public double getFuturePercentage() {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0;
        return future.doubleValue() / total.doubleValue() * 100;
    }

    public double getAvailablePercentage() {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0;
        return available.doubleValue() / total.doubleValue() * 100;
    }
}

