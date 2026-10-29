/**
 * Mock dashboard summary factory.
 * Provides the structure for event summary metrics, alerts, plan status, and workload distribution.
 */

export function createEmptyDashboardSummary() {
  return {
    volunteersRegistered: 0,
    activeTasks: 0,
    headcountFilled: null,
    headcountRequired: null,
    openSkillGaps: null,
    alerts: [],
    planStatus: 'none', // 'none' | 'draft' | 'approved' | 'published'
    workloadPerVolunteer: [],
  }
}
