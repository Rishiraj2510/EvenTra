import { useState } from 'react'
import { Link } from 'react-router-dom'
import StarRating from '../common/StarRating'

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

export default function VolunteerProfileView({
  volunteer,
  history,
  showBackLink = false,
  isEditable = false,
  onSave = null,
}) {
  // Edit mode state for editable profile
  const [isEditing, setIsEditing] = useState(false)
  const [formData, setFormData] = useState({
    phone: volunteer?.phone || '',
    maxHoursPerEvent: volunteer?.maxHoursPerEvent || 8,
    availability: volunteer?.availability ? JSON.parse(JSON.stringify(volunteer.availability)) : [],
    interests: volunteer?.interests ? [...volunteer.interests] : [],
  })
  const [newInterestInput, setNewInterestInput] = useState('')
  const [formErrors, setFormErrors] = useState({})
  const [isSaving, setIsSaving] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)

  // Sync formData when volunteer prop changes (or exiting edit mode)
  const handleStartEdit = () => {
    setFormData({
      phone: volunteer?.phone || '',
      maxHoursPerEvent: volunteer?.maxHoursPerEvent || 8,
      availability: volunteer?.availability ? JSON.parse(JSON.stringify(volunteer.availability)) : [],
      interests: volunteer?.interests ? [...volunteer.interests] : [],
    })
    setNewInterestInput('')
    setFormErrors({})
    setIsEditing(true)
  }

  const handleCancelEdit = () => {
    setIsEditing(false)
    setFormErrors({})
    setNewInterestInput('')
  }

  // Toggle cell availability
  const handleToggleAvailability = (dayName, slotName) => {
    setFormData((prev) => {
      const currentList = prev.availability || []
      // Check if slot currently matches
      const index = currentList.findIndex((a) => {
        const matchDay = a.day?.toLowerCase() === dayName.toLowerCase()
        if (!matchDay) return false
        const slot = a.slot?.toLowerCase()
        return slot === 'full day' || slot === slotName.toLowerCase()
      })

      let nextList
      if (index !== -1) {
        // Remove matching slot
        nextList = currentList.filter((_, idx) => idx !== index)
      } else {
        // Add specific slot
        nextList = [...currentList, { day: dayName, slot: slotName }]
      }

      return {
        ...prev,
        availability: nextList,
      }
    })
  }

  // Add interest chip
  const handleAddInterest = () => {
    const trimmed = newInterestInput.trim()
    if (!trimmed) return
    if (formData.interests.includes(trimmed)) {
      setNewInterestInput('')
      return
    }
    setFormData((prev) => ({
      ...prev,
      interests: [...prev.interests, trimmed],
    }))
    setNewInterestInput('')
  }

  // Remove interest chip
  const handleRemoveInterest = (idxToRemove) => {
    setFormData((prev) => ({
      ...prev,
      interests: prev.interests.filter((_, idx) => idx !== idxToRemove),
    }))
  }

  // Save handler with validations
  const handleSaveClick = async () => {
    const errors = {}

    // 1. Phone validation: not empty
    if (!formData.phone || !formData.phone.trim()) {
      errors.phone = 'Phone number is required.'
    }

    // 2. Max hours validation: at least 1
    const hoursNum = Number(formData.maxHoursPerEvent)
    if (isNaN(hoursNum) || hoursNum < 1) {
      errors.maxHoursPerEvent = 'Max hours per event must be at least 1.'
    }

    // 3. Availability validation: at least one availability cell
    if (!formData.availability || formData.availability.length === 0) {
      errors.availability = 'Please select at least one availability slot.'
    }

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors)
      return
    }

    setFormErrors({})
    setIsSaving(true)

    try {
      if (onSave) {
        await onSave({
          phone: formData.phone.trim(),
          maxHoursPerEvent: Math.round(hoursNum),
          availability: formData.availability,
          interests: formData.interests,
        })
      }
      setIsSaving(false)
      setIsEditing(false)
      setSaveSuccess(true)
      setTimeout(() => setSaveSuccess(false), 2500)
    } catch (err) {
      setIsSaving(false)
      setFormErrors({ general: err.message || 'Failed to save changes.' })
    }
  }

  // Derive initials
  const initials = volunteer.fullName
    ? volunteer.fullName
        .split(' ')
        .map((n) => n[0])
        .slice(0, 2)
        .join('')
        .toUpperCase()
    : 'VO'

  // Availability lookup helper
  const isAvailableSlot = (dayName, slotName, sourceList) => {
    const list = sourceList || volunteer.availability
    if (!list || !Array.isArray(list)) return false
    return list.some((a) => {
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

  const pastAssignments = history?.assignments || []
  const averageRating = history?.averageRating

  return (
    <div className="flex flex-col w-full pb-16">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Navigation Row (Only for organiser view with Back link) */}
        {showBackLink ? (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-space-xs text-outline font-label-md text-label-md">
              <Link to="/volunteers" className="hover:text-primary transition-colors cursor-pointer">
                Volunteers
              </Link>
              <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              <span className="text-on-surface font-label-lg text-label-lg">{volunteer.fullName}</span>
            </div>

            <Link
              to="/volunteers"
              className="flex items-center gap-1.5 px-space-md py-1.5 rounded-lg bg-surface-container-lowest hover:bg-surface-container-low text-on-surface-variant hover:text-on-surface border border-outline-variant/30 font-label-md text-label-md transition-colors cursor-pointer shadow-xs"
            >
              <span className="material-symbols-outlined text-[18px]">arrow_back</span>
              <span>Back to Volunteers</span>
            </Link>
          </div>
        ) : (
          /* Volunteer Portal Header Ribbon */
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-md">
            <div className="flex flex-col gap-space-xs">
              <div className="flex items-center gap-space-sm text-primary font-label-sm uppercase tracking-wider">
                <span className="material-symbols-outlined text-[16px]">badge</span>
                <span>Volunteer Profile Portal</span>
              </div>
              <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
                My Profile
              </h1>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
                Manage your volunteer contact info, weekly availability schedule, workload capacity, and task interests.
              </p>
            </div>

            {/* Edit / Save Action Button on My Profile Page */}
            {isEditable && !isEditing && (
              <div className="flex items-center gap-space-sm self-start sm:self-center">
                {saveSuccess && (
                  <span className="flex items-center gap-1 text-emerald-600 font-label-sm text-label-sm animate-fade-in mr-2">
                    <span className="material-symbols-outlined text-[16px]">check_circle</span>
                    Saved
                  </span>
                )}
                <button
                  type="button"
                  onClick={handleStartEdit}
                  className="flex items-center gap-space-xs px-space-lg py-2 rounded-lg bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm hover:bg-primary transition-all cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[18px]">edit</span>
                  <span>Edit Profile</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* Global Error Banner if general save failure */}
        {formErrors.general && (
          <div className="p-space-md rounded-xl bg-error-container text-error text-body-md flex items-center gap-space-sm">
            <span className="material-symbols-outlined text-[20px]">error</span>
            <span>{formErrors.general}</span>
          </div>
        )}

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
                  <h2 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
                    {volunteer.fullName}
                  </h2>
                  <span
                    className={`inline-flex items-center px-3 py-1 rounded-full font-label-sm text-label-sm font-semibold capitalize ${
                      volunteer.status === 'active'
                        ? 'bg-secondary-container/40 text-secondary border border-secondary/30'
                        : 'bg-surface-container text-outline border border-outline-variant/30'
                    }`}
                  >
                    {volunteer.status || 'Active'}
                  </span>
                </div>

                <div className="flex flex-wrap items-center gap-x-space-md gap-y-1 text-on-surface-variant font-body-md text-body-md">
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined text-[18px] text-outline">school</span>
                    <span>{volunteer.department}</span>
                  </span>
                  {volunteer.yearOfStudy && (
                    <>
                      <span className="w-1 h-1 rounded-full bg-outline-variant" />
                      <span>{volunteer.yearOfStudy}</span>
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* Contact Details Block */}
            <div className="flex flex-col sm:flex-row lg:flex-col gap-space-md pt-4 lg:pt-0 border-t lg:border-t-0 border-outline-variant/20">
              <div className="flex items-center gap-space-sm text-on-surface-variant font-label-md text-label-md">
                <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-outline shrink-0">
                  <span className="material-symbols-outlined text-[18px]">mail</span>
                </div>
                <span className="truncate">{volunteer.email}</span>
              </div>

              {/* Editable or Display Phone */}
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-space-sm text-on-surface-variant font-label-md text-label-md">
                  <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-outline shrink-0">
                    <span className="material-symbols-outlined text-[18px]">call</span>
                  </div>
                  {isEditing ? (
                    <div className="flex flex-col">
                      <input
                        type="text"
                        value={formData.phone}
                        onChange={(e) => setFormData((prev) => ({ ...prev, phone: e.target.value }))}
                        placeholder="+91 98765 43210"
                        className="px-2.5 py-1 rounded-lg bg-surface-container-low border border-outline-variant/40 text-on-surface text-label-md focus:outline-none focus:ring-1 focus:ring-primary w-48"
                      />
                    </div>
                  ) : (
                    <span>{volunteer.phone || '—'}</span>
                  )}
                </div>
                {isEditing && formErrors.phone && (
                  <span className="text-error text-body-sm pl-10">{formErrors.phone}</span>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 2-Column Responsive Body Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-space-xl items-start">
          {/* Left 2 Columns: Skills, Availability, Past Assignments */}
          <div className="lg:col-span-2 flex flex-col gap-space-xl">
            {/* Skills Card (Read-only as per specification) */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6">
              <div className="flex items-center justify-between pb-4 mb-4 border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">psychology</span>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Skills &amp; Competencies
                  </h3>
                </div>
                <span className="font-label-sm text-label-sm text-outline">
                  {volunteer.skills?.length || 0} registered (read-only)
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

            {/* Weekly Availability Grid (Interactive in Edit mode) */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 mb-4 border-b border-outline-variant/20">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-primary text-[22px]">calendar_view_week</span>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Weekly Availability
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      {isEditing
                        ? 'Click blocks to toggle your availability.'
                        : 'Recurring shift blocks committed to.'}
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

              {isEditing && formErrors.availability && (
                <div className="mb-3 p-2.5 rounded-lg bg-error-container text-error text-body-sm flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px]">error</span>
                  <span>{formErrors.availability}</span>
                </div>
              )}

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
                          const available = isAvailableSlot(
                            day.key,
                            block.name,
                            isEditing ? formData.availability : volunteer.availability
                          )
                          return (
                            <td key={day.key} className="p-1.5 text-center">
                              {isEditing ? (
                                <button
                                  type="button"
                                  onClick={() => handleToggleAvailability(day.key, block.name)}
                                  className={`w-full py-2 rounded-lg font-label-sm text-[12px] font-semibold transition-all flex items-center justify-center gap-1 cursor-pointer ${
                                    available
                                      ? 'bg-secondary-container/50 text-secondary border-2 border-secondary/60 hover:bg-secondary-container/70'
                                      : 'bg-surface-container-low text-outline/50 border border-outline-variant/30 hover:border-outline hover:text-on-surface'
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
                                </button>
                              ) : (
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
                              )}
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
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Past Event Assignments
                    </h3>
                    <p className="font-body-sm text-body-sm text-on-surface-variant">
                      Operational duties performed in previous festivals and hackathons.
                    </p>
                  </div>
                </div>

                <span className="font-label-sm text-label-sm text-outline">
                  {pastAssignments.length} recorded
                </span>
              </div>

              {pastAssignments.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[500px]">
                    <thead>
                      <tr className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase">
                        <th className="py-2.5 px-3 rounded-l-lg">Event</th>
                        <th className="py-2.5 px-3">Role / Duty</th>
                        <th className="py-2.5 px-3 text-center">Fit Score</th>
                        <th className="py-2.5 px-3 rounded-r-lg text-right">Rating</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant/15 font-body-sm text-body-sm">
                      {pastAssignments.map((asg, idx) => (
                        <tr key={idx} className="hover:bg-surface-container-low/50 transition-colors">
                          <td className="py-3 px-3 font-label-md text-label-md text-on-surface font-medium">
                            {asg.eventName}
                          </td>
                          <td className="py-3 px-3 text-on-surface-variant">{asg.taskTitle}</td>
                          <td className="py-3 px-3 text-center">
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full font-label-sm text-[11px] font-semibold bg-primary-fixed text-primary">
                              {asg.fit}%
                            </span>
                          </td>
                          <td className="py-3 px-3 text-right">
                            {asg.rating ? (
                              <div className="flex items-center justify-end gap-1 text-tertiary-container font-label-md text-label-md">
                                <span
                                  className="material-symbols-outlined text-[16px]"
                                  style={{ fontVariationSettings: "'FILL' 1" }}
                                >
                                  star
                                </span>
                                <span>{asg.rating}</span>
                              </div>
                            ) : (
                              <span className="text-outline font-label-sm text-label-sm">Unrated</span>
                            )}
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
                  <h4 className="font-headline-sm text-headline-sm text-on-surface mb-1">
                    No past assignments yet
                  </h4>
                  <p className="font-body-md text-body-md text-outline max-w-sm">
                    This volunteer has not yet been assigned to completed events on Eventra.
                  </p>
                </div>
              )}
            </section>
          </div>

          {/* Right Column: Performance, Experience Tiles, Interests */}
          <div className="flex flex-col gap-space-xl">
            {/* Performance Card (Read-only) */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-tertiary text-[22px]">grade</span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Performance &amp; Rating
                </h3>
              </div>

              {averageRating !== null && averageRating !== undefined ? (
                <div className="flex flex-col items-center justify-center p-6 rounded-xl bg-surface-container-low border border-outline-variant/20 text-center gap-2">
                  <span className="font-headline-xl text-[44px] font-bold text-on-surface leading-none">
                    {(Math.round(Number(averageRating) * 10) / 10).toFixed(1)}
                  </span>
                  <div className="flex items-center gap-0.5 my-1">
                    <StarRating value={Math.round(Number(averageRating) * 10) / 10} size="22px" />
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

            {/* Experience & Workload Capacity (Max hours is editable in Edit mode) */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-primary text-[22px]">timelapse</span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Experience &amp; Capacity
                </h3>
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
                    Total festivals staffed (read-only)
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                  <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                    Max Hours
                  </span>
                  {isEditing ? (
                    <div className="flex flex-col mt-0.5">
                      <input
                        type="number"
                        min="1"
                        max="168"
                        value={formData.maxHoursPerEvent}
                        onChange={(e) =>
                          setFormData((prev) => ({ ...prev, maxHoursPerEvent: e.target.value }))
                        }
                        className="px-2 py-1 bg-surface-container-lowest border border-outline-variant/40 rounded-lg text-primary font-bold text-headline-sm w-20 focus:outline-none focus:ring-1 focus:ring-primary"
                      />
                      {formErrors.maxHoursPerEvent && (
                        <span className="text-error text-[10px] mt-1">{formErrors.maxHoursPerEvent}</span>
                      )}
                    </div>
                  ) : (
                    <span className="font-headline-md text-headline-md font-bold text-primary">
                      {volunteer.maxHoursPerEvent ? `${volunteer.maxHoursPerEvent}h` : '—'}
                    </span>
                  )}
                  <span className="font-body-sm text-[11px] text-outline">
                    Cap per event cycle
                  </span>
                </div>
              </div>
            </section>

            {/* Task Interests Chips (Editable in Edit mode) */}
            <section className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col gap-4">
              <div className="flex items-center gap-space-sm pb-3 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-primary text-[22px]">favorite</span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Task Interests
                </h3>
              </div>

              {isEditing ? (
                <div className="flex flex-col gap-3">
                  <div className="flex flex-wrap gap-2">
                    {formData.interests.map((interest, idx) => (
                      <span
                        key={idx}
                        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-primary/10 text-primary font-label-sm text-label-sm border border-primary/20"
                      >
                        <span>{interest}</span>
                        <button
                          type="button"
                          onClick={() => handleRemoveInterest(idx)}
                          className="hover:text-error transition-colors cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[14px]">close</span>
                        </button>
                      </span>
                    ))}
                  </div>

                  <div className="flex items-center gap-2 mt-1">
                    <input
                      type="text"
                      value={newInterestInput}
                      onChange={(e) => setNewInterestInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          handleAddInterest()
                        }
                      }}
                      placeholder="Add an interest (e.g. Stage Logistics)..."
                      className="flex-1 px-3 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant/40 text-on-surface text-body-sm focus:outline-none focus:ring-1 focus:ring-primary"
                    />
                    <button
                      type="button"
                      onClick={handleAddInterest}
                      className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-sm text-label-sm border border-outline-variant/30 cursor-pointer"
                    >
                      Add
                    </button>
                  </div>
                </div>
              ) : volunteer.interests && volunteer.interests.length > 0 ? (
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

        {/* Edit Action Ribbon when isEditing */}
        {isEditing && (
          <div className="fixed bottom-0 left-64 right-0 bg-surface-container-lowest/95 backdrop-blur-md border-t border-outline-variant/30 px-margin-lg py-space-md flex items-center justify-end gap-space-md z-40 shadow-lg animate-fade-in">
            <button
              type="button"
              disabled={isSaving}
              onClick={handleCancelEdit}
              className="px-space-lg py-2 rounded-lg border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={isSaving}
              onClick={handleSaveClick}
              className="flex items-center gap-space-xs px-space-xl py-2 rounded-lg bg-primary-container text-on-primary font-label-lg font-semibold shadow-sm hover:bg-primary transition-all cursor-pointer disabled:opacity-50"
            >
              {isSaving ? (
                <>
                  <div className="w-4 h-4 rounded-full border-2 border-white/20 border-t-white animate-spin" />
                  <span>Saving changes...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]">check</span>
                  <span>Save Profile</span>
                </>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
