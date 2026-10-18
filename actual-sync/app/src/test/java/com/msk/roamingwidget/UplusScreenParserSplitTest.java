package com.msk.roamingwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class UplusScreenParserSplitTest {
    @Test
    public void splitLabelAndValueWithSuffixIsOneMeter() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터",
                "2GB 남음"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("2 GB 남음", result.primaryText);
    }

    @Test
    public void labelValueAndSuffixInThreeNodesAreOneMeter() {
        UplusScreenParser.Result result = UplusScreenParser.parse(Arrays.asList(
                "해외 로밍 이용 현황",
                "남은 데이터",
                "2GB",
                "남음"
        ));

        assertEquals(UplusScreenParser.Status.SUCCESS, result.status);
        assertEquals("2 GB 남음", result.primaryText);
    }
}
