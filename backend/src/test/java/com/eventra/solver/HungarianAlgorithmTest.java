package com.eventra.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HungarianAlgorithm.
 */
class HungarianAlgorithmTest {

    @Test
    @DisplayName("Golden test: 3x3 matrix [[4,1,3],[2,0,5],[3,2,2]] -> rowToCol [1,0,2] with total cost 5")
    void goldenCase_3x3() {
        long[][] cost = {
                {4, 1, 3},
                {2, 0, 5},
                {3, 2, 2}
        };

        int[] rowToCol = HungarianAlgorithm.solve(cost);
        assertArrayEquals(new int[]{1, 0, 2}, rowToCol);

        long totalCost = cost[0][rowToCol[0]] + cost[1][rowToCol[1]] + cost[2][rowToCol[2]];
        assertEquals(5L, totalCost);
    }

    @Test
    @DisplayName("1x1 matrix test: [[42]] -> [0]")
    void singleElementMatrix() {
        long[][] cost = {{42L}};
        int[] rowToCol = HungarianAlgorithm.solve(cost);
        assertArrayEquals(new int[]{0}, rowToCol);
    }

    @Test
    @DisplayName("Empty matrix: 0x0 -> empty array")
    void emptyMatrix() {
        long[][] cost = new long[0][0];
        int[] rowToCol = HungarianAlgorithm.solve(cost);
        assertNotNull(rowToCol);
        assertEquals(0, rowToCol.length);
    }

    @Test
    @DisplayName("Validation: null or non-square matrix throws IllegalArgumentException")
    void nonSquareOrNull_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(null));

        long[][] nonSquareWide = {
                {1, 2, 3},
                {4, 5, 6}
        };
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(nonSquareWide));

        long[][] nonSquareTall = {
                {1, 2},
                {3, 4},
                {5, 6}
        };
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(nonSquareTall));

        long[][] ragged = {
                {1, 2},
                null
        };
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(ragged));
    }

    @Test
    @DisplayName("f) 200 random matrices (seed 5, n 1..7, costs 0..20 and 10^12) match permutation brute force")
    void bruteForceComparison_200Matrices() {
        Random rng = new Random(5);
        long bigM = 1_000_000_000_000L;

        for (int iter = 0; iter < 200; iter++) {
            int n = 1 + rng.nextInt(7);
            long[][] cost = new long[n][n];
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (rng.nextDouble() < 0.15) {
                        cost[r][c] = bigM;
                    } else {
                        cost[r][c] = rng.nextInt(21);
                    }
                }
            }

            int[] rowToCol = HungarianAlgorithm.solve(cost);
            long solverTotal = 0L;
            for (int r = 0; r < n; r++) {
                solverTotal += cost[r][rowToCol[r]];
            }

            boolean[] usedCols = new boolean[n];
            long[] bestCost = new long[]{Long.MAX_VALUE};
            permuteBruteForce(cost, 0, n, usedCols, 0L, bestCost);

            assertEquals(bestCost[0], solverTotal, "Iteration " + iter + " cost mismatch for n=" + n);
        }
    }

    private static void permuteBruteForce(
            long[][] cost, int row, int n,
            boolean[] usedCols, long curCost, long[] bestCost
    ) {
        if (row == n) {
            if (curCost < bestCost[0]) {
                bestCost[0] = curCost;
            }
            return;
        }

        if (curCost >= bestCost[0]) {
            return;
        }

        for (int col = 0; col < n; col++) {
            if (!usedCols[col]) {
                usedCols[col] = true;
                permuteBruteForce(cost, row + 1, n, usedCols, curCost + cost[row][col], bestCost);
                usedCols[col] = false;
            }
        }
    }
}
