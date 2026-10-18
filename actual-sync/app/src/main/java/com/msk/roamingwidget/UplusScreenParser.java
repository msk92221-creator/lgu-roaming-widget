package com.msk.roamingwidget;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UplusScreenParser {
    enum Status {
        SUCCESS,
        NOT_ROAMING_SCREEN,
        LOADING,
        LOGIN_REQUIRED,
        NO_VALUE,
        AMBIGUOUS
    }

    enum Kind {
        DIRECT_REMAINING,
        TOTAL_BALANCE,
        DISPLAYED_USED_OF_TOTAL,
        UNLIMITED
    }

    static final class Result {
        final Status status;
        final Kind kind;
        final String primaryText;
        final String detailsText;
        final String diagnostic;
        final RoamingBalance balance;

        private Result(Status status, Kind kind, String primary, String details, String diagnostic) {
            this(status, kind, primary, details, diagnostic, null);
        }

        private Result(Status status, Kind kind, String primary, String details,
                String diagnostic, RoamingBalance balance) {
            this.status = status;
            this.kind = kind;
            this.primaryText = primary;
            this.detailsText = details;
            this.diagnostic = diagnostic;
            this.balance = balance;
        }

        static Result success(Kind kind, String primary, String details, String diagnostic) {
            return new Result(Status.SUCCESS, kind, primary, details, diagnostic);
        }

        static Result status(Status status, String diagnostic) {
            return new Result(status, null, "", "", diagnostic);
        }
    }

    private static final class Candidate {
        final String value;
        final int origin;

        Candidate(String value, int origin) {
            this.value = value;
            this.origin = origin;
        }
    }

    private static final String NUMBER = "(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?";
    private static final String UNIT =
            "(?:(?:TB|GB|MB|KB|B)(?![A-Za-z])|테라바이트|기가바이트|메가바이트|킬로바이트|테라|기가|메가)";
    private static final String DATA_TOKEN = NUMBER + "\\s*" + UNIT;
    private static final String VALUE = "(" + DATA_TOKEN + ")";
    private static final String REMAINING_LABEL =
            "(?:남은\\s*(?:해외\\s*)?(?:로밍\\s*)?데이터(?:량)?|"
                    + "데이터\\s*잔여량|잔여\\s*(?:해외\\s*)?(?:로밍\\s*)?데이터(?:량)?|"
                    + "사용\\s*가능\\s*(?:해외\\s*)?(?:로밍\\s*)?데이터(?:량)?|잔여\\s*제공량)";
    private static final String USED_LABEL =
            "(?:현재까지\\s*사용한\\s*데이터량|현재까지\\s*사용|데이터\\s*사용량|사용\\s*데이터(?:량)?|사용량)";
    private static final String TOTAL_LABEL =
            "(?:기본\\s*제공\\s*데이터량|기본\\s*제공량|총\\s*데이터(?:량)?|전체\\s*데이터(?:량)?|제공량)";

    private static final Pattern REMAINING_LABEL_PATTERN = pattern(REMAINING_LABEL);
    private static final Pattern DIRECT_BEFORE = pattern(REMAINING_LABEL + "\\s*[:：]?\\s*" + VALUE);
    private static final Pattern DIRECT_AFTER = pattern(VALUE + "\\s*(?:남음|잔여)");
    private static final Pattern UNLIMITED = pattern(
            REMAINING_LABEL + "\\s*[:：]?\\s*(?:데이터\\s*)?무제한"
    );
    private static final Pattern COMPOUND_REMAINING = pattern(
            REMAINING_LABEL + "\\s*[:：]?\\s*" + DATA_TOKEN
                    + "\\s*(?:\\+|및|,)?\\s*" + DATA_TOKEN
    );
    private static final Pattern USED = pattern(USED_LABEL + "\\s*[:：]?\\s*" + VALUE);
    private static final Pattern TOTAL = pattern(TOTAL_LABEL + "\\s*[:：]?\\s*" + VALUE);
    private static final Pattern DATA_VALUE = pattern(DATA_TOKEN);
    private static final Pattern DOMESTIC_CONTEXT = pattern(
            "국내\\s*데이터|5G\\s*/?\\s*LTE\\s*데이터|테더링|쉐어링|나눠쓰기|선물받은\\s*데이터"
    );
    private static final Pattern RELEVANT = pattern(
            "로밍|해외|데이터|잔여|남은|사용|제공|이용\\s*현황|무제한|(?:^|\\s)" + DATA_TOKEN
    );
    private static final Pattern SENSITIVE_LABEL = pattern(
            "비밀번호|인증번호|주민|생년월일|고객명|가입자|휴대폰\\s*번호|전화번호|회선번호|아이디|주소"
    );
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+82[-. ]?10|01[016789])[-. ]?\\d{3,4}[-. ]?\\d{4}(?!\\d)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\d.])\\d{7,}(?![\\d.])");

    private UplusScreenParser() {
    }

    static Result parse(List<String> rawNodes) {
        List<ScreenText> screenNodes = new ArrayList<>();
        if (rawNodes != null) {
            for (String text : rawNodes) {
                screenNodes.add(new ScreenText(text));
            }
        }
        return parseScreen(screenNodes);
    }

    static Result parseScreen(List<ScreenText> screenNodes) {
        List<String> rawNodes = new ArrayList<>();
        for (ScreenText node : screenNodes) {
            rawNodes.add(node.text);
        }
        List<String> nodes = normalizeNodes(rawNodes);
        if (nodes.isEmpty()) {
            return Result.status(Status.NOT_ROAMING_SCREEN, "접근성 텍스트 노드 0개");
        }

        boolean roamingScreen = hasRoamingScreenMarker(nodes);
        if (containsAny(nodes, "로그인", "본인인증", "간편인증") && !roamingScreen) {
            return Result.status(Status.LOGIN_REQUIRED, safeStatus(nodes, "로그인 화면 감지"));
        }
        if (containsAny(nodes, "로딩 중", "불러오는 중", "잠시만 기다려")) {
            return Result.status(Status.LOADING, safeStatus(nodes, "로딩 화면 감지"));
        }
        if (!roamingScreen) {
            return Result.status(Status.NOT_ROAMING_SCREEN, safeStatus(nodes, "로밍 이용 현황 표식 없음"));
        }

        String diagnostic = buildRoamingDiagnostic(nodes);
        UplusTotalBalanceParser.Selection total = UplusTotalBalanceParser.select(screenNodes);
        if (total.invalid || total.balances.size() > 1) {
            return Result.status(Status.AMBIGUOUS,
                    diagnostic + "\n총 데이터 행이 여러 개이거나 잔여량/전체량이 일치하지 않음");
        }
        if (total.balances.size() == 1) {
            RoamingBalance balance = total.balances.get(0);
            return new Result(Status.SUCCESS, Kind.TOTAL_BALANCE,
                    balance.remainingText + " 남음",
                    "사용 " + balance.usedText + " · 전체 " + balance.totalText,
                    diagnostic + "\n총 데이터 행 선택 · 사용량은 전체−남은량", balance);
        }
        if (total.hasTotalLabel) {
            return Result.status(Status.NO_VALUE,
                    diagnostic + "\n총 데이터 라벨과 같은 행의 잔여량/전체량을 확인하지 못함");
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            String localContext = join(nodes, Math.max(0, index - 2), Math.min(nodes.size(), index + 3));
            if (DOMESTIC_CONTEXT.matcher(localContext).find()) {
                continue;
            }

            String node = nodes.get(index);
            String forward = join(nodes, index, Math.min(nodes.size(), index + 3));
            boolean hasRemainingLabel = REMAINING_LABEL_PATTERN.matcher(node).find();
            if (hasRemainingLabel && COMPOUND_REMAINING.matcher(forward).find()) {
                return Result.status(
                        Status.AMBIGUOUS,
                        diagnostic + "\n복합 단위 잔여량은 원문 구조 확인이 필요합니다"
                );
            }

            int previousRemainingLabel = findPreviousRemainingLabel(nodes, index);
            boolean directInNode = addMatches(candidates, node, DIRECT_BEFORE, index);
            if (previousRemainingLabel < 0) {
                directInNode |= addMatches(candidates, node, DIRECT_AFTER, index);
            }
            if (!directInNode && hasRemainingLabel) {
                addMatches(candidates, forward, DIRECT_BEFORE, index);
            }
            if (!directInNode && DATA_VALUE.matcher(node).find()
                    && previousRemainingLabel < 0) {
                String valueAndSuffix = join(nodes, index, Math.min(nodes.size(), index + 2));
                addMatches(candidates, valueAndSuffix, DIRECT_AFTER, index);
            }
            if (UNLIMITED.matcher(node).find()) {
                candidates.add(new Candidate("무제한", index));
            } else if (hasRemainingLabel && UNLIMITED.matcher(forward).find()) {
                candidates.add(new Candidate("무제한", index));
            }
        }

        List<Candidate> meters = deduplicateSameOrigin(candidates);
        if (meters.size() == 1) {
            Candidate meter = meters.get(0);
            if ("무제한".equals(meter.value)) {
                return Result.success(Kind.UNLIMITED, "무제한", "U+one 로밍 화면의 표시 원문", diagnostic);
            }
            return Result.success(
                    Kind.DIRECT_REMAINING,
                    meter.value + " 남음",
                    "U+one 화면의 잔여량 원문",
                    diagnostic
            );
        }
        if (meters.size() > 1) {
            List<String> values = new ArrayList<>();
            for (Candidate meter : meters) {
                values.add(meter.value);
            }
            return Result.status(
                    Status.AMBIGUOUS,
                    diagnostic + "\n서로 다른 계량기 후보: " + String.join(", ", values)
            );
        }

        if (!findLabeledValues(nodes, USED).isEmpty() || !findLabeledValues(nodes, TOTAL).isEmpty()) {
            return Result.status(
                    Status.NO_VALUE,
                    diagnostic + "\n잔여량 직접 표시는 없고 사용/제공량 원문만 확인됨"
            );
        }
        return Result.status(Status.NO_VALUE, diagnostic);
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean addMatches(
            List<Candidate> output, String text, Pattern pattern, int origin
    ) {
        boolean found = false;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            output.add(new Candidate(formatValue(matcher.group(1)), origin));
            found = true;
        }
        return found;
    }

    private static int findPreviousRemainingLabel(List<String> nodes, int index) {
        for (int cursor = index - 1; cursor >= Math.max(0, index - 2); cursor--) {
            if (REMAINING_LABEL_PATTERN.matcher(nodes.get(cursor)).find()) {
                return cursor;
            }
        }
        return -1;
    }

    private static List<Candidate> deduplicateSameOrigin(List<Candidate> candidates) {
        Map<String, Candidate> unique = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = candidate.origin + "|" + candidate.value.toUpperCase(Locale.ROOT);
            unique.putIfAbsent(key, candidate);
        }
        return new ArrayList<>(unique.values());
    }

    private static Set<String> findLabeledValues(List<String> nodes, Pattern pattern) {
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            String forward = join(nodes, index, Math.min(nodes.size(), index + 3));
            if (DOMESTIC_CONTEXT.matcher(forward).find()) {
                continue;
            }
            Matcher matcher = pattern.matcher(forward);
            if (matcher.find()) {
                values.add(formatValue(matcher.group(1)));
            }
        }
        return values;
    }

    private static List<String> normalizeNodes(List<String> rawNodes) {
        List<String> result = new ArrayList<>();
        if (rawNodes == null) {
            return result;
        }
        for (String raw : rawNodes) {
            String normalized = normalize(raw);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace('／', '/')
                .replace('：', ':')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String compact(String value) {
        return value.replaceAll("[\\s·>›/]+", "");
    }

    private static boolean hasRoamingScreenMarker(List<String> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            String text = compact(join(nodes, index, Math.min(nodes.size(), index + 3)));
            if (text.contains("해외로밍이용현황")
                    || text.contains("해외로밍사용현황")
                    || text.contains("로밍데이터이용현황")
                    || text.contains("로밍사용량조회")) {
                return true;
            }
        }
        return false;
    }

    private static String join(List<String> nodes, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(nodes.get(index));
        }
        return builder.toString();
    }

    private static boolean containsAny(List<String> nodes, String... needles) {
        for (String node : nodes) {
            for (String needle : needles) {
                if (node.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatValue(String rawValue) {
        Matcher matcher = pattern("(" + NUMBER + ")\\s*(" + UNIT + ")")
                .matcher(normalize(rawValue));
        if (!matcher.find()) {
            return normalize(rawValue);
        }
        String unit = matcher.group(2);
        if (unit.matches("(?i)TB|GB|MB|KB|B")) {
            unit = unit.toUpperCase(Locale.ROOT);
        }
        return matcher.group(1) + " " + unit;
    }

    private static String safeStatus(List<String> nodes, String status) {
        return status + " · 텍스트 노드 " + nodes.size() + "개 · 원문 저장 안 함";
    }

    private static String buildRoamingDiagnostic(List<String> nodes) {
        List<String> relevant = new ArrayList<>();
        List<String> ordered = new ArrayList<>();
        for (String node : nodes) {
            if (node.contains("총 데이터") || node.contains("총데이터")
                    || node.contains("기본 데이터") || node.contains("기본데이터")
                    || DIRECT_AFTER.matcher(node).find()) {
                ordered.add(node);
            }
        }
        for (String node : nodes) {
            if (!ordered.contains(node)) {
                ordered.add(node);
            }
        }
        for (String node : ordered) {
            if (!RELEVANT.matcher(node).find() || SENSITIVE_LABEL.matcher(node).find()) {
                continue;
            }
            String safe = PHONE.matcher(node).replaceAll("[전화번호]");
            safe = EMAIL.matcher(safe).replaceAll("[이메일]");
            safe = LONG_NUMBER.matcher(safe).replaceAll("[긴 숫자]");
            if (safe.length() > 120) {
                safe = safe.substring(0, 120) + "…";
            }
            relevant.add(safe);
            if (relevant.size() >= 12) {
                break;
            }
        }
        if (relevant.isEmpty()) {
            return safeStatus(nodes, "로밍 화면 표식 확인");
        }
        return "로밍 화면 표식 확인 · 텍스트 노드 " + nodes.size()
                + "개\n- " + String.join("\n- ", relevant);
    }
}
