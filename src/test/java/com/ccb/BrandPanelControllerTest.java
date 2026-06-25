package com.ccb;

import com.ccb.controller.page.BrandPanelController;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrandPanelControllerTest {

    @Test
    void summaryValuesExposeInitialReceivedBalanceAndIssued() {
        BrandPanelController.VariantData variant = new BrandPanelController.VariantData(
                "11 kg",
                42,
                18,
                60,
                24,
                List.of(5, 7, 10)
        );

        List<String> summary = BrandPanelController.buildSummaryValues(variant);

        assertEquals(List.of("60", "24", "18", "42"), summary);
    }
}
