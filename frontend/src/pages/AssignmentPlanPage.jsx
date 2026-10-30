import { useState, useCallback, useMemo } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getPlan, runSolver, approvePlan, swapAssignment, getVolunteers } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import { DEFAULT_WEIGHTS } from '../utils/scoring'

export default function AssignmentPlanPage() {
  const { role } = useAuth()
  const { events, selectedEventId, setSelectedEventId } = useEvent()

  // Fetch plan for selected event
  const fetchPlanCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve(null)
    return getPlan(selectedEventId)
  }, [selectedEventId])

  const { data: plan, loading, error, reload } = useFetch(fetchPlanCallback)

  // Fetch volunteers for swap modal
  const fetchVolunteersCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve([])
    return getVolunteers(selectedEventId)
  }, [selectedEventId])

  const { data: volunteers } = useFetch(fetchVolunteersCallback)

  // Local state for actions, drawer, modal
  const [isRunningSolver, setIsRunningSolver] = useState(false)
  const [isApproving, setIsApproving] = useState(false)
  const [actionError, setActionError] = useState('')
  const [showApproveConfirm, setShowApproveConfirm] = useState(false)

  // Drawer explainability state
  const [selectedAssignmentForDrawer, setSelectedAssignmentForDrawer] = useState(null)

  // Swap modal state
  const [swapModalAssignment, setSwapModalAssignment] = useState(null)
  const [selectedVolunteerForSwap, setSelectedVolunteerForSwap] = useState('')
  const [isSwapping, setIsSwapping] = useState(false)

  // Candidate volunteers for swap modal (excluding currently assigned)
  const candidateVolunteers = useMemo(() => {
    if (!volunteers || !swapModalAssignment) return []
    return volunteers.filter((v) => v.id !== swapModalAssignment.volunteerId)
  }, [volunteers, swapModalAssignment])

  // Redirect volunteers away (role protection: organiser/coordinator only)
  if (role === 'volunteer') {
    return <Navigate to="/events" replace />
  }

  const status = plan?.status || 'none'
  const isApproved = status === 'approved' || status === 'published'
  const isDraft = status === 'draft'

  // Trigger solver execution
  const handleRunSolver = async () => {
    if (!selectedEventId || isRunningSolver || isApproved) return
    setActionError('')
    setIsRunningSolver(true)
    try {
      await runSolver(selectedEventId)
      await reload()
    } catch (err) {
      setActionError(err.message || 'Failed to run solver')
    } finally {
      setIsRunningSolver(false)
    }
  }

  // Trigger approval
  const handleConfirmApprove = async () => {
    if (!selectedEventId || isApproving || !isDraft) return
    setActionError('')
    setIsApproving(true)
    try {
      await approvePlan(selectedEventId)
      setShowApproveConfirm(false)
      await reload()
    } catch (err) {
      setActionError(err.message || 'Failed to approve plan')
    } finally {
      setIsApproving(false)
    }
  }

  // Open Swap Modal
  const handleOpenSwapModal = (assignment) => {
    if (isApproved) return
    setSwapModalAssignment(assignment)
    setSelectedVolunteerForSwap('')
    setActionError('')
  }

  // Confirm Swap
  const handleConfirmSwap = async () => {
    if (!swapModalAssignment || !selectedVolunteerForSwap || isSwapping) return
    setIsSwapping(true)
    setActionError('')
    try {
      await swapAssignment(selectedEventId, swapModalAssignment.id, selectedVolunteerForSwap)
      setSwapModalAssignment(null)
      setSelectedVolunteerForSwap('')
      await reload()
    } catch (err) {
      setActionError(err.message || 'Failed to swap volunteer')
    } finally {
      setIsSwapping(false)
    }
  }

  // Helper for fit score pill color & bar using @theme tokens
  const getFitScoreTheme = (fit) => {
    if (fit >= 75) {
      return {
        text: 'text-secondary font-semibold',
        bar: 'bg-secondary',
        bg: 'bg-secondary-container/30',
        badge: 'bg-secondary-container text-secondary',
      }
    }
    if (fit >= 50) {
      return {
        text: 'text-tertiary font-semibold',
        bar: 'bg-tertiary',
        bg: 'bg-tertiary-fixed/30',
        badge: 'bg-tertiary-fixed text-on-tertiary-fixed',
      }
    }
    return {
      text: 'text-error font-semibold',
      bar: 'bg-error',
      bg: 'bg-error-container/30',
      badge: 'bg-error-container text-on-error-container',
    }
  }

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Header & Operational Bar */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-xs">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <span className="font-label-sm text-label-sm text-outline tracking-wider uppercase">
                Optimization Engine
              </span>
              <span className="font-body-sm text-body-sm text-outline">/</span>
              <span className="font-label-sm text-label-sm text-primary font-semibold">
                Allocation Matrix
              </span>
            </div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight mt-0.5">
              Assignment Plan
            </h1>
          </div>

          {/* Action buttons and Stepper */}
          {selectedEventId && (
            <div className="flex flex-wrap items-center gap-space-md">
              {/* Stepper reflecting lifecycle status */}
              <div className="flex items-center p-1 rounded-xl bg-surface-container-low shadow-sm">
                <div
                  className={`flex items-center gap-1.5 px-3 py-1 rounded-lg text-on-surface ${
                    status === 'none' ? 'bg-surface-container-lowest shadow-sm font-semibold' : 'opacity-60'
                  }`}
                >
                  <span
                    className={`w-2 h-2 rounded-full ${status === 'none' ? 'bg-outline' : 'bg-outline-variant'}`}
                  />
                  <span className="font-label-sm text-label-sm">No plan</span>
                </div>
                <div
                  className={`flex items-center gap-1.5 px-2.5 py-1 ${
                    status === 'draft' ? 'bg-surface-container-lowest rounded-lg shadow-sm font-semibold text-primary' : 'text-on-surface-variant opacity-60'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      status === 'draft' ? 'bg-primary' : 'bg-outline-variant'
                    }`}
                  />
                  <span className="font-label-sm text-label-sm">Draft</span>
                </div>
                <div
                  className={`flex items-center gap-1.5 px-2.5 py-1 ${
                    status === 'approved' ? 'bg-surface-container-lowest rounded-lg shadow-sm font-semibold text-secondary' : 'text-on-surface-variant opacity-60'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      status === 'approved' ? 'bg-secondary' : 'bg-outline-variant'
                    }`}
                  />
                  <span className="font-label-sm text-label-sm">Approved</span>
                </div>
                <div
                  className={`flex items-center gap-1.5 px-2.5 py-1 ${
                    status === 'published' ? 'bg-surface-container-lowest rounded-lg shadow-sm font-semibold text-secondary' : 'text-on-surface-variant opacity-60'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      status === 'published' ? 'bg-secondary' : 'bg-outline-variant'
                    }`}
                  />
                  <span className="font-label-sm text-label-sm">Published</span>
                </div>
              </div>

              {/* Run Solver Button */}
              <button
                onClick={handleRunSolver}
                disabled={isRunningSolver || isApproved}
                className="flex items-center gap-2 h-10 px-5 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg shadow-md hover:shadow-lg transition-all active:scale-[0.99] cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">
                  {isRunningSolver ? 'sync' : 'bolt'}
                </span>
                <span>{isRunningSolver ? 'Solving...' : 'Run solver'}</span>
              </button>

              {/* Approve Plan Button */}
              <button
                onClick={() => setShowApproveConfirm(true)}
                disabled={!isDraft || isApproving}
                className="flex items-center gap-2 h-10 px-5 rounded-lg bg-secondary hover:bg-on-secondary-container text-on-secondary font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">verified</span>
                <span>{isApproved ? 'Approved' : 'Approve plan'}</span>
              </button>
            </div>
          )}
        </div>

        {/* Global Error Banner */}
        {actionError && (
          <div className="bg-error-container text-on-error-container p-4 rounded-xl flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined">error</span>
              <span className="font-body-md text-body-md">{actionError}</span>
            </div>
            <button
              onClick={() => setActionError('')}
              className="text-on-error-container hover:opacity-75 font-label-md"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Empty state: No event selected in topbar */}
        {!selectedEventId && (
          <section className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">event_available</span>
            </div>
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight mb-space-xs">
              Select an Event
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
              Choose an event from the topbar or select one below to inspect or generate its assignment plan.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-space-sm max-w-lg">
              {events?.map((evt) => (
                <button
                  key={evt.id}
                  onClick={() => setSelectedEventId(evt.id)}
                  className="px-space-md py-space-xs rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer border border-outline-variant/30"
                >
                  {evt.name}
                </button>
              ))}
            </div>
          </section>
        )}

        {/* Event is selected */}
        {selectedEventId && (
          <>
            {/* Loading Spinner for initial fetch */}
            {loading && !plan && (
              <div className="bg-surface-container-lowest rounded-xl p-16 flex flex-col items-center justify-center text-center shadow-sm">
                <div className="w-10 h-10 border-4 border-surface-container border-t-primary rounded-full animate-spin mb-3" />
                <p className="font-body-md text-body-md text-on-surface-variant">Loading plan details...</p>
              </div>
            )}

            {/* Error loading plan */}
            {error && (
              <div className="bg-error-container text-on-error-container p-4 rounded-xl flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined">error</span>
                  <span className="font-body-md text-body-md">{error}</span>
                </div>
                <button
                  onClick={reload}
                  className="px-3 py-1 bg-surface-container-lowest rounded-lg font-label-md text-label-md text-on-surface shadow-sm cursor-pointer"
                >
                  Retry
                </button>
              </div>
            )}

            {/* 4 Summary Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
              {/* Metric 1: Average Fit Score */}
              <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex flex-col justify-between">
                <div className="flex items-start justify-between">
                  <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
                    Average fit score
                  </span>
                  <span className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-primary">
                    <span className="material-symbols-outlined text-[18px]">query_stats</span>
                  </span>
                </div>
                <div className="mt-space-md flex flex-col">
                  <div className="flex items-baseline gap-space-sm">
                    <span className="font-headline-xl text-headline-xl text-on-surface font-bold tracking-tight">
                      {status !== 'none' && plan?.summary?.averageFit ? `${plan.summary.averageFit}` : '--'}
                    </span>
                    <span className="font-label-sm text-label-sm text-outline">%</span>
                  </div>
                  <div className="flex items-center gap-1.5 mt-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span className="w-1.5 h-1.5 rounded-full bg-outline" />
                    <span>Target &gt;= 80%</span>
                  </div>
                </div>
                <div className="w-full bg-surface-container-low h-1 rounded-full mt-3 overflow-hidden">
                  <div
                    className="bg-primary h-full transition-all duration-500"
                    style={{ width: status !== 'none' && plan?.summary?.averageFit ? `${plan.summary.averageFit}%` : '0%' }}
                  />
                </div>
              </div>

              {/* Metric 2: Unqualified assignments */}
              <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex flex-col justify-between">
                <div className="flex items-start justify-between">
                  <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
                    Unqualified assignments
                  </span>
                  <span className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-secondary">
                    <span className="material-symbols-outlined text-[18px]">verified_user</span>
                  </span>
                </div>
                <div className="mt-space-md flex flex-col">
                  <div className="flex items-baseline gap-space-sm">
                    <span className="font-headline-xl text-headline-xl text-on-surface font-bold tracking-tight">
                      {status !== 'none' ? plan?.summary?.unqualifiedAssignments ?? 0 : '--'}
                    </span>
                    <span className="font-label-sm text-label-sm text-outline">conflicts</span>
                  </div>
                  <div className="flex items-center gap-1.5 mt-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
                    <span>Hard constraints</span>
                  </div>
                </div>
                <div className="w-full bg-surface-container-low h-1 rounded-full mt-3 overflow-hidden">
                  <div className="bg-secondary h-full w-0" />
                </div>
              </div>

              {/* Metric 3: Max Shift Workload */}
              <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex flex-col justify-between">
                <div className="flex items-start justify-between">
                  <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
                    Workload spread
                  </span>
                  <span className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-tertiary">
                    <span className="material-symbols-outlined text-[18px]">timer</span>
                  </span>
                </div>
                <div className="mt-space-md flex flex-col">
                  <div className="flex items-baseline gap-space-sm">
                    <span className="font-headline-xl text-headline-xl text-on-surface font-bold tracking-tight">
                      {status !== 'none' ? plan?.summary?.workloadSpread || '4.5 hrs / vol' : '--'}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 mt-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span className="w-1.5 h-1.5 rounded-full bg-outline" />
                    <span>Target: 4h - 8h</span>
                  </div>
                </div>
                <div className="w-full bg-surface-container-low h-1 rounded-full mt-3 overflow-hidden">
                  <div className="bg-tertiary h-full w-full" />
                </div>
              </div>

              {/* Metric 4: Headcount filled */}
              <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex flex-col justify-between">
                <div className="flex items-start justify-between">
                  <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
                    Headcount filled
                  </span>
                  <span className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-primary-container">
                    <span className="material-symbols-outlined text-[18px]">groups</span>
                  </span>
                </div>
                <div className="mt-space-md flex flex-col">
                  <div className="flex items-baseline gap-space-sm">
                    <span className="font-headline-xl text-headline-xl text-on-surface font-bold tracking-tight">
                      {status !== 'none' ? plan?.summary?.headcountFilled ?? '--' : '--'}
                    </span>
                    <span className="font-label-sm text-label-sm text-outline">
                      / {status !== 'none' ? plan?.summary?.headcountRequired ?? '--' : '--'}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 mt-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span className="w-1.5 h-1.5 rounded-full bg-outline" />
                    <span>
                      {status !== 'none' && plan?.summary?.headcountRequired
                        ? `${plan.summary.headcountFilled} of ${plan.summary.headcountRequired} positions allocated`
                        : 'Solver not executed'}
                    </span>
                  </div>
                </div>
                <div className="w-full bg-surface-container-low h-1 rounded-full mt-3 overflow-hidden">
                  <div
                    className="bg-primary-container h-full transition-all duration-500"
                    style={{
                      width:
                        status !== 'none' && plan?.summary?.headcountRequired
                          ? `${Math.min(100, Math.round((plan.summary.headcountFilled / plan.summary.headcountRequired) * 100))}%`
                          : '0%',
                    }}
                  />
                </div>
              </div>
            </div>

            {/* Empty State Banner when status is none */}
            {status === 'none' && (
              <div className="bg-surface-container-lowest rounded-2xl p-space-xl lg:p-10 shadow-sm flex flex-col items-center text-center">
                <div className="w-20 h-20 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary shadow-sm mb-space-lg">
                  <span className="material-symbols-outlined text-[44px]">splitscreen</span>
                </div>
                <h2 className="font-headline-lg text-headline-lg text-on-surface max-w-xl font-bold">
                  No plan yet. Run the solver to generate assignments
                </h2>
                <p className="font-body-lg text-body-lg text-on-surface-variant max-w-2xl mt-space-sm">
                  The algorithmic solver balances volunteer availability, skill ratings, fatigue limits,
                  and role priorities to create an optimal schedule.
                </p>
                <div className="flex items-center justify-center gap-space-md mt-space-xl">
                  <button
                    onClick={handleRunSolver}
                    disabled={isRunningSolver}
                    className="h-10 px-6 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm flex items-center gap-2 transition-all cursor-pointer disabled:opacity-50"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[18px]">play_arrow</span>
                    <span>{isRunningSolver ? 'Generating Plan...' : 'Run solver'}</span>
                  </button>
                </div>
              </div>
            )}

            {/* Active Plan Content: Bento Layout (Assignments Table + Skill Gaps + Drawer) */}
            {status !== 'none' && (
              <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-xl items-start">
                {/* Left Area (8 cols if drawer open, or 8 cols default) */}
                <div className="xl:col-span-8 flex flex-col gap-space-xl">
                  {/* Skill Gaps Panel */}
                  <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                    <div className="px-5 py-4 bg-surface-container-low flex items-center justify-between border-b border-surface-container/50">
                      <div className="flex items-center gap-space-sm">
                        <span className="material-symbols-outlined text-outline text-[20px]">psychology</span>
                        <h2 className="font-headline-sm text-headline-sm text-on-surface">Skill Gaps</h2>
                      </div>
                      <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface font-label-sm text-label-sm font-semibold">
                        {plan?.skillGaps?.length || 0}
                      </span>
                    </div>

                    <div className="p-space-lg">
                      {plan?.skillGaps && plan.skillGaps.length > 0 ? (
                        <div className="flex flex-col gap-3">
                          {plan.skillGaps.map((sg, idx) => (
                            <div
                              key={idx}
                              className="p-4 rounded-xl bg-surface-container-low flex items-start justify-between gap-4"
                            >
                              <div className="flex items-start gap-3">
                                <span className="material-symbols-outlined text-tertiary text-[22px] shrink-0 mt-0.5">
                                  warning
                                </span>
                                <div className="flex flex-col">
                                  <span className="font-label-lg text-label-lg text-on-surface font-semibold">
                                    {sg.taskTitle}
                                  </span>
                                  <span className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                                    Missing required skill: <strong className="text-on-surface">{sg.missingSkill}</strong>
                                  </span>
                                </div>
                              </div>
                              <span className="px-2.5 py-1 rounded-full font-label-sm text-label-sm bg-surface-container-highest text-on-surface font-semibold shrink-0">
                                {sg.slotsUnfilled} slot{sg.slotsUnfilled === 1 ? '' : 's'} unfilled
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="py-6 text-center text-on-surface-variant font-body-md text-body-md">
                          No skill gaps detected. All mandatory requirements are fulfilled.
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Assignments Table Card */}
                  <div className="bg-surface-container-lowest rounded-2xl shadow-sm overflow-hidden">
                    <div className="px-6 py-4 flex items-center justify-between border-b border-surface-container/50">
                      <div className="flex items-center gap-space-sm">
                        <span className="material-symbols-outlined text-primary text-[22px]">assignment_ind</span>
                        <h2 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                          Allocated Assignments
                        </h2>
                      </div>
                      <span className="px-2.5 py-0.5 rounded-full bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm">
                        {plan?.assignments?.length || 0} active
                      </span>
                    </div>

                    {/* Table View */}
                    <div className="overflow-x-auto">
                      <table className="w-full text-left border-collapse">
                        <thead>
                          <tr className="border-b border-surface-container/60 bg-surface-container-low/40">
                            <th className="py-3 px-4 font-label-sm text-label-sm text-outline uppercase tracking-wider">
                              Task
                            </th>
                            <th className="py-3 px-4 font-label-sm text-label-sm text-outline uppercase tracking-wider">
                              Assigned Volunteer
                            </th>
                            <th className="py-3 px-4 font-label-sm text-label-sm text-outline uppercase tracking-wider">
                              Fit Score
                            </th>
                            <th className="py-3 px-4 font-label-sm text-label-sm text-outline uppercase tracking-wider">
                              Flags
                            </th>
                            <th className="py-3 px-4 font-label-sm text-label-sm text-outline uppercase tracking-wider text-right">
                              Actions
                            </th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-surface-container/40">
                          {plan?.assignments?.map((asg) => {
                            const fitStyle = getFitScoreTheme(asg.fit)
                            return (
                              <tr
                                key={asg.id}
                                className="hover:bg-surface-container-low/30 transition-colors"
                              >
                                <td className="py-4 px-4">
                                  <span className="font-label-md text-label-md text-on-surface font-medium block">
                                    {asg.taskTitle}
                                  </span>
                                </td>

                                <td className="py-4 px-4">
                                  <div className="flex items-center gap-2">
                                    <div className="w-7 h-7 rounded-full bg-surface-container-high flex items-center justify-center text-on-surface text-[12px] font-semibold">
                                      {asg.volunteerName.charAt(0)}
                                    </div>
                                    <span className="font-body-md text-body-md text-on-surface">
                                      {asg.volunteerName}
                                    </span>
                                  </div>
                                </td>

                                <td className="py-4 px-4">
                                  <div className="flex items-center gap-2.5 min-w-[130px]">
                                    <span className={`font-label-sm text-label-sm ${fitStyle.text} w-8`}>
                                      {asg.fit}%
                                    </span>
                                    <div className="w-20 bg-surface-container-low h-2 rounded-full overflow-hidden">
                                      <div
                                        className={`h-full ${fitStyle.bar}`}
                                        style={{ width: `${asg.fit}%` }}
                                      />
                                    </div>
                                  </div>
                                </td>

                                <td className="py-4 px-4">
                                  {asg.flags && asg.flags.length > 0 ? (
                                    <div className="flex items-center gap-1">
                                      {asg.flags.map((flag, i) => (
                                        <span
                                          key={i}
                                          className="px-2 py-0.5 rounded-full font-label-sm text-label-sm bg-tertiary-fixed text-on-tertiary-fixed font-semibold"
                                        >
                                          {flag}
                                        </span>
                                      ))}
                                    </div>
                                  ) : (
                                    <span className="text-outline text-body-sm">--</span>
                                  )}
                                </td>

                                <td className="py-4 px-4 text-right">
                                  <div className="flex items-center justify-end gap-2">
                                    <button
                                      onClick={() => setSelectedAssignmentForDrawer(asg)}
                                      className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-primary font-label-sm text-label-sm transition-colors cursor-pointer flex items-center gap-1"
                                      type="button"
                                    >
                                      <span className="material-symbols-outlined text-[16px]">visibility</span>
                                      <span>Explain</span>
                                    </button>

                                    {!isApproved && (
                                      <button
                                        onClick={() => handleOpenSwapModal(asg)}
                                        className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-sm text-label-sm transition-colors cursor-pointer flex items-center gap-1"
                                        type="button"
                                      >
                                        <span className="material-symbols-outlined text-[16px]">swap_horiz</span>
                                        <span>Swap</span>
                                      </button>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>

                {/* Right Area (4 cols): Explainability Drawer */}
                <aside className="xl:col-span-4 bg-surface-container-lowest rounded-2xl p-space-xl shadow-sm flex flex-col gap-space-lg sticky top-20">
                  <div className="flex items-start justify-between gap-space-sm pb-space-md border-b border-surface-container/50">
                    <div className="flex flex-col">
                      <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-primary text-[22px]">auto_awesome</span>
                        <h3 className="font-headline-md text-headline-md text-on-surface font-bold tracking-tight">
                          Why this pick?
                        </h3>
                      </div>
                      <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider font-semibold mt-1">
                        Explainability Breakdown
                      </span>
                    </div>
                    {selectedAssignmentForDrawer && (
                      <button
                        onClick={() => setSelectedAssignmentForDrawer(null)}
                        className="text-outline hover:text-on-surface"
                      >
                        <span className="material-symbols-outlined text-[18px]">close</span>
                      </button>
                    )}
                  </div>

                  {selectedAssignmentForDrawer ? (
                    <div className="flex flex-col gap-space-md">
                      {/* Active target banner */}
                      <div className="p-space-md rounded-xl bg-surface-container-low flex items-center gap-space-md">
                        <div className="w-8 h-8 rounded-full bg-surface-container-highest flex items-center justify-center text-on-surface font-label-md shrink-0">
                          <span className="material-symbols-outlined text-[16px]">person</span>
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="font-label-sm text-label-sm text-outline font-semibold">
                            Inspecting Assignment
                          </span>
                          <span className="font-label-md text-label-md text-on-surface truncate font-medium">
                            {selectedAssignmentForDrawer.volunteerName} → {selectedAssignmentForDrawer.taskTitle}
                          </span>
                        </div>
                      </div>

                      {/* Overall Fit Total */}
                      <div className="p-space-md rounded-xl bg-surface-container-low flex items-center justify-between">
                        <span className="font-label-md text-label-md text-on-surface font-semibold">
                          Total Fit Score
                        </span>
                        <span className={`font-headline-sm text-headline-sm ${getFitScoreTheme(selectedAssignmentForDrawer.fit).text}`}>
                          {selectedAssignmentForDrawer.fit}%
                        </span>
                      </div>

                      {/* 5 Factors Breakdown */}
                      <div className="flex flex-col gap-2.5">
                        {/* Factor 1: Skill */}
                        <div className="p-3 rounded-xl bg-surface-container-low flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <span className="font-label-md text-label-md text-on-surface">
                              Skill match ({Math.round(DEFAULT_WEIGHTS.skill * 100)}%)
                            </span>
                            <span className="font-label-sm text-label-sm font-bold text-on-surface">
                              {Math.round(selectedAssignmentForDrawer.breakdown.skill * 100)}%
                            </span>
                          </div>
                          <div className="w-full bg-surface-container h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-primary h-full"
                              style={{ width: `${selectedAssignmentForDrawer.breakdown.skill * 100}%` }}
                            />
                          </div>
                        </div>

                        {/* Factor 2: Experience */}
                        <div className="p-3 rounded-xl bg-surface-container-low flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <span className="font-label-md text-label-md text-on-surface">
                              Experience ({Math.round(DEFAULT_WEIGHTS.experience * 100)}%)
                            </span>
                            <span className="font-label-sm text-label-sm font-bold text-on-surface">
                              {Math.round(selectedAssignmentForDrawer.breakdown.experience * 100)}%
                            </span>
                          </div>
                          <div className="w-full bg-surface-container h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-primary h-full"
                              style={{ width: `${selectedAssignmentForDrawer.breakdown.experience * 100}%` }}
                            />
                          </div>
                        </div>

                        {/* Factor 3: Performance */}
                        <div className="p-3 rounded-xl bg-surface-container-low flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <span className="font-label-md text-label-md text-on-surface">
                              Past performance ({Math.round(DEFAULT_WEIGHTS.performance * 100)}%)
                            </span>
                            <span className="font-label-sm text-label-sm font-bold text-on-surface">
                              {Math.round(selectedAssignmentForDrawer.breakdown.performance * 100)}%
                            </span>
                          </div>
                          <div className="w-full bg-surface-container h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-primary h-full"
                              style={{ width: `${selectedAssignmentForDrawer.breakdown.performance * 100}%` }}
                            />
                          </div>
                        </div>

                        {/* Factor 4: Load */}
                        <div className="p-3 rounded-xl bg-surface-container-low flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <span className="font-label-md text-label-md text-on-surface">
                              Workload balance ({Math.round(DEFAULT_WEIGHTS.load * 100)}%)
                            </span>
                            <span className="font-label-sm text-label-sm font-bold text-on-surface">
                              {Math.round(selectedAssignmentForDrawer.breakdown.load * 100)}%
                            </span>
                          </div>
                          <div className="w-full bg-surface-container h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-primary h-full"
                              style={{ width: `${selectedAssignmentForDrawer.breakdown.load * 100}%` }}
                            />
                          </div>
                        </div>

                        {/* Factor 5: Interest */}
                        <div className="p-3 rounded-xl bg-surface-container-low flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <span className="font-label-md text-label-md text-on-surface">
                              Role interest ({Math.round(DEFAULT_WEIGHTS.interest * 100)}%)
                            </span>
                            <span className="font-label-sm text-label-sm font-bold text-on-surface">
                              {Math.round(selectedAssignmentForDrawer.breakdown.interest * 100)}%
                            </span>
                          </div>
                          <div className="w-full bg-surface-container h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-primary h-full"
                              style={{ width: `${selectedAssignmentForDrawer.breakdown.interest * 100}%` }}
                            />
                          </div>
                        </div>
                      </div>

                      {/* Rationale explanation */}
                      {selectedAssignmentForDrawer.reason && (
                        <div className="p-3 rounded-xl bg-surface-container-low/70 border border-surface-container">
                          <span className="font-label-sm text-label-sm text-outline block mb-1">
                            Rationale
                          </span>
                          <p className="font-body-sm text-body-sm text-on-surface-variant">
                            {selectedAssignmentForDrawer.reason}
                          </p>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="py-12 flex flex-col items-center justify-center text-center text-on-surface-variant">
                      <span className="material-symbols-outlined text-[36px] text-outline mb-2">
                        touch_app
                      </span>
                      <p className="font-body-sm text-body-sm max-w-xs">
                        Click <strong>Explain</strong> on any assignment row to view its algorithmic factor breakdown and scoring weights.
                      </p>
                    </div>
                  )}
                </aside>
              </div>
            )}
          </>
        )}

        {/* Modal: Confirm Plan Approval */}
        {showApproveConfirm && (
          <div className="fixed inset-0 z-50 bg-on-surface/40 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-xl max-w-md w-full flex flex-col gap-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-secondary-container flex items-center justify-center text-secondary shrink-0">
                  <span className="material-symbols-outlined text-[24px]">verified</span>
                </div>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  Approve Assignment Plan
                </h3>
              </div>

              <p className="font-body-md text-body-md text-on-surface-variant">
                Approve this plan? This cannot be undone. Once approved, the plan will be locked for editing and marked as confirmed.
              </p>

              <div className="flex items-center justify-end gap-3 mt-2">
                <button
                  type="button"
                  onClick={() => setShowApproveConfirm(false)}
                  disabled={isApproving}
                  className="px-4 h-10 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md cursor-pointer transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmApprove}
                  disabled={isApproving}
                  className="px-5 h-10 rounded-lg bg-secondary hover:bg-on-secondary-container text-on-secondary font-label-md text-label-md shadow-sm cursor-pointer transition-all disabled:opacity-50"
                >
                  {isApproving ? 'Approving...' : 'Yes, Approve'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Modal: Swap Volunteer */}
        {swapModalAssignment && (
          <div className="fixed inset-0 z-50 bg-on-surface/40 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-xl max-w-lg w-full flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-surface-container/60 pb-3">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-primary text-[22px]">swap_horiz</span>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                    Swap Volunteer
                  </h3>
                </div>
                <button
                  onClick={() => setSwapModalAssignment(null)}
                  className="text-outline hover:text-on-surface cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[20px]">close</span>
                </button>
              </div>

              <div className="flex flex-col gap-1">
                <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                  Target Task
                </span>
                <span className="font-label-lg text-label-lg text-on-surface font-semibold">
                  {swapModalAssignment.taskTitle}
                </span>
                <span className="font-body-sm text-body-sm text-on-surface-variant">
                  Currently assigned: <strong>{swapModalAssignment.volunteerName}</strong>
                </span>
              </div>

              <div className="flex flex-col gap-2 mt-2">
                <label className="font-label-md text-label-md text-on-surface">
                  Select Replacement Volunteer
                </label>
                <select
                  value={selectedVolunteerForSwap}
                  onChange={(e) => setSelectedVolunteerForSwap(e.target.value)}
                  className="w-full h-10 px-3 bg-surface-container-low rounded-lg font-body-md text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
                >
                  <option value="">-- Choose a volunteer --</option>
                  {candidateVolunteers.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.fullName} ({v.department || 'General'}) · {v.skills?.map((s) => s.name).join(', ') || 'No skills listed'}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center justify-end gap-3 mt-4">
                <button
                  type="button"
                  onClick={() => setSwapModalAssignment(null)}
                  disabled={isSwapping}
                  className="px-4 h-10 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md cursor-pointer transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmSwap}
                  disabled={!selectedVolunteerForSwap || isSwapping}
                  className="px-5 h-10 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md shadow-sm cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isSwapping ? 'Swapping...' : 'Confirm Swap'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
