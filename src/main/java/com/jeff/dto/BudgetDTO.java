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
    public BigDecimal projected;
    public BigDecimal historicalAverage;

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

    public BigDecimal getProjected() {
        return projected;
    }

    public void setProjected(BigDecimal projected) {
        this.projected = projected;
    }

    public String getProjectedFormatted() {
        return projected != null ? currencyFormatter.format(projected) : "0,00";
    }

    public double getProjectedPercentage() {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0 || projected == null) return 0;
        return projected.doubleValue() / total.doubleValue() * 100;
    }

    /** "ok" (on track), "warning" (will come close, 90-100%), "over" (will blow past 100%). */
    public String getProjectionStatus() {
        double pct = getProjectedPercentage();
        if (pct > 100) return "over";
        if (pct >= 90) return "warning";
        return "ok";
    }

    public BigDecimal getHistoricalAverage() {
        return historicalAverage;
    }

    public void setHistoricalAverage(BigDecimal historicalAverage) {
        this.historicalAverage = historicalAverage;
    }

    public String getHistoricalAverageFormatted() {
        return historicalAverage != null ? currencyFormatter.format(historicalAverage) : "0,00";
    }

    public boolean getHasHistory() {
        return historicalAverage != null && historicalAverage.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Projeção deste mês vs. a média dos últimos meses, em %. Ex: 130 = gastando 30% a mais que o normal. */
    public double getTrendPercentage() {
        if (!getHasHistory() || projected == null) return 100;
        return projected.doubleValue() / historicalAverage.doubleValue() * 100;
    }

    /** "up" (acima da média), "down" (abaixo), "flat" (parecido, dentro de ±10%). */
    public String getTrendDirection() {
        double pct = getTrendPercentage();
        if (pct > 110) return "up";
        if (pct < 90) return "down";
        return "flat";
    }

    public long getTrendPercentageRounded() {
        return Math.round(getTrendPercentage());
    }
}

