package com.eventra.solver;

/**
 * Classic O(n^3) Hungarian (Kuhn-Munkres) algorithm implementation with potentials
 * using 64-bit integer (long) arithmetic.
 * Solves the minimum-weight perfect matching problem on a complete bipartite graph.
 */
public final class HungarianAlgorithm {

    private static final long INF = Long.MAX_VALUE / 4;

    private HungarianAlgorithm() {
    }

    /**
     * Finds the minimum cost matching in an n x n cost matrix.
     *
     * @param cost square n x n cost matrix
     * @return array of length n where rowToCol[i] is the column matched to row i
     * @throws IllegalArgumentException if cost is null or not square
     */
    public static int[] solve(long[][] cost) {
        if (cost == null) {
            throw new IllegalArgumentException("Cost matrix cannot be null");
        }
        int n = cost.length;
        if (n == 0) {
            return new int[0];
        }
        for (int i = 0; i < n; i++) {
            if (cost[i] == null || cost[i].length != n) {
                throw new IllegalArgumentException("Cost matrix must be square (n x n) and non-null");
            }
        }

        long[] u = new long[n + 1];
        long[] v = new long[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            long[] minv = new long[n + 1];
            boolean[] used = new boolean[n + 1];
            for (int j = 0; j <= n; j++) {
                minv[j] = INF;
            }

            do {
                used[j0] = true;
                int i0 = p[j0];
                long delta = INF;
                int j1 = 0;

                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        long cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }

                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }

                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        int[] rowToCol = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] > 0 && p[j] <= n) {
                rowToCol[p[j] - 1] = j - 1;
            }
        }
        return rowToCol;
    }
}

