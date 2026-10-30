import { useState, useCallback } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getDashboardSummary } from '../services/api'
import { useFetch } from '../hooks/useFetch'

export default function DashboardPage() {
  const { role } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent, updateRegistrationLink } = useEvent()
  const navigate = useNavigate()

  // Track the event ID whose URL is currently in state
  const [syncedEventId, setSyncedEventId] = useState(selectedEventId)
  const [formUrlInput, setFormUrlInput] = useState(selectedEvent?.registrationFormUrl || '')
  const [formSaveStatus, setFormSaveStatus] = useState(null) // null | 'saved' | 'cleared'
  const [formError, setFormError] = useState('')
  const [isSavingForm, setIsSavingForm] = useState(false)

  // Sync state if selectedEventId changed during render
  if (selectedEventId !== syncedEventId) {
    setSyncedEventId(selectedEventId)
    setFormUrlInput(selectedEvent?.registrationFormUrl || '')
    setFormError('')
    setFormSaveStatus(null)
  }

  // Fetch dashboard summary
  const fetchSummaryCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve(null)
    return getDashboardSummary(selectedEventId)
  }, [selectedEventId])

  const { data: summary, loading, error, reload } = useFetch(fetchSummaryCallback)

  // Redirect volunteers away from Dashboard
  if (role === 'volunteer') {
    return <Navigate to="/my-duties" replace />
  }

  // Handle Form URL Save
  const handleSaveFormUrl = async () => {
    setFormError('')
    setFormSaveStatus(null)
    const trimmed = formUrlInput.trim()

    if (!trimmed) {
      setFormError('Please enter a valid URL or click Clear to remove it.')
      return
    }

    if (!/^https:\/\/[^ "]+$/.test(trimmed)) {
      setFormError('Please enter a valid non-empty https:// URL.')
      return
    }

    try {
      setIsSavingForm(true)
      await updateRegistrationLink(selectedEventId, trimmed)
      setFormSaveStatus('saved')
      setTimeout(() => setFormSaveStatus(null), 3000)
    } catch (err) {
      setFormError(err.message || 'Failed to save registration link.')
    } finally {
      setIsSavingForm(false)
    }
  }

  // Handle Form URL Clear
  const handleClearFormUrl = async () => {
    setFormError('')
    setFormSaveStatus(null)
    try {
      setIsSavingForm(true)
      await updateRegistrationLink(selectedEventId, '')
      setFormUrlInput('')
      setFormSaveStatus('cleared')
      setTimeout(() => setFormSaveStatus(null), 3000)
    } catch (err) {
      setFormError(err.message || 'Failed to clear registration link.')
    } finally {
      setIsSavingForm(false)
    }
  }

  // Handle Solver action click
  const handleRunSolver = () => {
    navigate('/assignment-plan', { state: { message: 'Solver not connected yet' } })
  }

  const handleGenerateDraftPlan = () => {
    navigate('/assignment-plan', { state: { message: 'Solver not connected yet' } })
  }

  // Checklist calculations
  const tasksCount = summary?.activeTasks ?? 0
  const volunteersCount = summary?.volunteersRegistered ?? 0
  const isFormLinkSet = Boolean(selectedEvent?.registrationFormUrl && selectedEvent.registrationFormUrl.trim().length > 0)
  const isPreferencesConfigured = false // stays unchecked for now per requirement

  const isReadinessComplete = tasksCount > 0 && volunteersCount > 0 && isFormLinkSet

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Header Banner & Actions */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-space-lg">
          <div className="flex flex-col">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Organiser Dashboard
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant mt-1">
              Overview of tasks, volunteer allocations, and solver status for the selected event.
            </p>
          </div>

          <div className="flex items-center gap-space-md shrink-0">
            {/* Run Solver button with tooltip */}
            <div className="relative group">
              <button
                onClick={handleRunSolver}
                className="flex items-center gap-space-xs px-space-md h-10 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-on-surface transition-colors cursor-pointer shadow-sm active:scale-[0.99]"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">memory</span>
                <span className="font-label-lg text-label-lg">Run Solver</span>
              </button>
            </div>

            <button
              onClick={() => navigate('/tasks')}
              className="flex items-center gap-space-xs px-space-md h-10 rounded-lg bg-primary-container text-on-primary hover:opacity-95 shadow-sm active:scale-[0.99] transition-all cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[20px]">add</span>
              <span className="font-label-lg text-label-lg">Create Task</span>
            </button>
          </div>
        </div>

        {/* Empty state: No event selected */}
        {!selectedEventId && (
          <section className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">event_available</span>
            </div>
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight mb-space-xs">
              Select an Event
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
              Select an event from the topbar or choose one below to view metrics, alerts, and solver readiness.
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

        {/* Selected Event Dashboard Content */}
        {selectedEventId && (
          <>
            {/* Loading state indicator if fetching summary */}
            {loading && !summary && (
              <div className="bg-surface-container-lowest rounded-xl p-12 flex flex-col items-center justify-center text-center shadow-sm">
                <div className="w-10 h-10 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-3" />
                <p className="font-body-md text-body-md text-on-surface-variant">Loading dashboard metrics...</p>
              </div>
            )}

            {/* Error state */}
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

            {/* 4 Stat Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-gutter-lg">
              {/* Stat 1: Volunteers registered */}
              <div className="bg-surface-container-lowest rounded-xl p-5 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <span className="font-label-md text-label-md text-on-surface-variant">
                    Volunteers registered
                  </span>
                  <div className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-outline">
                    <span className="material-symbols-outlined text-[18px]">person_outline</span>
                  </div>
                </div>
                <div className="mt-4">
                  <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight leading-none">
                    {summary?.volunteersRegistered ?? '--'}
                  </span>
                  <p className="font-body-sm text-body-sm text-outline mt-1.5">
                    {summary?.volunteersRegistered > 0
                      ? `${summary.volunteersRegistered} volunteer${summary.volunteersRegistered === 1 ? '' : 's'} registered`
                      : 'No volunteers registered yet'}
                  </p>
                </div>
              </div>

              {/* Stat 2: Tasks */}
              <div className="bg-surface-container-lowest rounded-xl p-5 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <span className="font-label-md text-label-md text-on-surface-variant">Tasks</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-outline">
                    <span className="material-symbols-outlined text-[18px]">assignment_turned_in</span>
                  </div>
                </div>
                <div className="mt-4">
                  <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight leading-none">
                    {summary?.activeTasks ?? '--'}
                  </span>
                  <p className="font-body-sm text-body-sm text-outline mt-1.5">
                    {summary?.activeTasks > 0
                      ? `${summary.activeTasks} active task${summary.activeTasks === 1 ? '' : 's'}`
                      : 'No active tasks created'}
                  </p>
                </div>
              </div>

              {/* Stat 3: Headcount filled */}
              <div className="bg-surface-container-lowest rounded-xl p-5 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <span className="font-label-md text-label-md text-on-surface-variant">
                    Headcount filled
                  </span>
                  <div className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-outline">
                    <span className="material-symbols-outlined text-[18px]">groups_2</span>
                  </div>
                </div>
                <div className="mt-4">
                  <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight leading-none">
                    {summary?.headcountFilled !== null && summary?.headcountFilled !== undefined
                      ? summary.headcountFilled
                      : '--'}
                  </span>
                  <p className="font-body-sm text-body-sm text-outline mt-1.5">
                    {summary?.headcountFilled !== null && summary?.headcountRequired !== null && summary?.headcountFilled !== undefined && summary?.headcountRequired !== undefined
                      ? `${summary.headcountFilled} of ${summary.headcountRequired} positions allocated`
                      : '-- of -- positions allocated'}
                  </p>
                </div>
              </div>

              {/* Stat 4: Open skill gaps */}
              <div className="bg-surface-container-lowest rounded-xl p-5 shadow-sm flex flex-col justify-between">
                <div className="flex items-center justify-between">
                  <span className="font-label-md text-label-md text-on-surface-variant">
                    Open skill gaps
                  </span>
                  <div className="w-8 h-8 rounded-lg bg-surface-container-low flex items-center justify-center text-outline">
                    <span className="material-symbols-outlined text-[18px]">warning_amber</span>
                  </div>
                </div>
                <div className="mt-4">
                  <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight leading-none">
                    {summary?.openSkillGaps !== null && summary?.openSkillGaps !== undefined
                      ? summary.openSkillGaps
                      : '--'}
                  </span>
                  <p className="font-body-sm text-body-sm text-outline mt-1.5">
                    {summary?.openSkillGaps !== null && summary?.openSkillGaps !== undefined
                      ? `${summary.openSkillGaps} gaps detected`
                      : 'Solver not yet executed'}
                  </p>
                </div>
              </div>
            </div>

            {/* Asymmetric 2-Column Operational Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter-lg items-start">
              {/* Left Column (7 cols) */}
              <div className="lg:col-span-7 flex flex-col gap-space-xl">
                {/* Alerts Card */}
                <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                  <div className="px-5 py-4 flex items-center justify-between bg-surface-container-lowest border-b border-surface-container/50">
                    <div className="flex items-center gap-space-sm">
                      <span className="material-symbols-outlined text-[20px] text-outline">
                        shield_with_heart
                      </span>
                      <h2 className="font-headline-sm text-headline-sm text-on-surface">Alerts</h2>
                    </div>
                    <span className="px-2 py-0.5 rounded-full bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm">
                      {summary?.alerts?.length || 0}
                    </span>
                  </div>
                  <div className="p-6">
                    {summary?.alerts && summary.alerts.length > 0 ? (
                      <div className="flex flex-col gap-3">
                        {summary.alerts.map((alert, index) => (
                          <div
                            key={index}
                            className="p-4 rounded-lg bg-surface-container-low flex items-start gap-3"
                          >
                            <span className="material-symbols-outlined text-error text-[20px] shrink-0 mt-0.5">
                              warning
                            </span>
                            <div className="flex flex-col">
                              <span className="font-label-md text-label-md text-on-surface">
                                {alert.title || alert.message}
                              </span>
                              {alert.description && (
                                <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                                  {alert.description}
                                </p>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="bg-surface-container-low rounded-xl py-10 px-6 flex flex-col items-center text-center">
                        <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline mb-3">
                          <span className="material-symbols-outlined text-[26px]">check_circle</span>
                        </div>
                        <h3 className="font-headline-sm text-headline-sm text-on-surface">System clear</h3>
                        <p className="font-body-md text-body-md text-on-surface-variant max-w-sm mt-1 mb-5">
                          No alerts. Run the solver to check for clashes and skill gaps
                        </p>
                        <button
                          onClick={handleRunSolver}
                          className="flex items-center gap-space-xs px-space-md h-10 rounded-lg bg-surface-container-lowest hover:bg-surface text-on-surface shadow-sm transition-colors cursor-pointer"
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[18px] text-primary-container">
                            bolt
                          </span>
                          <span className="font-label-lg text-label-lg">Run Solver</span>
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* Volunteer Workload Card */}
                <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden w-full">
                  <div className="px-5 py-4 flex items-center justify-between bg-surface-container-lowest border-b border-surface-container/50">
                    <div className="flex flex-col">
                      <h2 className="font-headline-sm text-headline-sm text-on-surface">
                        Workload per volunteer (hours)
                      </h2>
                      <span className="font-body-sm text-body-sm text-outline mt-0.5">
                        Calculated scheduled duration distribution
                      </span>
                    </div>
                    <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                      Target: 4h - 8h
                    </span>
                  </div>
                  <div className="p-6">
                    <div className="bg-surface-container-low rounded-xl p-6 relative overflow-hidden flex flex-col justify-between min-h-[220px]">
                      <div className="w-full flex flex-col gap-6 opacity-40">
                        <div className="flex items-center gap-3">
                          <span className="font-label-sm text-label-sm text-outline w-6 text-right">
                            10h
                          </span>
                          <div className="h-[1px] w-full bg-outline-variant" />
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="font-label-sm text-label-sm text-outline w-6 text-right">
                            5h
                          </span>
                          <div className="h-[1px] w-full bg-outline-variant" />
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="font-label-sm text-label-sm text-outline w-6 text-right">
                            0h
                          </span>
                          <div className="h-[1px] w-full bg-outline-variant" />
                        </div>
                      </div>

                      <div className="absolute inset-0 flex flex-col items-center justify-center p-6 text-center bg-surface-container-low/75 backdrop-blur-[1px]">
                        <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center text-outline mb-2">
                          <span className="material-symbols-outlined text-[20px]">bar_chart</span>
                        </div>
                        <span className="font-headline-sm text-headline-sm text-on-surface">
                          No workload data to display
                        </span>
                        <p className="font-body-sm text-body-sm text-on-surface-variant max-w-xs mt-1 mb-4">
                          Workload distribution will appear once volunteer assignments are computed.
                        </p>
                        <button
                          onClick={() => navigate('/volunteers')}
                          className="flex items-center gap-space-xs px-space-md h-9 rounded-lg bg-surface-container-lowest hover:bg-surface text-on-surface shadow-sm transition-colors cursor-pointer"
                          type="button"
                        >
                          <span className="material-symbols-outlined text-[16px] text-outline">group</span>
                          <span className="font-label-md text-label-md">Manage Volunteers</span>
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Column (5 cols) */}
              <div className="lg:col-span-5 flex flex-col gap-space-xl">
                {/* Volunteer Registration Form Card */}
                <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                  <div className="px-5 py-4 bg-surface-container-lowest border-b border-surface-container/50">
                    <h2 className="font-headline-sm text-headline-sm text-on-surface">
                      Volunteer registration form
                    </h2>
                  </div>
                  <div className="p-6 flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                      <label className="font-label-md text-label-md text-on-surface-variant">
                        Google Form link for this event
                      </label>
                      <input
                        type="url"
                        value={formUrlInput}
                        onChange={(e) => {
                          setFormUrlInput(e.target.value)
                          if (formError) setFormError('')
                        }}
                        placeholder="https://forms.google.com/..."
                        className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline text-body-md font-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 w-full"
                      />
                      <span className="font-body-sm text-body-sm text-outline mt-1">
                        Volunteers will see this link on the Volunteers page.
                      </span>

                      {/* Inline Error Message */}
                      {formError && (
                        <p className="font-body-sm text-body-sm text-error mt-1 flex items-center gap-1">
                          <span className="material-symbols-outlined text-[16px]">error</span>
                          <span>{formError}</span>
                        </p>
                      )}

                      {/* Saved Confirmation */}
                      {formSaveStatus === 'saved' && (
                        <p className="font-body-sm text-body-sm text-secondary font-medium mt-1 flex items-center gap-1">
                          <span className="material-symbols-outlined text-[16px]">check_circle</span>
                          <span>Saved successfully</span>
                        </p>
                      )}
                      {formSaveStatus === 'cleared' && (
                        <p className="font-body-sm text-body-sm text-outline font-medium mt-1 flex items-center gap-1">
                          <span className="material-symbols-outlined text-[16px]">info</span>
                          <span>Link cleared</span>
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-space-sm pt-1">
                      <button
                        type="button"
                        onClick={handleSaveFormUrl}
                        disabled={isSavingForm}
                        className="flex items-center justify-center px-space-md h-9 rounded-lg bg-primary-container text-on-primary hover:opacity-95 shadow-sm active:scale-[0.99] transition-all font-label-md text-label-md cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {isSavingForm ? 'Saving...' : 'Save'}
                      </button>
                      <button
                        type="button"
                        onClick={handleClearFormUrl}
                        disabled={isSavingForm}
                        className="flex items-center justify-center px-space-md h-9 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors font-label-md text-label-md cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Clear
                      </button>
                    </div>
                  </div>
                </div>

                {/* Plan Status Stepper Card */}
                <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                  <div className="px-5 py-4 bg-surface-container-lowest border-b border-surface-container/50">
                    <h2 className="font-headline-sm text-headline-sm text-on-surface">Plan status</h2>
                  </div>
                  <div className="p-6">
                    {/* Stepper Container */}
                    <div className="relative flex items-center justify-between mb-8 px-2">
                      <div className="absolute left-6 right-6 top-1/2 -translate-y-1/2 h-0.5 bg-surface-container" />

                      {/* Step 1: Draft */}
                      <div className="relative z-10 flex flex-col items-center">
                        <div
                          className={`w-8 h-8 rounded-full flex items-center justify-center font-label-md text-label-md shadow-sm ${
                            summary?.planStatus === 'draft' || summary?.planStatus === 'approved' || summary?.planStatus === 'published'
                              ? 'bg-primary-container text-on-primary'
                              : 'bg-surface-container-low text-outline'
                          }`}
                        >
                          1
                        </div>
                        <span className="font-label-sm text-label-sm text-outline mt-2">Draft</span>
                      </div>

                      {/* Step 2: Approved */}
                      <div className="relative z-10 flex flex-col items-center">
                        <div
                          className={`w-8 h-8 rounded-full flex items-center justify-center font-label-md text-label-md shadow-sm ${
                            summary?.planStatus === 'approved' || summary?.planStatus === 'published'
                              ? 'bg-primary-container text-on-primary'
                              : 'bg-surface-container-low text-outline'
                          }`}
                        >
                          2
                        </div>
                        <span className="font-label-sm text-label-sm text-outline mt-2">Approved</span>
                      </div>

                      {/* Step 3: Published */}
                      <div className="relative z-10 flex flex-col items-center">
                        <div
                          className={`w-8 h-8 rounded-full flex items-center justify-center font-label-md text-label-md shadow-sm ${
                            summary?.planStatus === 'published'
                              ? 'bg-primary-container text-on-primary'
                              : 'bg-surface-container-low text-outline'
                          }`}
                        >
                          3
                        </div>
                        <span className="font-label-sm text-label-sm text-outline mt-2">Published</span>
                      </div>
                    </div>

                    {/* Stepper Null State Notice */}
                    <div className="bg-surface-container-low rounded-xl p-4 flex flex-col gap-3">
                      <div className="flex items-start gap-space-sm">
                        <span className="material-symbols-outlined text-[20px] text-outline shrink-0 mt-0.5">
                          pending_actions
                        </span>
                        <div className="flex flex-col">
                          <span className="font-label-md text-label-md text-on-surface">
                            Pending initiation
                          </span>
                          <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                            No assignment plan has been generated for this event yet.
                          </p>
                        </div>
                      </div>
                      <button
                        onClick={handleGenerateDraftPlan}
                        className="w-full flex items-center justify-center gap-space-xs px-space-md h-10 rounded-lg bg-surface-container-lowest hover:bg-surface text-on-surface shadow-sm transition-colors mt-1 cursor-pointer"
                        type="button"
                      >
                        <span className="material-symbols-outlined text-[18px] text-primary-container">
                          alt_route
                        </span>
                        <span className="font-label-lg text-label-lg">Generate Draft Plan</span>
                      </button>
                    </div>
                  </div>
                </div>

                {/* Solver Readiness Card */}
                <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                  <div className="px-5 py-4 bg-surface-container-lowest border-b border-surface-container/50 flex items-center justify-between">
                    <h2 className="font-headline-sm text-headline-sm text-on-surface">
                      Solver Readiness
                    </h2>
                    <span
                      className={`px-2 py-0.5 rounded-full font-label-sm text-label-sm ${
                        isReadinessComplete
                          ? 'bg-secondary-container text-secondary'
                          : 'bg-surface-container-low text-outline'
                      }`}
                    >
                      {isReadinessComplete ? 'Ready' : 'Incomplete'}
                    </span>
                  </div>
                  <div className="p-6 flex flex-col gap-4">
                    <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                      Event setup checklist
                    </span>
                    <div className="flex flex-col gap-2">
                      {/* Row 1: Tasks created */}
                      <div className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low">
                        <div className="flex items-center gap-space-sm">
                          <span
                            className={`material-symbols-outlined text-[18px] ${
                              tasksCount > 0 ? 'text-secondary' : 'text-outline'
                            }`}
                          >
                            {tasksCount > 0 ? 'check_circle' : 'radio_button_unchecked'}
                          </span>
                          <span className="font-body-md text-body-md text-on-surface">
                            Tasks created
                          </span>
                        </div>
                        <span
                          className={`font-label-md text-label-md ${
                            tasksCount > 0 ? 'text-secondary font-medium' : 'text-outline'
                          }`}
                        >
                          {tasksCount > 0 ? `${tasksCount} created` : '--'}
                        </span>
                      </div>

                      {/* Row 2: Volunteers imported */}
                      <div className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low">
                        <div className="flex items-center gap-space-sm">
                          <span
                            className={`material-symbols-outlined text-[18px] ${
                              volunteersCount > 0 ? 'text-secondary' : 'text-outline'
                            }`}
                          >
                            {volunteersCount > 0 ? 'check_circle' : 'radio_button_unchecked'}
                          </span>
                          <span className="font-body-md text-body-md text-on-surface">
                            Volunteers imported
                          </span>
                        </div>
                        <span
                          className={`font-label-md text-label-md ${
                            volunteersCount > 0 ? 'text-secondary font-medium' : 'text-outline'
                          }`}
                        >
                          {volunteersCount > 0 ? `${volunteersCount} registered` : '--'}
                        </span>
                      </div>

                      {/* Row 3: Registration form link set */}
                      <div className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low">
                        <div className="flex items-center gap-space-sm">
                          <span
                            className={`material-symbols-outlined text-[18px] ${
                              isFormLinkSet ? 'text-secondary' : 'text-outline'
                            }`}
                          >
                            {isFormLinkSet ? 'check_circle' : 'radio_button_unchecked'}
                          </span>
                          <span className="font-body-md text-body-md text-on-surface">
                            Registration form link set
                          </span>
                        </div>
                        <span
                          className={`font-label-md text-label-md ${
                            isFormLinkSet ? 'text-secondary font-medium' : 'text-outline'
                          }`}
                        >
                          {isFormLinkSet ? 'Configured' : '--'}
                        </span>
                      </div>

                      {/* Row 4: Preferences configured */}
                      <div className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low">
                        <div className="flex items-center gap-space-sm">
                          <span
                            className={`material-symbols-outlined text-[18px] ${
                              isPreferencesConfigured ? 'text-secondary' : 'text-outline'
                            }`}
                          >
                            {isPreferencesConfigured ? 'check_circle' : 'radio_button_unchecked'}
                          </span>
                          <span className="font-body-md text-body-md text-on-surface">
                            Preferences configured
                          </span>
                        </div>
                        <span className="font-label-md text-label-md text-outline">--</span>
                      </div>
                    </div>

                    <button
                      onClick={() => navigate('/ai-assistant')}
                      className="w-full flex items-center justify-center gap-space-xs px-space-md h-10 rounded-lg bg-surface-container text-primary hover:bg-surface-container-high transition-colors mt-2 cursor-pointer"
                      type="button"
                    >
                      <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
                      <span className="font-label-lg text-label-lg">Ask AI Assistant</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
