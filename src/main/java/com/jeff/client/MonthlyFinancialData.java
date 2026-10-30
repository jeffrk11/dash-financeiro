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
        /** Quanto foi pra poupança/investimento este mês (líquido — já descontando o que voltou). */
        BigDecimal saved;

        public MonthlyFinancialData(String month) {
            this.month = month;
            this.receivedNow = new BigDecimal(0);
            this.receivedFuture = new BigDecimal(0);
            this.spentNow = new BigDecimal(0);
            this.spentFuture = new BigDecimal(0);
            this.saved = new BigDecimal(0);
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
            } else if (tx.type.equals("transfer") && tx.tags != null) {
                // Mesma convenção de tag usada nos orçamentos "reserva"/"investir":
                // "saving" = indo pra poupança/investimento, "expending" = voltando de lá.
                if (tx.tags.contains("saving")) {
                    saved = saved.add(tx.amount.abs());
                } else if (tx.tags.contains("expending")) {
                    saved = saved.subtract(tx.amount.abs());
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

    public BigDecimal getSaved() {
        return saved;
    }

    public void setSaved(BigDecimal saved) {
        this.saved = saved;
    }
}