package com.jeff.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@RegisterForReflection
public class SummaryDTO {
    public BigDecimal spentNow;
    public BigDecimal receivedNow;
    public BigDecimal spentFuture;
    public BigDecimal receivedFuture;
    public BigDecimal projectedBalance;

    private static final DecimalFormat currencyFormatter = 
        new DecimalFormat("0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    public SummaryDTO() {}

    public BigDecimal getSpentNow() {
        return spentNow;
    }

    public void setSpentNow(BigDecimal spentNow) {
        this.spentNow = spentNow;
    }

    public String getSpentNowFormatted() {
        return spentNow != null ? currencyFormatter.format(spentNow) : "0,00";
    }

    public BigDecimal getReceivedNow() {
        return receivedNow;
    }

    public void setReceivedNow(BigDecimal receivedNow) {
        this.receivedNow = receivedNow;
    }

    public String getReceivedNowFormatted() {
        return receivedNow != null ? currencyFormatter.format(receivedNow) : "0,00";
    }

    public BigDecimal getSpentFuture() {
        return spentFuture;
    }

    public void setSpentFuture(BigDecimal spentFuture) {
        this.spentFuture = spentFuture;
    }

    public String getSpentFutureFormatted() {
        return spentFuture != null ? currencyFormatter.format(spentFuture) : "0,00";
    }

    public BigDecimal getReceivedFuture() {
        return receivedFuture;
    }

    public void setReceivedFuture(BigDecimal receivedFuture) {
        this.receivedFuture = receivedFuture;
    }

    public String getReceivedFutureFormatted() {
        return receivedFuture != null ? currencyFormatter.format(receivedFuture) : "0,00";
    }

    public BigDecimal getProjectedBalance() {
        return projectedBalance;
    }

    public void setProjectedBalance(BigDecimal projectedBalance) {
        this.projectedBalance = projectedBalance;
    }

    public String getProjectedBalanceFormatted() {
        return projectedBalance != null ? currencyFormatter.format(projectedBalance.abs()) : "0,00";
    }

    public boolean isBalancePositive() {
        return projectedBalance != null && projectedBalance.compareTo(BigDecimal.ZERO) >= 0;
    }
}

