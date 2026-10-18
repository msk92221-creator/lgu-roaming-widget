package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class UplusAccessibilityServiceTest {
    @Test
    public void identicalTextAndDescriptionAreCollectedOnce() {
        List<String> output = new ArrayList<>();

        UplusAccessibilityService.addNodeText(
                output,
                "48.7GB남음 / 49GB",
                "48.7GB남음 / 49GB"
        );

        assertEquals(1, output.size());
        assertEquals("48.7GB남음 / 49GB", output.get(0));
    }

    @Test
    public void differentTextAndDescriptionStayInOnePhysicalNode() {
        List<String> output = new ArrayList<>();

        UplusAccessibilityService.addNodeText(
                output,
                "48.7GB남음 / 49GB",
                "총 데이터 48.7GB남음 / 49GB"
        );

        assertEquals(1, output.size());
        assertEquals(
                "48.7GB남음 / 49GB 총 데이터 48.7GB남음 / 49GB",
                output.get(0)
        );
    }
}
