package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class UplusScreenParserTest {
    @Test
    public void directRemainingIsCapturedWithoutCalculation() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 로밍 데이터 6.5GB"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals(UplusScreenParser.Kind.DIRECT_REMAINING, result.kind);
        assertEquals("6.5 GB 남음", result.primaryText);
    }

    @Test
    public void splitLabelAndValueAreCaptured() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터",
                "3.75 GB"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("3.75 GB 남음", result.primaryText);
    }

    @Test
    public void domesticUnlimitedIsNeverAccepted() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "MY",
                "국내 데이터",
                "무제한",
                "해외 로밍"
        ));

        assertEquals(UplusScreenParser.Status.NOT_ROAMING_SCREEN, result.status);
    }

    @Test
    public void planCapacityAndSpeedAreNotRemaining() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "로밍패스 7GB",
                "소진 후 200Kbps"
        ));

        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
    }

    @Test
    public void labeledSpeedIsNeverParsedAsKilobytes() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터 200Kbps"
        ));

        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
    }

    @Test
    public void usedAndTotalAreNeverSubtractedOrPaired() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "현재까지 사용한 데이터량 1025.6MB",
                "기본 제공 데이터량 4608MB"
        ));

        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
        assertTrue(result.diagnostic.contains("사용/제공량 원문"));
    }

    @Test
    public void differentRemainingMetersAreAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "일일 남은 데이터 2GB",
                "전체 남은 데이터 8GB"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void sameValueFromTwoMetersIsStillAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "일일 남은 데이터",
                "2GB",
                "전체 남은 데이터",
                "2GB"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void samePhysicalNodeTextAndDescriptionAreOneMeter() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황",
                "총 데이터",
                "48.7GB남음 / 49GB 48.7GB남음 / 49GB",
                "기본 데이터"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("48.7 GB 남음", result.primaryText);
    }

    @Test
    public void sameRatioFromTwoPhysicalNodesIsStillAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황",
                "총 데이터",
                "48.7GB남음 / 49GB",
                "48.7GB남음 / 49GB",
                "기본 데이터"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void twoDifferentValuesInsideOnePhysicalNodeAreAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황",
                "총 데이터",
                "48.7GB남음 / 49GB 1GB남음 / 1GB",
                "기본 데이터"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void sameRemainingWithDifferentTotalsIsAmbiguous() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외로밍 이용현황",
                "48.7GB남음 / 49GB",
                "48.7GB남음 / 60GB"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void compoundUnitsAreRejectedInsteadOfTruncated() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터 6GB 500MB"
        ));

        assertEquals(UplusScreenParser.Status.AMBIGUOUS, result.status);
        assertFalse(result.primaryText.contains("6 GB 남음"));
    }

    @Test
    public void fullWidthTextIsNormalized() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터：６．５　ＧＢ"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("6.5 GB 남음", result.primaryText);
    }

    @Test
    public void roamingMenuOnHomeDoesNotUnlockDomesticCard() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "U+one MY",
                "데이터 무제한",
                "해외 로밍",
                "이용하기"
        ));

        assertEquals(UplusScreenParser.Status.NOT_ROAMING_SCREEN, result.status);
    }

    @Test
    public void nonRoamingScreenNeverStoresRawTextInDiagnostic() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "홍길동 고객님",
                "010-1234-5678",
                "국내 데이터 무제한"
        ));

        assertEquals(UplusScreenParser.Status.NOT_ROAMING_SCREEN, result.status);
        assertFalse(result.diagnostic.contains("홍길동"));
        assertFalse(result.diagnostic.contains("010-1234-5678"));
    }

    @Test
    public void internationalPhoneNumberIsMaskedInRoamingDiagnostic() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터 6.5GB +82 10 1234 5678"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertFalse(result.diagnostic.contains("1234 5678"));
        assertTrue(result.diagnostic.contains("[전화번호]"));
    }
}
