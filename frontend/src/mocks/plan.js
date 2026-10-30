import { calculateFitScore } from '../utils/scoring.js'

/**
 * Initial assignment plans for mock mode.
 * evt-1 has a generated plan (starts as "draft").
 * evt-2, evt-3, evt-4 have status "none" so the empty state shows.
 */

const evt1AssignmentsRaw = [
  {
    id: 'asg-1',
    taskId: 'tsk-1',
    taskTitle: 'Stage Management & Mic Setup',
    volunteerId: 'vol-1',
    volunteerName: 'Aarav Sharma',
    breakdown: {
      skill: 0.95,
      experience: 0.9,
      performance: 0.85,
      load: 0.8,
      interest: 0.9,
    },
    reason: 'Strong audio board operation skill with 4 past events leadership experience.',
    flags: [],
    locked: false,
  },
  {
    id: 'asg-2',
    taskId: 'tsk-2',
    taskTitle: 'Hackathon Registration & Kit Desk',
    volunteerId: 'vol-2',
    volunteerName: 'Ananya Iyer',
    breakdown: {
      skill: 0.92,
      experience: 0.75,
      performance: 0.85,
      load: 0.9,
      interest: 0.8,
    },
    reason: 'Expert registration and badging rating; aligns with morning availability slot.',
    flags: [],
    locked: false,
  },
  {
    id: 'asg-3',
    taskId: 'tsk-3',
    taskTitle: 'Robotics Arena Flow & Crowd Control',
    volunteerId: 'vol-4',
    volunteerName: 'Pooja Kulkarni',
    breakdown: {
      skill: 0.85,
      experience: 0.7,
      performance: 0.75,
      load: 0.85,
      interest: 0.7,
    },
    reason: 'Solid logistics track record; optimal proximity to Indoor Sports Complex.',
    flags: [],
    locked: false,
  },
  {
    id: 'asg-4',
    taskId: 'tsk-4',
    taskTitle: 'Emergency Response & Medical Booth Support',
    volunteerId: 'vol-5',
    volunteerName: 'Vikram Malhotra',
    breakdown: {
      skill: 0.65,
      experience: 0.4,
      performance: 0.6,
      load: 0.7,
      interest: 0.5,
    },
    reason: 'Allocated based on general task support availability.',
    flags: ['First-timer'],
    locked: false,
  },
]

// Compute fit dynamically via calculateFitScore
const evt1Assignments = evt1AssignmentsRaw.map((a) => ({
  ...a,
  fit: calculateFitScore(a.breakdown),
}))

export function computePlanSummary(assignments, skillGaps, totalHeadcountRequired = 14) {
  const avgFit =
    assignments.length > 0
      ? Math.round(assignments.reduce((sum, a) => sum + a.fit, 0) / assignments.length)
      : 0

  return {
    averageFit: avgFit,
    unqualifiedAssignments: 0, // must be 0 per requirement
    workloadSpread: '4.5 hrs / vol',
    headcountFilled: assignments.length,
    headcountRequired: totalHeadcountRequired,
    skillGapCount: skillGaps.length,
  }
}

const evt1SkillGaps = [
  {
    taskId: 'tsk-4',
    taskTitle: 'Emergency Response & Medical Booth Support',
    missingSkill: 'First Aid Certified',
    slotsUnfilled: 1,
  },
]

export const initialPlans = [
  {
    eventId: 'evt-1',
    status: 'draft',
    summary: computePlanSummary(evt1Assignments, evt1SkillGaps, 14),
    assignments: evt1Assignments,
    skillGaps: evt1SkillGaps,
  },
  {
    eventId: 'evt-2',
    status: 'none',
    summary: {
      averageFit: 0,
      unqualifiedAssignments: 0,
      workloadSpread: '--',
      headcountFilled: 0,
      headcountRequired: 0,
      skillGapCount: 0,
    },
    assignments: [],
    skillGaps: [],
  },
  {
    eventId: 'evt-3',
    status: 'none',
    summary: {
      averageFit: 0,
      unqualifiedAssignments: 0,
      workloadSpread: '--',
      headcountFilled: 0,
      headcountRequired: 0,
      skillGapCount: 0,
    },
    assignments: [],
    skillGaps: [],
  },
  {
    eventId: 'evt-4',
    status: 'none',
    summary: {
      averageFit: 0,
      unqualifiedAssignments: 0,
      workloadSpread: '--',
      headcountFilled: 0,
      headcountRequired: 0,
      skillGapCount: 0,
    },
    assignments: [],
    skillGaps: [],
  },
]

export function createEmptyPlan(eventId) {
  return {
    eventId,
    status: 'none',
    summary: {
      averageFit: 0,
      unqualifiedAssignments: 0,
      workloadSpread: '--',
      headcountFilled: 0,
      headcountRequired: 0,
      skillGapCount: 0,
    },
    assignments: [],
    skillGaps: [],
  }
}
