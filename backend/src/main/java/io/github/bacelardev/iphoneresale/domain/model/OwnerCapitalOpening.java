package io.github.bacelardev.iphoneresale.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "owner_capital_opening")
public class OwnerCapitalOpening extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_initialization_id", nullable = false, updatable = false)
    private BusinessInitialization businessInitialization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private AppUser ownerUser;

    @Column(name = "historical_contribution_amount", nullable = false,
            updatable = false, precision = 14, scale = 2)
    private BigDecimal historicalContributionAmount;

    @Column(name = "historical_withdrawal_amount", nullable = false,
            updatable = false, precision = 14, scale = 2)
    private BigDecimal historicalWithdrawalAmount;

    protected OwnerCapitalOpening() {
    }
}
