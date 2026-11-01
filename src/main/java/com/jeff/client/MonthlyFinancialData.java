package com.jeff.client;

import com.jeff.dto.AccountDeltaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MonthlyFinancialData {
        final YearMonth yearMonth;
        BigDecimal receivedNow;
        BigDecimal receivedFuture;
        BigDecimal spentNow;
        BigDecimal spentFuture;
        /** Líquido indo pro budget-reserva este mês (poupança — já descontando o que voltou). */
        BigDecimal savedReserve;
        /** Líquido indo pro budget-investir este mês (investimento). */
        BigDecimal savedInvest;
        /** Previsão de fixas que ainda não aconteceram neste mês (0 em mês já encerrado). */
        BigDecimal predictedFixas;
        /** Previsão de salário/rendimentos que ainda não aconteceram neste mês. */
        BigDecimal predictedIncome;
        /** Variação líquida de saldo por conta real, pro tooltip do "Saldo Projetado" na Visão Anual. */
        List<AccountDeltaDTO> accountDeltas;

        public MonthlyFinancialData(YearMonth yearMonth) {
            this.yearMonth = yearMonth;
            this.receivedNow = new BigDecimal(0);
            this.receivedFuture = new BigDecimal(0);
            this.spentNow = new BigDecimal(0);
            this.spentFuture = new BigDecimal(0);
            this.savedReserve = new BigDecimal(0);
            this.savedInvest = new BigDecimal(0);
            this.predictedFixas = new BigDecimal(0);
            this.predictedIncome = new BigDecimal(0);
            this.accountDeltas = new ArrayList<>();
        }

        private static final String CASH_ACCOUNT_NAME = "Cash account";

    public void buildMonthlyFinancialData(List<FireflyClient.Transaction> transactions) {
        LocalDate today = LocalDate.now();
        for (FireflyClient.Transaction tx : transactions) {
            if (tx.date == null) continue;

            if (tx.amount == null) continue;

            boolean isPast = tx.date.isBefore(today) || tx.date.isEqual(today);

            if (tx.type.equals("deposit")) {
                // Entrada
                if (isPast) {
                    this.receivedNow = receivedNow.add(tx.amount);
                } else {
                    receivedFuture = receivedFuture.add(tx.amount);
                }
            } else if (tx.type.equals("withdrawal")) {
                // Saída
                if (isPast) {
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
                if (tx.tags.contains("budget-reserva")) {
                    savedReserve = savedReserve.add(delta);
                } else if (tx.tags.contains("budget-investir")) {
                    savedInvest = savedInvest.add(delta);
                }
            }
        }

        this.accountDeltas = buildAccountDeltas(transactions);
    }

    /**
     * Mesma lógica usada no tooltip do card mensal (ver DashboardMapper.buildAccountDeltas),
     * aqui repetida por mês pra alimentar o tooltip da linha "Saldo Projetado" na Visão Anual.
     */
    private List<AccountDeltaDTO> buildAccountDeltas(List<FireflyClient.Transaction> transactions) {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        for (FireflyClient.Transaction tx : transactions) {
            if (tx.amount == null || tx.sourceName == null || tx.destinationName == null) continue;
            net.merge(tx.sourceName, tx.amount.negate(), BigDecimal::add);
            net.merge(tx.destinationName, tx.amount, BigDecimal::add);
        }
        net.remove(CASH_ACCOUNT_NAME);

        return net.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) != 0)
                .map(e -> new AccountDeltaDTO(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.delta.compareTo(a.delta))
                .collect(Collectors.toList());
    }

    public List<AccountDeltaDTO> getAccountDeltas() {
        return accountDeltas;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
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

    public BigDecimal getPredictedFixas() {
        return predictedFixas;
    }

    public void setPredictedFixas(BigDecimal predictedFixas) {
        this.predictedFixas = predictedFixas;
    }

    public BigDecimal getPredictedIncome() {
        return predictedIncome;
    }

    public void setPredictedIncome(BigDecimal predictedIncome) {
        this.predictedIncome = predictedIncome;
    }
}