package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class OfficialWebBalanceParserTest {
    @Test
    public void verifiedOfficialWebAmountsGiveFiveGigabytesUsed() {
        UplusScreenParser.Result result = OfficialWebBalanceParser.parse("ready", "44GB", "49GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals(UplusScreenParser.Kind.TOTAL_BALANCE, result.kind);
        assertNotNull(result.balance);
        assertEquals("44 GB", result.balance.remainingText);
        assertEquals("49 GB", result.balance.totalText);
        assertEquals("5 GB", result.balance.usedText);
    }

    @Test
    public void formattedDecimalAmountAndNonBreakingSpacesAreAccepted() {
        UplusScreenParser.Result result = OfficialWebBalanceParser.parse(
                "ready", "\u00a044.0\u202fGB\u00a0", "49.0 GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("5 GB", result.balance.usedText);
        assertEquals("44 GB", result.balance.remainingText);
    }

    @Test
    public void aggregateIncludesAlreadySelectedTopUpAllowance() {
        UplusScreenParser.Result result = OfficialWebBalanceParser.parse("ready", "44GB", "50GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("6 GB", result.balance.usedText);
        assertEquals("50 GB", result.balance.totalText);
    }

    @Test
    public void megabytesAndTerabytesUseExactSameUnitArithmetic() {
        UplusScreenParser.Result megabytes = OfficialWebBalanceParser.parse(
                "ready", "1,024 MB", "2,048 MB");
        assertEquals(UplusScreenParser.Status.SUCCESS, megabytes.status);
        assertEquals("1024 MB", megabytes.balance.usedText);

        UplusScreenParser.Result terabytes = OfficialWebBalanceParser.parse(
                "ready", "0.125 tb", "0.5 TB");
        assertEquals(UplusScreenParser.Status.SUCCESS, terabytes.status);
        assertEquals("0.375 TB", terabytes.balance.usedText);
    }

    @Test
    public void zeroRemainingAndUnusedAllowanceAreValidNumericValues() {
        UplusScreenParser.Result exhausted = OfficialWebBalanceParser.parse("ready", "0GB", "49GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, exhausted.status);
        assertEquals("49 GB", exhausted.balance.usedText);

        UplusScreenParser.Result unused = OfficialWebBalanceParser.parse("ready", "49GB", "49GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, unused.status);
        assertEquals("0 GB", unused.balance.usedText);
    }

    @Test
    public void decimalsAreSubtractedWithoutFloatingPointRounding() {
        UplusScreenParser.Result result = OfficialWebBalanceParser.parse("ready", "48.7GB", "49GB");
        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("0.3 GB", result.balance.usedText);
    }

    @Test
    public void loadingAndLoginStatesNeverReuseVisibleNumbers() {
        assertEquals(UplusScreenParser.Status.LOGIN_REQUIRED,
                OfficialWebBalanceParser.parse("login", "44GB", "49GB").status);
        assertEquals(UplusScreenParser.Status.LOADING,
                OfficialWebBalanceParser.parse("loading", "44GB", "49GB").status);
        assertEquals(UplusScreenParser.Status.AMBIGUOUS,
                OfficialWebBalanceParser.parse("ambiguous", "44GB", "49GB").status);
    }

    @Test
    public void missingOrUnknownMarkerDoesNotTreatStaleValuesAsReady() {
        for (String state : new String[] {null, "", "stale", "exhausted", "READY", "ready "}) {
            assertUnavailable(OfficialWebBalanceParser.parse(state, "44GB", "49GB"));
        }
    }

    @Test
    public void missingAmountAndExtraneousWordsAreNotGuessed() {
        for (String value : new String[] {null, "", "무제한", "44GB 남음", "남은량 44GB",
                "44GB 사용할 수 있어요", "44GB 1GB", "44GB/49GB", "매일 44GB", "44GB\n49GB"}) {
            assertUnavailable(OfficialWebBalanceParser.parse("ready", value, "49GB"));
            assertUnavailable(OfficialWebBalanceParser.parse("ready", "44GB", value));
        }
    }

    @Test
    public void malformedAndUnsupportedAmountsAreRejected() {
        for (String value : new String[] {"-1GB", "+1GB", ".5GB", "1.GB", "1e1GB", "NaN GB",
                "Infinity GB", "1,23GB", "12,34,567GB", "1 GBGB", "1KB", "1B", "１ＧＢ"}) {
            assertUnavailable(OfficialWebBalanceParser.parse("ready", value, "49GB"));
        }
    }

    @Test
    public void mixedUnitsOrRemainingGreaterThanTotalAreRejected() {
        assertUnavailable(OfficialWebBalanceParser.parse("ready", "500MB", "49GB"));
        assertUnavailable(OfficialWebBalanceParser.parse("ready", "50GB", "49GB"));
        assertUnavailable(OfficialWebBalanceParser.parse("ready", "0GB", "0GB"));
        assertUnavailable(OfficialWebBalanceParser.parse("ready", "1GB", "-49GB"));
    }

    @Test
    public void diagnosticsDoNotEchoUntrustedPayload() {
        String sensitive = "01012345678 password 123456";
        for (String state : new String[] {"ready", "login", "loading", "ambiguous", sensitive}) {
            UplusScreenParser.Result result = OfficialWebBalanceParser.parse(state, sensitive, sensitive);
            assertFalse(result.diagnostic.contains("01012345678"));
            assertFalse(result.diagnostic.contains("password"));
            assertFalse(result.diagnostic.contains("123456"));
        }
    }

    @Test
    public void oversizedPayloadIsRejectedBeforeNumberParsing() {
        StringBuilder oversized = new StringBuilder();
        for (int index = 0; index < 1000; index++) {
            oversized.append('1');
        }
        oversized.append("GB");
        assertUnavailable(OfficialWebBalanceParser.parse("ready", oversized.toString(), "49GB"));
    }

    private static void assertUnavailable(UplusScreenParser.Result result) {
        assertEquals(UplusScreenParser.Status.NO_VALUE, result.status);
        assertNull(result.balance);
    }
}
