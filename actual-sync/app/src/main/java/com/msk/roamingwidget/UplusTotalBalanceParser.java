package com.msk.roamingwidget;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects the named aggregate row, never the basic/additional component rows. */
final class UplusTotalBalanceParser {
    private static final String AMOUNT =
            "((?:[0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.[0-9]+)?\\s*(?:TB|GB|MB|KB|B)(?![A-Za-z]))";
    private static final String PAIR = AMOUNT + "\\s*(?:남음|잔여)\\s*[/|│∣]\\s*" + AMOUNT;
    private static final Pattern LABEL = pattern("^총\\s*데이터(?:량)?\\s*[:：]?$");
    private static final Pattern LABEL_ANYWHERE = pattern("총\\s*데이터(?:량)?");
    private static final Pattern LABELLED_PAIR = pattern(
            "총\\s*데이터(?:량)?\\s*[:：]?\\s*" + PAIR
    );
    private static final Pattern PAIR_PATTERN = pattern(PAIR);
    private static final Pattern PURE_PAIRS = pattern(
            "^(?:" + PAIR + ")(?:\\s+(?:" + PAIR + "))*$"
    );
    private static final Pattern NEXT_SECTION = pattern(
            "기본\\s*데이터|추가\\s*데이터|충전\\s*데이터|총\\s*데이터|이용\\s*국가|이용\\s*기간"
    );

    static final class Selection {
        final boolean hasTotalLabel;
        final boolean invalid;
        final List<RoamingBalance> balances;

        Selection(boolean hasTotalLabel, boolean invalid, List<RoamingBalance> balances) {
            this.hasTotalLabel = hasTotalLabel;
            this.invalid = invalid;
            this.balances = balances;
        }
    }

    private static final class Candidate {
        final int origin;
        final ScreenText anchor;
        final RoamingBalance balance;

        Candidate(int origin, ScreenText anchor, RoamingBalance balance) {
            this.origin = origin;
            this.anchor = anchor;
            this.balance = balance;
        }
    }

    private UplusTotalBalanceParser() {
    }

    static Selection select(List<ScreenText> nodes) {
        List<Candidate> candidates = new ArrayList<>();
        boolean hasLabel = false;
        boolean invalid = false;
        for (int index = 0; index < nodes.size(); index++) {
            ScreenText node = nodes.get(index);
            String text = normalize(node.text);
            hasLabel |= LABEL_ANYWHERE.matcher(text).find();
            Matcher labelled = LABELLED_PAIR.matcher(text);
            while (labelled.find()) {
                int end = text.length();
                Matcher boundary = NEXT_SECTION.matcher(text);
                if (boundary.find(labelled.end())) {
                    end = boundary.start();
                }
                String scopedText = text.substring(labelled.start(1), end).trim();
                if (!PURE_PAIRS.matcher(scopedText).matches()) {
                    invalid = true;
                    continue;
                }
                Matcher scoped = PAIR_PATTERN.matcher(scopedText);
                while (scoped.find()) {
                    invalid |= !add(candidates, index, node, scoped);
                }
            }
            if (!LABEL.matcher(text).matches()) {
                continue;
            }
            for (ScreenText value : nodes) {
                // Geometry, not breadth/depth-first ordering, associates the two cells.
                if (value == node || !node.sameRowAs(value) || value.right <= node.right) {
                    continue;
                }
                String valueText = normalize(value.text);
                if (!PURE_PAIRS.matcher(valueText).matches()) {
                    continue;
                }
                Matcher pair = PAIR_PATTERN.matcher(valueText);
                while (pair.find()) {
                    invalid |= !add(candidates, index, node, pair);
                }
            }
        }
        List<RoamingBalance> balances = new ArrayList<>();
        for (Candidate candidate : candidates) {
            balances.add(candidate.balance);
        }
        return new Selection(hasLabel, invalid, balances);
    }

    private static boolean add(
            List<Candidate> candidates, int origin, ScreenText anchor, Matcher match
    ) {
        RoamingBalance balance = RoamingBalance.fromDisplayedPair(match.group(1), match.group(2));
        if (balance == null) {
            return false;
        }
        for (Candidate existing : candidates) {
            boolean sameAnchor = existing.origin == origin
                    || (existing.anchor.sameRowAs(anchor)
                    && existing.anchor.left == anchor.left && existing.anchor.right == anchor.right);
            if (sameAnchor && existing.balance.signature().equals(balance.signature())) {
                return true;
            }
        }
        candidates.add(new Candidate(origin, anchor, balance));
        return true;
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ').replace('\u202F', ' ')
                .replace("\u200B", "").trim().replaceAll("\\s+", " ");
    }
}
