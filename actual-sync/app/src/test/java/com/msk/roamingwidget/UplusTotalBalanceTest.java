package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public final class UplusTotalBalanceTest {
    @Test
    public void fold7ScreenshotUsesTotalRatherThanBasicComponent() {
        UplusScreenParser.Result result = UplusScreenParser.parseScreen(Arrays.asList(
                text("해외로밍 이용현황", 30, 30, 500, 80),
                text("로밍패스 49GB", 70, 700, 400, 740),
                text("총 데이터", 70, 840, 250, 880),
                text("47.8GB남음 / 49GB", 590, 840, 850, 880),
                text("기본 데이터", 70, 950, 300, 990),
                text("47.8GB 남음 | 49GB", 620, 950, 850, 990),
                text("로밍 데이터 나눠쓰기 가입하러 가기", 70, 1600, 800, 1650)
        ));
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("47.8 GB", result.balance.remainingText);
        assertEquals("49 GB", result.balance.totalText);
        assertEquals("1.2 GB", result.balance.usedText);
    }

    @Test
    public void breadthFirstNodeOrderDoesNotBindWrongRow() {
        UplusScreenParser.Result result = UplusScreenParser.parseScreen(Arrays.asList(
                new ScreenText("해외로밍 이용현황"),
                text("총 데이터", 20, 200, 200, 240),
                text("기본 데이터", 20, 300, 200, 340),
                text("40GB남음 / 45GB", 400, 300, 700, 340),
                text("47.8GB남음 / 49GB", 400, 200, 700, 240),
                text("추가 데이터", 20, 400, 200, 440),
                text("7.8GB남음 / 10GB", 400, 400, 700, 440)
        ));
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("47.8 GB", result.balance.remainingText);
    }

    @Test
    public void combinedCardStopsAtBasicSection() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황",
                "총 데이터 47.8GB남음 / 49GB 기본 데이터 40GB 남음 | 45GB 추가 데이터 7.8GB 남음 | 10GB"
        ));
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("1.2 GB", result.balance.usedText);
    }

    @Test
    public void twoTotalRowsRemainAmbiguousEvenWhenSameValues() {
        UplusScreenParser.Result result = UplusScreenParser.parseScreen(Arrays.asList(
                new ScreenText("해외로밍 이용현황"),
                text("총 데이터", 20, 200, 200, 240),
                text("47.8GB남음 / 49GB", 400, 200, 700, 240),
                text("총 데이터", 20, 500, 200, 540),
                text("47.8GB남음 / 49GB", 400, 500, 700, 540)
        ));
        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void missingGeometryDoesNotGuessFromNearbyNumbers() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "총 데이터", "기본 데이터", "47.8GB남음 / 49GB"
        ));
        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
    }

    @Test
    public void largeParentBoundsCannotBeMistakenForTotalRow() {
        UplusScreenParser.Result result = UplusScreenParser.parseScreen(Arrays.asList(
                new ScreenText("해외로밍 이용현황"),
                text("총 데이터", 20, 200, 200, 240),
                text("40GB남음 / 49GB", 400, 100, 700, 800)
        ));
        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
    }

    @Test
    public void remainingOverTotalAndZeroTotalAreRejected() {
        for (String pair : Arrays.asList("50GB남음 / 49GB", "0GB남음 / 0GB")) {
            UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                    "해외로밍 이용현황", "총 데이터 " + pair
            ));
            assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
        }
    }

    @Test
    public void sameRemainingWithDifferentTotalsInsideNodeIsAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "총 데이터 47.8GB남음 / 49GB 47.8GB남음 / 60GB"
        ));
        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void verticalSeparatorAndFullWidthNumbersWork() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "총 데이터 ４７．８ＧＢ 남음 │ ４９ＧＢ"
        ));
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("1.2 GB", result.balance.usedText);
    }

    @Test
    public void mixedUnitsAreNotConvertedUsingAssumedMultiplier() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "총 데이터 500MB남음 / 49GB"
        ));
        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void malformedOrDifferentlyLabelledValueOnSameRowIsNotBound() {
        for (String value : Arrays.asList(
                "-1GB남음 / 49GB", "1GB 500MB남음 / 999MB",
                "기본 데이터 40GB남음 / 49GB")) {
            UplusScreenParser.Result result = UplusScreenParser.parseScreen(Arrays.asList(
                    new ScreenText("해외로밍 이용현황"),
                    text("총 데이터", 20, 200, 200, 240),
                    text(value, 400, 200, 700, 240)
            ));
            assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
        }
    }

    @Test
    public void compoundTotalSuffixIsNotSilentlyTruncated() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "총 데이터 47.8GB남음 / 49GB 500MB"
        ));
        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void decimalsUseExactSubtractionAndZeroIsValid() {
        assertEquals("0.3 GB", RoamingBalance.fromDisplayedPair("48.7GB", "49GB").usedText);
        assertEquals("49 GB", RoamingBalance.fromDisplayedPair("0GB", "49GB").usedText);
        assertEquals("0 GB", RoamingBalance.fromDisplayedPair("49GB", "49GB").usedText);
    }

    @Test
    public void diagnosticsPrioritizeTotalAndBasicOverAds() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황", "로밍 광고 1", "로밍 광고 2", "로밍 광고 3", "로밍 광고 4",
                "로밍 광고 5", "로밍 광고 6", "로밍 광고 7", "로밍 광고 8", "로밍 광고 9",
                "로밍 광고 10", "로밍 광고 11", "로밍 광고 12",
                "총 데이터 47.8GB남음 / 49GB", "기본 데이터 47.8GB남음 | 49GB"
        ));
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertTrue(result.diagnostic.contains("총 데이터 47.8GB"));
        assertTrue(result.diagnostic.contains("기본 데이터 47.8GB"));
        assertFalse(result.diagnostic.contains("로밍 광고 12"));
    }

    private static ScreenText text(String value, int left, int top, int right, int bottom) {
        return new ScreenText(value, left, top, right, bottom);
    }
}
