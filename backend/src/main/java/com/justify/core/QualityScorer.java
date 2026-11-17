package com.justify.core;

/** Score = 60% completeness (decided by code) + 40% clarity (rated by the model), on 0-10. See D4. */
public final class QualityScorer {

    public static final double COMPLETENESS_WEIGHT = 0.6;
    public static final double CLARITY_WEIGHT = 0.4;

    /**
     * @param completeness 0.0-1.0 share of required fields present
     * @param clarity      0-10 from the model
     * @return 0.0-10.0 rounded to one decimal
     */
    public double score(double completeness, int clarity) {
        double c = clamp(completeness, 0, 1);
        double cl = clamp(clarity, 0, 10) / 10.0;
        double raw = 10 * (COMPLETENESS_WEIGHT * c + CLARITY_WEIGHT * cl);
        return Math.round(clamp(raw, 0, 10) * 10) / 10.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
