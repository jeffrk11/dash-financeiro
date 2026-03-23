package com.jeff.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class MonthlyFinancialData {
        final String month;        // "2026-01"
        BigDecimal receivedNow;
        BigDecimal receivedFuture;
        BigDecimal spentNow;
        BigDecimal spentFuture;

        public MonthlyFinancialData(String month) {
            this.month = month;
            this.receivedNow = new BigDecimal(0);
            this.receivedFuture = new BigDecimal(0);
            this.spentNow = new BigDecimal(0);
            this.spentFuture = new BigDecimal(0);
        }

    public void buildMonthlyFinancialData(List<FireflyClient.Transaction> transactions) {
        LocalDate today = LocalDate.now();
        for (FireflyClient.Transaction tx : transactions) {
            if (tx.date == null) continue;

            if (tx.amount == null) continue;

            if (tx.type.equals("deposit")) {
                // Entrada
                if (tx.date.isBefore(today)) {
                    this.receivedNow = receivedNow.add(tx.amount);
                } else {
                    receivedFuture = receivedFuture.add(tx.amount);
                }
            } else if (tx.type.equals("withdrawal")) {
                // Saída
                if (tx.date.isBefore(today)) {
                    spentNow = spentNow.add(tx.amount.abs());
                } else {
                    spentFuture = spentFuture.add(tx.amount.abs());
                }
            }
        }

    }

    public String getMonth() {
        return month;
    }

    public BigDecimal getReceivedNow() {
        return receivedNow;
    }

    public void setReceivedNow(BigDecimal receivedNow) {
        this.receivedNow = receivedNow;
    }

    public BigDecimal getReceivedFuture() {
        return receivedFuture;
    }

    public void setReceivedFuture(BigDecimal receivedFuture) {
        this.receivedFuture = receivedFuture;
    }

    public BigDecimal getSpentNow() {
        return spentNow;
    }

    public void setSpentNow(BigDecimal spentNow) {
        this.spentNow = spentNow;
    }

    public BigDecimal getSpentFuture() {
        return spentFuture;
    }

    public void setSpentFuture(BigDecimal spentFuture) {
        this.spentFuture = spentFuture;
    }
}