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
        /** Fatia de "saved" que foi pro budget-reserva (poupança). */
        BigDecimal savedReserve;
        /** Fatia de "saved" que foi pro budget-investir (investimento). */
        BigDecimal savedInvest;

        public MonthlyFinancialData(String month) {
            this.month = month;
            this.receivedNow = new BigDecimal(0);
            this.receivedFuture = new BigDecimal(0);
            this.spentNow = new BigDecimal(0);
            this.spentFuture = new BigDecimal(0);
            this.saved = new BigDecimal(0);
            this.savedReserve = new BigDecimal(0);
            this.savedInvest = new BigDecimal(0);
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
                // Investimento externo (corretora/cripto) não é uma conta rastreada no
                // Firefly, então sai como "withdrawal" pro Cash account, não como
                // "transfer" interno igual a poupança — ainda assim conta como investido
                // (descoberto 2026-10-31: Investido aparecia sempre €0,00 por só olhar
                // transfers, igual a reserva).
                if (tx.tags != null && tx.tags.contains("budget-investir")) {
                    savedInvest = savedInvest.add(tx.amount.abs());
                    saved = saved.add(tx.amount.abs());
                }
            } else if (tx.type.equals("transfer") && tx.tags != null) {
                // Mesma convenção de tag usada nos orçamentos "reserva"/"investir":
                // "saving" = indo pra poupança/investimento, "expending" = voltando de lá,
                // sempre junto com a tag do orçamento (budget-reserva ou budget-investir)
                // que diz pra qual das duas fatias essa movimentação conta.
                BigDecimal delta = BigDecimal.ZERO;
                if (tx.tags.contains("saving")) {
                    delta = tx.amount.abs();
                } else if (tx.tags.contains("expending")) {
                    delta = tx.amount.abs().negate();
                }
                saved = saved.add(delta);
                if (tx.tags.contains("budget-reserva")) {
                    savedReserve = savedReserve.add(delta);
                } else if (tx.tags.contains("budget-investir")) {
                    savedInvest = savedInvest.add(delta);
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

    public BigDecimal getSavedReserve() {
        return savedReserve;
    }

    public void setSavedReserve(BigDecimal savedReserve) {
        this.savedReserve = savedReserve;
    }

    public BigDecimal getSavedInvest() {
        return savedInvest;
    }

    public void setSavedInvest(BigDecimal savedInvest) {
        this.savedInvest = savedInvest;
    }
}