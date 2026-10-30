/**
 * Scoring helper for assignment plan solver fit calculations.
 * Default Weights (integers summing to 100):
 * - Skill match: 45
 * - Experience: 15
 * - Past Performance: 15
 * - Workload / Load: 15
 * - Interest: 10
 */

export const DEFAULT_WEIGHTS = {
  skill: 45,
  experience: 15,
  performance: 15,
  load: 15,
  interest: 10,
}

// Global active weights store in memory so AssignmentPlanPage and solvers can reflect saved weights
let currentWeightsStore = { ...DEFAULT_WEIGHTS }

export function getCurrentWeights() {
  return { ...currentWeightsStore }
}

export function setCurrentWeights(newWeights) {
  if (newWeights && typeof newWeights === 'object') {
    currentWeightsStore = {
      skill: newWeights.skill != null ? Number(newWeights.skill) : currentWeightsStore.skill,
      experience: newWeights.experience != null ? Number(newWeights.experience) : currentWeightsStore.experience,
      performance: newWeights.performance != null ? Number(newWeights.performance) : currentWeightsStore.performance,
      load: newWeights.load != null ? Number(newWeights.load) : currentWeightsStore.load,
      interest: newWeights.interest != null ? Number(newWeights.interest) : currentWeightsStore.interest,
    }
  }
}

export function calculateFitScore(breakdown, weights = currentWeightsStore) {
  if (!breakdown) return 0
  const skill = Number(breakdown.skill) || 0
  const experience = Number(breakdown.experience) || 0
  const performance = Number(breakdown.performance) || 0
  const load = Number(breakdown.load) || 0
  const interest = Number(breakdown.interest) || 0

  // Weights are stored as integers 0-100 (summing to 100) or floats 0.0-1.0
  const normWeight = (w) => (w > 1 ? w / 100 : w)

  const wSkill = normWeight(weights.skill ?? DEFAULT_WEIGHTS.skill)
  const wExp = normWeight(weights.experience ?? DEFAULT_WEIGHTS.experience)
  const wPerf = normWeight(weights.performance ?? DEFAULT_WEIGHTS.performance)
  const wLoad = normWeight(weights.load ?? DEFAULT_WEIGHTS.load)
  const wInt = normWeight(weights.interest ?? DEFAULT_WEIGHTS.interest)

  const total =
    wSkill * skill +
    wExp * experience +
    wPerf * performance +
    wLoad * load +
    wInt * interest

  return Math.round(total * 100)
}
