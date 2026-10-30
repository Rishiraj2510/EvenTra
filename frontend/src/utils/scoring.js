/**
 * Scoring helper for assignment plan solver fit calculations.
 * Weights:
 * - Skill match: 45% (0.45)
 * - Experience: 15% (0.15)
 * - Past Performance: 15% (0.15)
 * - Workload / Load: 15% (0.15)
 * - Interest: 10% (0.10)
 *
 * Formula:
 * fit = 100 * (0.45 * skill + 0.15 * experience + 0.15 * performance + 0.15 * load + 0.10 * interest)
 */

export const DEFAULT_WEIGHTS = {
  skill: 0.45,
  experience: 0.15,
  performance: 0.15,
  load: 0.15,
  interest: 0.10,
}

export function calculateFitScore(breakdown, weights = DEFAULT_WEIGHTS) {
  if (!breakdown) return 0
  const skill = Number(breakdown.skill) || 0
  const experience = Number(breakdown.experience) || 0
  const performance = Number(breakdown.performance) || 0
  const load = Number(breakdown.load) || 0
  const interest = Number(breakdown.interest) || 0

  const total =
    weights.skill * skill +
    weights.experience * experience +
    weights.performance * performance +
    weights.load * load +
    weights.interest * interest

  return Math.round(total * 100)
}
