package com.msk.roamingwidget;

import java.util.regex.Pattern;

/** Accepts only the separately selected aggregate amounts from the official web page. */
final class OfficialWebBalanceParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?\\s*(?:TB|GB|MB)",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_AMOUNT_LENGTH = 80;

    private OfficialWebBalanceParser() {
    }

    static UplusScreenParser.Result parse(String state, String remaining, String total) {
        if ("login".equals(state)) {
            return UplusScreenParser.Result.status(UplusScreenParser.Status.LOGIN_REQUIRED,
                    "공식 웹 로그인 필요 · 원문 저장 안 함");
        }
        if ("loading".equals(state)) {
            return UplusScreenParser.Result.status(UplusScreenParser.Status.LOADING,
                    "공식 웹 데이터 불러오는 중 · 원문 저장 안 함");
        }
        if ("ambiguous".equals(state)) {
            return UplusScreenParser.Result.status(UplusScreenParser.Status.AMBIGUOUS,
                    "공식 웹 총 데이터 영역을 하나로 확인하지 못함 · 원문 저장 안 함");
        }
        if (!"ready".equals(state)) {
            return unavailable();
        }

        String remainingAmount = normalizeAmount(remaining);
        String totalAmount = normalizeAmount(total);
        if (remainingAmount == null || totalAmount == null) {
            return unavailable();
        }
        RoamingBalance balance = RoamingBalance.fromDisplayedPair(remainingAmount, totalAmount);
        if (balance == null) {
            return unavailable();
        }
        return UplusScreenParser.Result.fromBalance(balance,
                "공식 웹 총 데이터 영역 확인 · 사용량은 전체−남은량 · 원문 저장 안 함");
    }

    private static String normalizeAmount(String raw) {
        if (raw == null || raw.length() > MAX_AMOUNT_LENGTH) {
            return null;
        }
        String normalized = raw.replace('\u00a0', ' ').replace('\u202f', ' ').trim();
        return AMOUNT.matcher(normalized).matches() ? normalized : null;
    }

    private static UplusScreenParser.Result unavailable() {
        return UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                "공식 웹 총 데이터의 남은량/전체량을 확인하지 못함 · 원문 저장 안 함");
    }
}
