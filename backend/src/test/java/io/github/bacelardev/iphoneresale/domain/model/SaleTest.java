package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.SaleStatus;
import io.github.bacelardev.iphoneresale.web.dto.sale.SaleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleTest {
    private final Device device = mock(Device.class);
    private final AppUser actor = mock(AppUser.class);
    private final Instant at = Instant.parse("2026-09-10T18:00:00Z");

    @ParameterizedTest
    @CsvSource({"1800,250,3200,1150,35.9375", "2500,0,2500,0,0.0000",
            "2500,0,2300,-200,-8.6957", "2,0,3,1,33.3333"})
    void calculatesOfficialMoneyAndFourDecimalMargin(String purchase, String maintenance,
            String price, String profit, String margin) {
        when(device.getPurchasePrice()).thenReturn(new BigDecimal(purchase));
        SaleResponse response = SaleResponse.from(Sale.active(device, new BigDecimal(price), at, actor),
                new BigDecimal(maintenance));
        assertThat(response.profit()).isEqualByComparingTo(profit);
        assertThat(response.marginPercent()).isEqualTo(new BigDecimal(margin));
        assertThat(response.marginPercent().scale()).isEqualTo(4);
    }

    @Test
    void requiresCancellationDataBeforeChangingStateAndAllowsCancellationOnlyOnce() {
        Sale sale = Sale.active(device, new BigDecimal("2000.00"), at, actor);
        assertThatThrownBy(() -> sale.cancel(null, actor, "Motivo")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sale.cancel(at, null, "Motivo")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sale.cancel(at, actor, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sale.cancel(at, actor, "x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.ACTIVE);
        sale.cancel(at, actor, "  Motivo  ");
        assertThat(sale.getCancellationReason()).isEqualTo("Motivo");
        assertThatThrownBy(() -> sale.cancel(at, actor, "Outro")).isInstanceOf(IllegalStateException.class);
    }
}
