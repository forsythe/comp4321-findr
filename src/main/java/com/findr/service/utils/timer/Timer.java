package com.findr.service.utils;

interface Op {
    void runOp();
}

/**
 * Util class for timing functions in a single line
 */
public class Timer {
    private static final double ONE_BILLION = 1_000_000_000;

    public static double measure(Op operation) {
        long startTime = System.nanoTime();
        operation.runOp();
        long endTime = System.nanoTime();
        return (endTime - startTime) / ONE_BILLION;
    }
}