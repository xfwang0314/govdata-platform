package com.xfwang.govdata.metadata.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 根据后端受控阈值判断本次缺失结果是否需要暂停生效。 */
public class MetadataMissingGuard {

    private MetadataMissingGuard() {
    }

    public static boolean shouldSuppress(int activeTableCount, int missingCandidateCount, int minimumBaselineCount,
                                         int minimumMissingCount, BigDecimal maximumMissingRate) {
        if (activeTableCount < minimumBaselineCount || missingCandidateCount < minimumMissingCount) {
            return false;
        }
        BigDecimal missingRate = BigDecimal.valueOf(missingCandidateCount)
            .divide(BigDecimal.valueOf(activeTableCount), 4, RoundingMode.HALF_UP);
        return missingRate.compareTo(maximumMissingRate) >= 0;
    }
}
