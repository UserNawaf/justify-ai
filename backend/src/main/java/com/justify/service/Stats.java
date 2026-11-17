package com.justify.service;

public record Stats(long total, long needsInfo, long complete, long incomplete, double firstPassRatePct,
                    Double avgQuality, Double avgRounds, long feedbackCount, Double sufficientPct) {
}
