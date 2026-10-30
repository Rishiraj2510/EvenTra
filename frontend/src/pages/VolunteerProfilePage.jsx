import { useCallback } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getVolunteer, getVolunteerHistory } from '../services/api'
import { useFetch } from '../hooks/useFetch'

const DAYS_OF_WEEK = [
  { key: 'Monday', label: 'Mon' },
  { key: 'Tuesday', label: 'Tue' },
  { key: 'Wednesday', label: 'Wed' },
  { key: 'Thursday', label: 'Thu' },
  { key: 'Friday', label: 'Fri' },
  { key: 'Saturday', label: 'Sat' },
  { key: 'Sunday', label: 'Sun' },
]

const TIME_BLOCKS = [
  { name: 'Morning', hours: '08:00 - 12:00' },
  { name: 'Afternoon', hours: '12:00 - 17:00' },
  { name: 'Evening', hours: '17:00 - 21:00' },
]

export default function VolunteerProfilePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { role } = useAuth()

  // Data fetching callbacks
  const fetchVolunteerCallback = useCallback(() => {
    if (!id) return Promise.resolve(null)
    return getVolunteer(id)
  }, [id])

  const fetchHistoryCallback = useCallback(() => {
    if (!id) return Promise.resolve({ volunteerId: id, averageRating: null, assignments: [] })
    return getVolunteerHistory(id)
  }, [id])

  const {
    data: volunteer,
    loading: loadingVolunteer,
    error: errorVolunteer,
    reload: reloadVolunteer,
  } = useFetch(fetchVolunteerCallback)

  const {
    data: history,
    loading: loadingHistory,
    error: errorHistory,
    reload: reloadHistory,
  } = useFetch(fetchHistoryCallback)

  // Redirect volunteers away (organiser & coordinator only)
  if (role === 'volunteer') {
    return <Navigate to="/events" replace />
  }

  const handleRetryAll = () => {
    reloadVolunteer()
    reloadHistory()
  }

  const isLoading = loadingVolunteer || loadingHistory
  const hasError = errorVolunteer || errorHistory

  // Loading state
  if (isLoading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading volunteer profile...</p>
        </div>
      </div>
    )
  }

  // Error state
  if (hasError) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-error-container text-error flex items-center justify-center mb-space-md">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
            Failed to load profile
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {errorVolunteer || errorHistory || 'An unexpected error occurred while loading volunteer data.'}
          </p>
          <div className="flex items-center gap-space-sm">
            <button
              type="button"
              onClick={() => navigate('/volunteers')}
              className="px-space-lg py-2 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
            >
              Back to Volunteers
            </button>
            <button
              type="button"
              onClick={handleRetryAll}
              className="px-space-lg py-2 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors cursor-pointer"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    )
  }

  // Not found state
  if (!volunteer) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-surface-container text-outline flex items-center justify-center mb-space-md">
            <span className="material-symbols-outlined text-[30px]">person_off</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
            Volunteer not found
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            No volunteer found with ID &ldquo;{id}&rdquo;. They may have been removed or the link is incorrect.
          </p>
          <button
            type="button"
            onClick={() => navigate('/volunteers')}
            className="flex items-center gap-space-xs px-space-lg py-2.5 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors shadow-sm cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
            <span>Back to Volunteers</span>
          </button>
        </div>
      </div>
    )
  }

  // Derive volunteer initial avatar
  const initials = volunteer.fullName
    ? volunteer.fullName
        .split(' ')
        .map((n) => n[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : 'VO'

  // Availability lookup helper
  const isAvailableSlot = (dayName, slotName) => {
    if (!volunteer.availability || !Array.isArray(volunteer.availability)) return false
    return volunteer.availability.some((a) => {
      const matchDay = a.day?.toLowerCase() === dayName.toLowerCase()
      if (!matchDay) return false
      const slot = a.slot?.toLowerCase()
      if (slot === 'full day') return true
      return slot === slotName.toLowerCase()
    })
  }

  // Skill badge color helper
  const getSkillBadgeClass = (level) => {
    const l = (level || '').toLowerCase()
    if (l === 'expert') {
      return 'bg-secondary-container/40 text-secondary border border-secondary/30'
    }
    if (l === 'intermediate') {
      return 'bg-primary-fixed text-primary border border-primary/20'
    }
    return 'bg-surface-container text-on-surface-variant border border-outline-variant/30'
  }

  // Star rendering helper
  const renderStars = (rating) => {
    if (rating === null || rating === undefined) return null
    const stars = []
    const rounded = Math.round(rating * 2) / 2
    for (let i = 1; i <= 5; i++) {
      if (rounded >= i) {
        stars.push(
          <span key={i} className="material-symbols-outlined text-[18px] text-tertiary font-variation-fill">
            star
          </span>
        )
      } else if (rounded >= i - 0.5) {
        stars.push(
          <span key={i} className="material-symbols-outlined text-[18px] text-tertiary font-variation-fill">
            star_half
          </span>
        )
      } else {
        stars.push(
          <span key={i} className="material-symbols-outlined text-[18px] text-outline-variant">
            star
          </span>
        )
      }
    }
    return stars
  }

  const pastAssignments = history?.assignments || []
  const averageRating = history?.averageRating

  return (
    <div className="flex flex-col w-full pb-16">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Breadcrumb & Navigation */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-space-xs text-outline font-label-md text-label-md">
            <button
              type="button"
              onClick={() => navigate('/volunteers')}
              className="hover:text-primary transition-colors cursor-pointer"
            >
              Volunteers
            </button>
            <span className="material-symbols-outlined text-[16px]">chevron_right</span>
            <span className="text-on-surface font-label-lg text-label-lg">
              {volunteer.fullName}
            </span>
          </div>

          <button
            type="button"
            onClick={() => navigate('/volunteers')}
            className="flex items-center gap-1.5 px-space-md py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container-low text-on-surface-variant hover:text-on-surface border border-outline-variant/30 font-label-md text-label-md transition-colors cursor-pointer shadow-xs"
          >
            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
            <span>Back to Volunteers</span>
          </button>
        </div>

        {/* Header Card */}
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 lg:p-8">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
            {/* Avatar & Core Bio */}
            <div className="flex items-start sm:items-center gap-space-lg">
              <div className="w-20 h-20 rounded-2xl bg-primary-container text-on-primary flex items-center justify-center font-headline-lg text-headline-lg font-bold shadow-md shrink-0">
                {initials}
              </div>

              <div className="flex flex-col gap-1.5">
                <div className="flex flex-wrap items-center gap-space-sm">
                  <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
                    {volunteer.fullName}
                  </h1>
                  <span
                    className={`inline-flex items-center px-3 py-1 rounded-full font-label-sm text-label-sm font-semibold capitalize ${
                      volunteer.status === 'active'
                        ? 'bg-secondary-container/40 text-secondary border border-secondary/30'
                        : 'bg-surface-container text-outline border border-outline-variant/40'
                    }`}
                  >
                    <span
                      className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
                        volunteer.status === 'active' ? 'bg-secondary' : 'bg-outline'
                      }`}
                    />
                    {volunteer.status || 'Active'}
                  </span>
                </div>

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-on-surface-variant font-body-sm text-body-sm">
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[16px] text-outline">school</span>
                    <span>{volunteer.department || 'General Dept'}</span>
                  </span>
                  <span className="text-outline">•</span>
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[16px] text-outline">calendar_today</span>
                    <span>{volunteer.yearOfStudy || 'Year not specified'}</span>
                  </span>
                  {volunteer.notificationPreference && (
                    <>
                      <span className="text-outline">•</span>
                      <span className="flex items-center gap-1 capitalize">
                        <span className="material-symbols-outlined text-[16px] text-outline">notifications</span>
                        <span>Prefers {volunteer.notificationPreference}</span>
                      </span>
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* Contact Details Pill */}
            <div className="flex flex-wrap sm:flex-nowrap items-center gap-4 p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 shrink-0">
              <div className="flex flex-col gap-1">
                <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                  Email
                </span>
                <a
                  href={`mailto:${volunteer.email}`}
                  className="font-body-md text-body-md text-primary hover:underline flex items-center gap-1"
                >
                  <span className="material-symbols-outlined text-[16px]">mail</span>
                  <span>{volunteer.email}</span>
                </a>
              </div>

              <div className="h-8 w-px bg-outline-variant/30 hidden sm:block" />

              <div className="flex flex-col gap-1">
                <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                  Phone
                </span>
                <span className="font-body-md text-body-md text-on-surface flex items-center gap-1">
                  <span className="material-symbols-outlined text-[16px] text-outline">call</span>
                  <span>{volunteer.phone || '—'}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Main Grid: Left Details & Right Metrics */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-space-xl">
          {/* Left 2 Columns: Skills, Availability, Past Assignments */}
          <div className="lg:col-span-2 flex flex-col gap-space-xl">
            {/* Skills Card */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6">
              <div className="flex items-center justify-between pb-4 mb-4 border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">psychology</span>
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Skills &amp; Competencies
                  </h2>
                </div>
                <span className="font-label-sm text-label-sm text-outline">
                  {volunteer.skills?.length || 0} registered
                </span>
              </div>

              {volunteer.skills && volunteer.skills.length > 0 ? (
                <div className="flex flex-wrap gap-2.5">
                  {volunteer.skills.map((skill, idx) => (
                    <div
                      key={idx}
                      className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-surface-container-low border border-outline-variant/25 shadow-xs"
                    >
                      <span className="font-label-md text-label-md text-on-surface font-medium">
                        {skill.name}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-full font-label-sm text-[11px] font-semibold ${getSkillBadgeClass(
                          skill.level
                        )}`}
                      >
                        {skill.level}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="font-body-md text-body-md text-outline">No skills listed for this volunteer.</p>
              )}
            </section>

            {/* Weekly Availability Grid */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6">
              <div className="flex items-center justify-between pb-4 mb-4 border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">calendar_view_week</span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Weekly Availability
                    </h2>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Recurring shift blocks volunteer has committed to.
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3 font-label-sm text-label-sm">
                  <div className="flex items-center gap-1.5">
                    <span className="w-3 h-3 rounded bg-secondary-container border border-secondary" />
                    <span>Available</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-3 h-3 rounded bg-surface-container-low border border-outline-variant/40" />
                    <span className="text-outline">Unavailable</span>
                  </div>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[560px]">
                  <thead>
                    <tr className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase">
                      <th className="py-2.5 px-3 rounded-l-lg">Time Block</th>
                      {DAYS_OF_WEEK.map((d) => (
                        <th key={d.key} className="py-2.5 px-2 text-center">
                          {d.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/15 font-body-sm text-body-sm">
                    {TIME_BLOCKS.map((block) => (
                      <tr key={block.name}>
                        <td className="py-3 px-3">
                          <div className="font-label-md text-label-md text-on-surface font-medium">
                            {block.name}
                          </div>
                          <div className="text-outline font-body-sm text-body-sm">{block.hours}</div>
                        </td>
                        {DAYS_OF_WEEK.map((day) => {
                          const available = isAvailableSlot(day.key, block.name)
                          return (
                            <td key={day.key} className="p-1.5 text-center">
                              <div
                                className={`w-full py-2 rounded-lg font-label-sm text-[12px] font-semibold transition-all flex items-center justify-center gap-1 ${
                                  available
                                    ? 'bg-secondary-container/40 text-secondary border border-secondary/30'
                                    : 'bg-surface-container-low text-outline/50 border border-transparent'
                                }`}
                              >
                                {available ? (
                                  <>
                                    <span className="material-symbols-outlined text-[14px]">check</span>
                                    <span>Yes</span>
                                  </>
                                ) : (
                                  <span>—</span>
                                )}
                              </div>
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            {/* Past Assignments Table */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6">
              <div className="flex items-center justify-between pb-4 mb-4 border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">history_edu</span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Past Event Assignments
                    </h2>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Operational duties performed in previous festivals and hackathons.
                    </p>
                  </div>
                </div>

                <span className="font-label-sm text-label-sm text-outline">
                  {pastAssignments.length} {pastAssignments.length === 1 ? 'assignment' : 'assignments'}
                </span>
              </div>

              {pastAssignments.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[500px]">
                    <thead>
                      <tr className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase">
                        <th className="py-2.5 px-3 rounded-l-lg">Event</th>
                        <th className="py-2.5 px-3">Duty / Task</th>
                        <th className="py-2.5 px-3 text-center">Fit Score</th>
                        <th className="py-2.5 px-3 rounded-r-lg text-right">Rating</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant/15 font-body-sm text-body-sm">
                      {pastAssignments.map((item, idx) => (
                        <tr key={idx} className="hover:bg-surface-container-low/40 transition-colors">
                          <td className="py-3 px-3 font-label-md text-label-md text-on-surface font-medium">
                            {item.eventName}
                          </td>
                          <td className="py-3 px-3 text-on-surface-variant">
                            {item.taskTitle}
                          </td>
                          <td className="py-3 px-3 text-center">
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-primary-fixed text-primary font-label-sm text-[12px] font-semibold">
                              {item.fit}%
                            </span>
                          </td>
                          <td className="py-3 px-3 text-right">
                            <div className="inline-flex items-center gap-1 justify-end">
                              <span className="material-symbols-outlined text-[16px] text-tertiary font-variation-fill">
                                star
                              </span>
                              <span className="font-label-md text-label-md text-on-surface font-semibold">
                                {item.rating}
                              </span>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center p-8 rounded-xl bg-surface-container-low/50 border border-dashed border-outline-variant/40 text-center">
                  <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline mb-2">
                    <span className="material-symbols-outlined text-[24px]">event_busy</span>
                  </div>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface mb-1">
                    No past assignments yet
                  </h3>
                  <p className="font-body-md text-body-md text-outline max-w-sm">
                    This volunteer has not yet been assigned to completed events on Eventra.
                  </p>
                </div>
              )}
            </section>
          </div>

          {/* Right Column: Performance, Experience Tiles, Interests */}
          <div className="flex flex-col gap-space-xl">
            {/* Performance Card */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-tertiary text-[22px]">grade</span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Performance &amp; Rating
                </h2>
              </div>

              {averageRating !== null && averageRating !== undefined ? (
                <div className="flex flex-col items-center justify-center p-6 rounded-xl bg-surface-container-low border border-outline-variant/20 text-center gap-2">
                  <span className="font-headline-xl text-[44px] font-bold text-on-surface leading-none">
                    {averageRating.toFixed(1)}
                  </span>
                  <div className="flex items-center gap-0.5 my-1">
                    {renderStars(averageRating)}
                  </div>
                  <span className="font-label-sm text-label-sm text-outline">
                    Based on {pastAssignments.length} completed past duties
                  </span>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center p-6 rounded-xl bg-surface-container-low border border-outline-variant/20 text-center gap-1.5">
                  <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center text-outline mb-1">
                    <span className="material-symbols-outlined text-[20px]">star_border</span>
                  </div>
                  <span className="font-headline-sm text-headline-sm text-on-surface">
                    No ratings yet
                  </span>
                  <span className="font-body-sm text-body-sm text-outline max-w-[200px]">
                    Ratings are recorded upon event task completion.
                  </span>
                </div>
              )}
            </section>

            {/* Experience & Workload Tiles */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-primary text-[22px]">timelapse</span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Experience &amp; Capacity
                </h2>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                  <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                    Past Events
                  </span>
                  <span className="font-headline-md text-headline-md font-bold text-on-surface">
                    {volunteer.pastEventsCount ?? 0}
                  </span>
                  <span className="font-body-sm text-[11px] text-outline">
                    Total festivals staffed
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                  <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                    Max Hours
                  </span>
                  <span className="font-headline-md text-headline-md font-bold text-primary">
                    {volunteer.maxHoursPerEvent ? `${volunteer.maxHoursPerEvent}h` : '—'}
                  </span>
                  <span className="font-body-sm text-[11px] text-outline">
                    Cap per event cycle
                  </span>
                </div>
              </div>
            </section>

            {/* Task Interests Chips */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-primary text-[22px]">favorite</span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Task Interests
                </h2>
              </div>

              {volunteer.interests && volunteer.interests.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {volunteer.interests.map((interest, idx) => (
                    <span
                      key={idx}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-sm text-label-sm border border-outline-variant/25 transition-colors"
                    >
                      <span className="w-1.5 h-1.5 rounded-full bg-primary" />
                      <span>{interest}</span>
                    </span>
                  ))}
                </div>
              ) : (
                <p className="font-body-md text-body-md text-outline">No specific interests indicated.</p>
              )}
            </section>
          </div>
        </div>
      </div>
    </div>
  )
}
