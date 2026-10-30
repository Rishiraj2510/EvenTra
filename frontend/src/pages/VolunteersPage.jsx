import { useState, useMemo, useCallback } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getVolunteers } from '../services/api'
import { useFetch } from '../hooks/useFetch'

export default function VolunteersPage() {
  const { role } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent } = useEvent()
  const navigate = useNavigate()

  // Fetch volunteers matching the selected event
  const fetchVolunteersCallback = useCallback(() => {
    return getVolunteers(selectedEventId)
  }, [selectedEventId])

  const { data: volunteers, loading, error, reload } = useFetch(fetchVolunteersCallback)

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedSkill, setSelectedSkill] = useState('')
  const [selectedStatus, setSelectedStatus] = useState('')
  const [copiedLink, setCopiedLink] = useState(false)

  // Extract distinct skill names for dropdown
  const availableSkills = useMemo(() => {
    if (!volunteers) return []
    const skillSet = new Set()
    volunteers.forEach((v) => {
      v.skills?.forEach((s) => {
        if (s.name) skillSet.add(s.name)
      })
    })
    return Array.from(skillSet).sort()
  }, [volunteers])

  // Filter volunteers
  const filteredVolunteers = useMemo(() => {
    if (!volunteers) return []
    return volunteers.filter((vol) => {
      // Skill filter
      if (selectedSkill && !vol.skills?.some((s) => s.name.toLowerCase() === selectedSkill.toLowerCase())) {
        return false
      }
      // Status filter
      if (selectedStatus && vol.status?.toLowerCase() !== selectedStatus.toLowerCase()) {
        return false
      }
      // Search query (name, email, department, skills)
      if (!searchQuery.trim()) return true
      const q = searchQuery.toLowerCase()
      const matchName = vol.fullName?.toLowerCase().includes(q)
      const matchEmail = vol.email?.toLowerCase().includes(q)
      const matchDept = vol.department?.toLowerCase().includes(q)
      const matchSkill = vol.skills?.some((s) => s.name?.toLowerCase().includes(q))
      return matchName || matchEmail || matchDept || matchSkill
    })
  }, [volunteers, selectedSkill, selectedStatus, searchQuery])

  // Redirect volunteers away (organiser only)
  if (role === 'volunteer') {
    return <Navigate to={getHomeRoute(role)} replace />
  }

  // Copy registration form link
  const handleCopyLink = () => {
    const link = selectedEvent?.registrationFormUrl || `${window.location.origin}/register?eventId=${selectedEventId || ''}`
    navigator.clipboard?.writeText(link).then(() => {
      setCopiedLink(true)
      setTimeout(() => setCopiedLink(false), 2000)
    })
  }

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Header Banner & Actions */}
        <div className="flex flex-col gap-space-sm">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-lg">
            <div className="flex flex-col gap-space-xs max-w-2xl">
              <div className="flex items-center gap-space-xs text-outline font-label-sm text-label-sm tracking-wide">
                <span>{selectedEvent ? selectedEvent.name : 'No event selected'}</span>
              </div>
              <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
                Volunteers
              </h1>
              <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                View registered volunteers for the selected event.
              </p>
            </div>

            <div className="flex items-center gap-space-md shrink-0">
              <button
                type="button"
                onClick={handleCopyLink}
                disabled={!selectedEventId}
                className="flex items-center gap-space-xs px-space-lg h-10 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg transition-colors shadow-sm cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span className="material-symbols-outlined text-[18px]">
                  {copiedLink ? 'check' : 'link'}
                </span>
                <span>{copiedLink ? 'Link copied!' : 'Copy form link'}</span>
              </button>
            </div>
          </div>
          <p className="font-body-sm text-body-sm text-on-surface-variant">
            The registration form link is set by the organiser on the Dashboard.
          </p>
        </div>

        {/* If No Event is Selected: Prompt State */}
        {!selectedEventId && (
          <section className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">event_available</span>
            </div>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
              Please select an event
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
              Volunteers are registered per collegiate event. Choose an event from the topbar or select one below to view the volunteer roster.
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

        {/* Main Content when Event is Selected */}
        {selectedEventId && (
          <>
            {/* Search and Filter Control Bar */}
            <div className="p-space-lg rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05)]">
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-space-md justify-between">
                <div className="relative flex-1 min-w-[260px]">
                  <span className="material-symbols-outlined absolute left-3 top-2.5 text-[20px] text-outline">
                    search
                  </span>
                  <input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full h-10 pl-10 pr-4 bg-surface-container-low/50 rounded-lg font-body-md text-body-md text-on-surface placeholder:text-outline-variant focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
                    placeholder="Search volunteers by name, email, department..."
                    type="text"
                  />
                </div>

                <div className="flex flex-wrap items-center gap-space-sm">
                  {/* Skill filter */}
                  <div className="relative min-w-[150px]">
                    <select
                      value={selectedSkill}
                      onChange={(e) => setSelectedSkill(e.target.value)}
                      className="w-full h-10 pl-3 pr-8 appearance-none bg-surface-container-low rounded-lg font-label-md text-label-md text-on-surface hover:bg-surface-container transition-colors focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
                    >
                      <option value="">All Skills</option>
                      {availableSkills.map((sk) => (
                        <option key={sk} value={sk}>
                          {sk}
                        </option>
                      ))}
                    </select>
                    <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-2.5 text-[18px] text-on-surface-variant">
                      expand_more
                    </span>
                  </div>

                  {/* Status filter */}
                  <div className="relative min-w-[140px]">
                    <select
                      value={selectedStatus}
                      onChange={(e) => setSelectedStatus(e.target.value)}
                      className="w-full h-10 pl-3 pr-8 appearance-none bg-surface-container-low rounded-lg font-label-md text-label-md text-on-surface hover:bg-surface-container transition-colors focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
                    >
                      <option value="">All Statuses</option>
                      <option value="active">Active</option>
                      <option value="inactive">Inactive</option>
                    </select>
                    <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-2.5 text-[18px] text-on-surface-variant">
                      expand_more
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Loading State */}
            {loading && (
              <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
                <div className="w-12 h-12 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-space-md" />
                <p className="font-body-md text-body-md text-on-surface-variant">
                  Loading volunteers roster...
                </p>
              </div>
            )}

            {/* Error State */}
            {!loading && error && (
              <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-12 flex flex-col items-center justify-center text-center border border-error/20">
                <span className="material-symbols-outlined text-[40px] text-error mb-space-sm">
                  error_outline
                </span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
                  Failed to load volunteers
                </h2>
                <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg">
                  {error}
                </p>
                <button
                  onClick={() => reload()}
                  className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm transition-all cursor-pointer"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">refresh</span>
                  <span>Try again</span>
                </button>
              </div>
            )}

            {/* Volunteers Table Matrix */}
            {!loading && !error && (
              <div className="w-full flex flex-col rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.05),0_4px_6px_-1px_rgba(0,0,0,0.05)] overflow-hidden">
                <div className="overflow-x-auto w-full">
                  <table className="w-full text-left min-w-[900px] border-collapse">
                    <thead>
                      <tr className="bg-surface-container-low/60 font-label-sm text-label-sm font-semibold uppercase tracking-wider text-outline border-b border-surface-container-high h-11">
                        <th className="px-space-md py-3 font-semibold">Name</th>
                        <th className="px-space-md py-3 font-semibold">Email</th>
                        <th className="px-space-md py-3 font-semibold">Skills</th>
                        <th className="px-space-md py-3 font-semibold">Experience</th>
                        <th className="px-space-md py-3 font-semibold">Availability</th>
                        <th className="px-space-md py-3 font-semibold">Max hours</th>
                        <th className="px-space-md py-3 font-semibold text-center">Status</th>
                        <th className="px-space-md py-3 font-semibold text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredVolunteers.length > 0 ? (
                        filteredVolunteers.map((vol) => {
                          const availabilitySummary = vol.availability?.length
                            ? vol.availability.map((a) => `${a.day} (${a.slot})`).join(', ')
                            : 'Flexible'

                          const isActive = vol.status?.toLowerCase() === 'active'

                          return (
                            <tr
                              key={vol.id}
                              className="border-b border-surface-container-low hover:bg-surface-container-low/40 transition-colors"
                            >
                              {/* Name & Department */}
                              <td className="py-3 px-space-md align-middle">
                                <div className="flex flex-col">
                                  <span className="font-label-md text-label-md text-on-surface font-semibold">
                                    {vol.fullName}
                                  </span>
                                  {vol.department && (
                                    <span className="font-body-sm text-body-sm text-outline">
                                      {vol.department} {vol.yearOfStudy ? `• ${vol.yearOfStudy}` : ''}
                                    </span>
                                  )}
                                </div>
                              </td>

                              {/* Email */}
                              <td className="py-3 px-space-md align-middle">
                                <span className="font-body-sm text-body-sm text-on-surface-variant">
                                  {vol.email}
                                </span>
                              </td>

                              {/* Skills chips */}
                              <td className="py-3 px-space-md align-middle">
                                <div className="flex flex-wrap gap-1 max-w-xs">
                                  {vol.skills?.slice(0, 3).map((sk) => (
                                    <span
                                      key={sk.name}
                                      className="px-2 py-0.5 rounded-full bg-primary-fixed text-on-primary-fixed-variant font-label-sm text-label-sm whitespace-nowrap"
                                      title={sk.level ? `Proficiency: ${sk.level}` : undefined}
                                    >
                                      {sk.name}
                                    </span>
                                  ))}
                                  {vol.skills && vol.skills.length > 3 && (
                                    <span className="px-1.5 py-0.5 rounded-full bg-surface-container-high text-outline font-label-sm text-label-sm">
                                      +{vol.skills.length - 3}
                                    </span>
                                  )}
                                </div>
                              </td>

                              {/* Experience */}
                              <td className="py-3 px-space-md align-middle">
                                <span className="font-body-sm text-body-sm text-on-surface">
                                  {vol.pastEventsCount} {vol.pastEventsCount === 1 ? 'event' : 'events'}
                                </span>
                              </td>

                              {/* Availability */}
                              <td className="py-3 px-space-md align-middle">
                                <span
                                  className="font-body-sm text-body-sm text-on-surface-variant line-clamp-2 max-w-xs"
                                  title={availabilitySummary}
                                >
                                  {availabilitySummary}
                                </span>
                              </td>

                              {/* Max hours */}
                              <td className="py-3 px-space-md align-middle">
                                <span className="font-label-md text-label-md text-on-surface">
                                  {vol.maxHoursPerEvent ? `${vol.maxHoursPerEvent}h` : '—'}
                                </span>
                              </td>

                              {/* Status */}
                              <td className="py-3 px-space-md align-middle text-center">
                                <span
                                  className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-label-sm text-label-sm capitalize font-medium ${
                                    isActive
                                      ? 'bg-secondary-fixed text-on-secondary-fixed-variant'
                                      : 'bg-surface-container-high text-outline'
                                  }`}
                                >
                                  {vol.status || 'Active'}
                                </span>
                              </td>

                              {/* Actions */}
                              <td className="py-3 px-space-md align-middle text-right">
                                <button
                                  type="button"
                                  onClick={() => navigate(`/volunteers/${vol.id}`)}
                                  className="inline-flex items-center gap-1 px-space-sm py-1 rounded-lg text-primary hover:bg-surface-container-low font-label-sm text-label-sm font-semibold transition-colors cursor-pointer"
                                  title="View volunteer profile"
                                >
                                  <span>View profile</span>
                                  <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                                </button>
                              </td>
                            </tr>
                          )
                        })
                      ) : (
                        /* Empty State Row */
                        <tr>
                          <td className="py-20 px-space-xl" colSpan="8">
                            <div className="flex flex-col items-center justify-center p-space-xl text-center max-w-md mx-auto">
                              <div className="w-16 h-16 rounded-2xl bg-surface-container-high flex items-center justify-center mb-space-lg shadow-inner text-outline">
                                <span className="material-symbols-outlined text-[36px]">group</span>
                              </div>
                              <h3 className="font-headline-md text-headline-md text-on-surface mb-space-xs">
                                {searchQuery || selectedSkill || selectedStatus
                                  ? 'No matching volunteers'
                                  : 'No volunteers yet'}
                              </h3>
                              <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl leading-relaxed">
                                {searchQuery || selectedSkill || selectedStatus
                                  ? 'Try adjusting your search criteria or filter options to see matching volunteers.'
                                  : 'Share the registration form to start collecting volunteers.'}
                              </p>
                              {!searchQuery && !selectedSkill && !selectedStatus && (
                                <button
                                  onClick={handleCopyLink}
                                  className="flex items-center gap-space-xs px-space-lg h-10 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg transition-colors shadow-sm cursor-pointer"
                                  type="button"
                                >
                                  <span className="material-symbols-outlined text-[18px]">
                                    {copiedLink ? 'check' : 'link'}
                                  </span>
                                  <span>{copiedLink ? 'Link copied!' : 'Copy form link'}</span>
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                {/* Footer status count */}
                <div className="px-space-xl py-space-sm bg-surface-container-low/40 flex items-center justify-between text-on-surface-variant font-label-sm text-label-sm border-t border-surface-container-low">
                  <span>
                    Showing {filteredVolunteers.length} of {volunteers?.length || 0} volunteers
                  </span>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
