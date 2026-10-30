import { useState, useCallback, useMemo } from 'react'
import { Navigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getMyDuties } from '../services/api'
import { useFetch } from '../hooks/useFetch'

export default function MyDutiesPage() {
  const { role, user } = useAuth()
  const { selectedEventId, selectedEvent } = useEvent()

  // Tab filter: 'all' | 'upcoming' | 'completed'
  const [activeFilter, setActiveFilter] = useState('all')

  // Volunteer ID from user context (or fallback to linked mock volunteer vol-1)
  const volunteerId = user?.volunteerId || 'vol-1'

  // Fetch duties callback
  const fetchDutiesCallback = useCallback(() => {
    return getMyDuties(volunteerId, selectedEventId)
  }, [volunteerId, selectedEventId])

  const { data: duties, loading, error, reload } = useFetch(fetchDutiesCallback)

  // Filter duties based on tab
  const filteredDuties = useMemo(() => {
    if (!duties) return []
    if (activeFilter === 'upcoming') {
      return duties.filter((d) => d.status === 'upcoming')
    }
    if (activeFilter === 'completed') {
      return duties.filter((d) => d.status === 'completed')
    }
    return duties
  }, [duties, activeFilter])

  // Count metrics
  const totalCount = duties ? duties.length : 0
  const upcomingCount = duties ? duties.filter((d) => d.status === 'upcoming').length : 0
  const completedCount = duties ? duties.filter((d) => d.status === 'completed').length : 0

  // Calculate approximate total hours logged from completed duties
  const totalHoursLogged = useMemo(() => {
    if (!duties) return 0
    let hours = 0
    duties.forEach((d) => {
      if (d.status === 'completed') {
        // Calculate diff between startTime and endTime if available
        if (d.startTime && d.endTime) {
          const [sh, sm] = d.startTime.split(':').map(Number)
          const [eh, em] = d.endTime.split(':').map(Number)
          const diff = eh + em / 60 - (sh + sm / 60)
          hours += diff > 0 ? diff : 4
        } else {
          hours += 4
        }
      }
    })
    return Math.round(hours * 10) / 10
  }, [duties])

  // Redirect non-volunteers (organisers/coordinators)
  if (role !== 'volunteer') {
    return <Navigate to="/dashboard" replace />
  }

  // Loading state
  if (loading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading your assigned duties...</p>
        </div>
      </div>
    )
  }

  // Error state
  if (error) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-error-container text-error flex items-center justify-center mb-space-md shadow-sm">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Unable to load shift schedule
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {error || 'A connection error occurred while retrieving your volunteer roster.'}
          </p>
          <button
            type="button"
            onClick={reload}
            className="flex items-center gap-space-xs px-space-lg py-2.5 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors shadow-sm cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            <span>Retry connection</span>
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col w-full pb-20">
      <div className="p-margin-lg flex flex-col gap-space-xl max-w-[1360px] mx-auto w-full">
        {/* Header Title Section */}
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-md">
          <div className="flex flex-col gap-space-xs">
            <div className="flex items-center gap-space-sm">
              <span className="inline-flex items-center px-space-sm py-0.5 rounded-full bg-surface-container-high text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider">
                Volunteer Workspace
              </span>
              <span className="inline-flex items-center gap-1.5 px-space-sm py-0.5 rounded-full bg-secondary-container/50 text-secondary font-label-sm text-label-sm font-medium border border-secondary/20">
                <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
                Roster Live
              </span>
            </div>
            <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight font-bold">
              My Duties
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant">
              {selectedEvent
                ? `Assigned shifts and active duty hours for ${selectedEvent.name}.`
                : 'View your assigned shifts across collegiate festivals and check duty statuses.'}
            </p>
          </div>
        </div>

        {/* Top Summary Stat Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-space-lg">
          {/* Total Hours Card */}
          <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] border border-outline-variant/20 flex items-start justify-between relative overflow-hidden">
            <div className="flex flex-col gap-space-xs z-10">
              <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline font-semibold">
                Completed Hours
              </span>
              <div className="flex items-baseline gap-space-xs">
                <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight font-bold">
                  {totalHoursLogged > 0 ? totalHoursLogged : '--'}
                </span>
                <span className="font-body-sm text-body-sm text-outline">hrs</span>
              </div>
              <span className="font-body-sm text-body-sm text-on-surface-variant">
                Logged completed duty hours
              </span>
            </div>
            <div className="w-12 h-12 rounded-xl bg-primary-fixed flex items-center justify-center text-primary shadow-sm z-10">
              <span className="material-symbols-outlined text-[26px]">schedule</span>
            </div>
          </div>

          {/* Assigned Shifts Card */}
          <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] border border-outline-variant/20 flex items-start justify-between relative overflow-hidden">
            <div className="flex flex-col gap-space-xs z-10">
              <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline font-semibold">
                Assigned Shifts
              </span>
              <div className="flex items-baseline gap-space-xs">
                <span className="font-headline-xl text-headline-xl text-on-surface tracking-tight font-bold">
                  {totalCount > 0 ? totalCount : '--'}
                </span>
                <span className="font-body-sm text-body-sm text-outline">
                  {totalCount === 1 ? 'shift' : 'shifts'}
                </span>
              </div>
              <Link
                to="/my-certificates"
                className="font-label-md text-label-md text-primary hover:underline flex items-center gap-1 mt-1 transition-colors"
              >
                <span>View my certificates</span>
                <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
              </Link>
            </div>
            <div className="w-12 h-12 rounded-xl bg-secondary-fixed flex items-center justify-center text-secondary shadow-sm z-10">
              <span className="material-symbols-outlined text-[26px]">assignment_turned_in</span>
            </div>
          </div>
        </div>

        {/* Shifts List Container */}
        <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] border border-outline-variant/20 overflow-hidden">
          {/* Sub-header Filter Tabs */}
          <div className="px-space-xl py-space-lg flex flex-col sm:flex-row sm:items-center justify-between gap-space-md bg-surface-container-low/50 border-b border-outline-variant/15">
            <div className="flex items-center gap-space-xs">
              <button
                type="button"
                onClick={() => setActiveFilter('all')}
                className={`px-space-md py-1.5 rounded-full font-label-md text-label-md transition-all cursor-pointer ${
                  activeFilter === 'all'
                    ? 'bg-primary text-on-primary shadow-sm font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                All shifts ({totalCount})
              </button>
              <button
                type="button"
                onClick={() => setActiveFilter('upcoming')}
                className={`px-space-md py-1.5 rounded-full font-label-md text-label-md transition-all cursor-pointer ${
                  activeFilter === 'upcoming'
                    ? 'bg-primary text-on-primary shadow-sm font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                Upcoming ({upcomingCount})
              </button>
              <button
                type="button"
                onClick={() => setActiveFilter('completed')}
                className={`px-space-md py-1.5 rounded-full font-label-md text-label-md transition-all cursor-pointer ${
                  activeFilter === 'completed'
                    ? 'bg-primary text-on-primary shadow-sm font-semibold'
                    : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                Past ({completedCount})
              </button>
            </div>

            <div className="flex items-center gap-space-sm text-outline font-label-sm text-label-sm">
              <span className="material-symbols-outlined text-[16px]">sync</span>
              <span>Published Roster</span>
            </div>
          </div>

          {/* Duties Card Grid or Empty State */}
          <div className="p-space-xl">
            {filteredDuties.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-space-lg">
                {filteredDuties.map((duty) => {
                  const isUpcoming = duty.status === 'upcoming'
                  return (
                    <div
                      key={duty.id}
                      className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm border border-outline-variant/25 flex flex-col justify-between gap-space-lg relative overflow-hidden transition-all hover:shadow-md"
                    >
                      <div className="flex flex-col gap-space-md">
                        {/* Card Badge Top */}
                        <div className="flex items-center justify-between">
                          <span className="px-space-sm py-0.5 rounded-md font-label-sm text-label-sm bg-surface-container-high text-on-surface-variant uppercase font-semibold tracking-wider">
                            Shift Duty
                          </span>
                          {isUpcoming ? (
                            <span className="inline-flex items-center gap-1.5 px-space-sm py-0.5 rounded-full font-label-sm text-label-sm bg-secondary-container/60 text-secondary font-semibold border border-secondary/30">
                              <span className="w-1.5 h-1.5 rounded-full bg-secondary animate-pulse" />
                              Upcoming
                            </span>
                          ) : (
                            <span className="px-space-sm py-0.5 rounded-full font-label-sm text-label-sm bg-surface-container-high text-on-surface-variant font-medium">
                              Completed
                            </span>
                          )}
                        </div>

                        {/* Task Title & Details */}
                        <div className="flex flex-col gap-1">
                          <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold leading-tight">
                            {duty.taskTitle}
                          </h3>
                        </div>

                        {/* Time & Location */}
                        <div className="flex flex-col gap-space-xs pt-1 text-on-surface-variant">
                          <div className="flex items-center gap-space-xs">
                            <span className="material-symbols-outlined text-[18px] text-primary">
                              calendar_today
                            </span>
                            <span className="font-body-sm text-body-sm font-medium text-on-surface">
                              {duty.date}
                            </span>
                          </div>
                          <div className="flex items-center gap-space-xs">
                            <span className="material-symbols-outlined text-[18px] text-primary">
                              schedule
                            </span>
                            <span className="font-body-sm text-body-sm font-medium text-on-surface">
                              {duty.startTime} – {duty.endTime}
                            </span>
                          </div>
                          <div className="flex items-center gap-space-xs">
                            <span className="material-symbols-outlined text-[18px] text-outline">
                              location_on
                            </span>
                            <span className="font-body-sm text-body-sm text-outline">
                              {duty.location}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Card Footer Strip */}
                      <div className="pt-space-sm flex items-center justify-between bg-surface-container-low -mx-space-lg -mb-space-lg p-space-md px-space-lg border-t border-outline-variant/15">
                        <div className="flex items-center gap-space-xs">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              isUpcoming ? 'bg-secondary' : 'bg-outline-variant'
                            }`}
                          />
                          <span
                            className={`font-label-sm text-label-sm ${
                              isUpcoming ? 'text-secondary font-medium' : 'text-outline'
                            }`}
                          >
                            {isUpcoming ? 'Assigned' : 'Logged'}
                          </span>
                        </div>
                        <span className="font-label-sm text-label-sm text-outline">
                          {isUpcoming ? 'Confirmed' : 'Duty Record Stored'}
                        </span>
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              /* Empty State */
              <div className="relative my-4">
                <div className="bg-surface-container-low rounded-xl py-16 px-space-xl flex flex-col items-center text-center max-w-2xl mx-auto shadow-xs border border-outline-variant/20">
                  <div className="w-16 h-16 rounded-2xl bg-surface-container-high flex items-center justify-center text-outline mb-space-md shadow-inner">
                    <span className="material-symbols-outlined text-[32px]">event_busy</span>
                  </div>
                  <h2 className="font-headline-md text-headline-md text-on-surface font-semibold mb-space-xs">
                    No duties assigned yet
                  </h2>
                  <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg leading-relaxed">
                    When an organiser publishes the approved assignment plan for this event, your scheduled shifts will appear here.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
