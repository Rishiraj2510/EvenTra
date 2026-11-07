import { useState } from 'react'
import { APP_NAME } from '../../config'
import { isRegistrationOpen } from '../../utils/registration'

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

const SKILL_LEVELS = ['Beginner', 'Intermediate', 'Advanced']

export default function VolunteerRegistrationModal({
  isOpen,
  onClose,
  event,
  form,
  existingRegistration,
  userProfile,
  currentUser,
  onSubmit,
  onUpdate,
  onWithdraw,
}) {
  if (!isOpen || !event || !form) return null

  return (
    <VolunteerRegistrationModalContent
      onClose={onClose}
      event={event}
      form={form}
      existingRegistration={existingRegistration}
      userProfile={userProfile}
      currentUser={currentUser}
      onSubmit={onSubmit}
      onUpdate={onUpdate}
      onWithdraw={onWithdraw}
    />
  )
}

function VolunteerRegistrationModalContent({
  onClose,
  event,
  form,
  existingRegistration,
  userProfile,
  currentUser,
  onSubmit,
  onUpdate,
  onWithdraw,
}) {
  const isRegistered = Boolean(existingRegistration)
  const isFormOpen = isRegistrationOpen(form)
  const isReadOnly = !isFormOpen && isRegistered

  // Initial values prefilled from existing registration or fallback to userProfile
  const [formData, setFormData] = useState(() => {
    const source = existingRegistration || userProfile || {}

    // Extract year of study number (1 to 5)
    let yearNum = 1
    if (source.yearOfStudy) {
      const match = String(source.yearOfStudy).match(/\d+/)
      if (match) yearNum = Math.min(5, Math.max(1, parseInt(match[0], 10)))
    }

    // Prefill skills from event offered skills
    // Map each offered skill to volunteer's current level or default to 'Intermediate'
    const initialSkillsMap = {}
    if (Array.isArray(source.skills)) {
      source.skills.forEach((s) => {
        if (s && s.name) {
          initialSkillsMap[s.name] = s.level || 'Intermediate'
        }
      })
    }

    // Extra answers map questionId -> answerText
    const initialExtraAnswers = {}
    if (Array.isArray(existingRegistration?.extraAnswers)) {
      existingRegistration.extraAnswers.forEach((a) => {
        if (a && a.questionId) {
          initialExtraAnswers[a.questionId] = a.answer || ''
        }
      })
    }

    return {
      phone: source.phone || '',
      department: source.department || '',
      yearOfStudy: yearNum,
      selectedSkills: initialSkillsMap, // { [skillName]: 'Beginner'|'Intermediate'|'Advanced' }
      pastEventsCount: source.pastEventsCount !== undefined ? Number(source.pastEventsCount) : 0,
      availability: Array.isArray(source.availability)
        ? JSON.parse(JSON.stringify(source.availability))
        : [],
      maxHoursPerEvent: source.maxHoursPerEvent ? Number(source.maxHoursPerEvent) : 8,
      interests: Array.isArray(source.interests) ? [...source.interests] : [],
      notificationPreference: source.notificationPreference || 'email',
      extraAnswers: initialExtraAnswers,
      consentChecked: isRegistered, // If already registered, default true
    }
  })

  const [newInterestInput, setNewInterestInput] = useState('')
  const [errors, setErrors] = useState({})
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [showWithdrawConfirm, setShowWithdrawConfirm] = useState(false)

  // Availability lookup
  const isSlotAvailable = (dayName, slotName) => {
    return formData.availability.some((a) => {
      const matchDay = a.day?.toLowerCase() === dayName.toLowerCase()
      if (!matchDay) return false
      const slot = a.slot?.toLowerCase()
      return slot === 'full day' || slot === slotName.toLowerCase()
    })
  }

  // Toggle availability
  const handleToggleAvailability = (dayName, slotName) => {
    if (isReadOnly) return
    setFormData((prev) => {
      const list = prev.availability || []
      const index = list.findIndex((a) => {
        const matchDay = a.day?.toLowerCase() === dayName.toLowerCase()
        if (!matchDay) return false
        const slot = a.slot?.toLowerCase()
        return slot === 'full day' || slot === slotName.toLowerCase()
      })

      let nextList
      if (index !== -1) {
        nextList = list.filter((_, idx) => idx !== index)
      } else {
        nextList = [...list, { day: dayName, slot: slotName }]
      }
      return { ...prev, availability: nextList }
    })
    if (errors.availability) setErrors((prev) => ({ ...prev, availability: null }))
  }

  // Toggle Skill inclusion and select level
  const handleSkillToggle = (skillName) => {
    if (isReadOnly) return
    setFormData((prev) => {
      const copy = { ...prev.selectedSkills }
      if (copy[skillName]) {
        delete copy[skillName]
      } else {
        copy[skillName] = 'Intermediate'
      }
      return { ...prev, selectedSkills: copy }
    })
  }

  const handleSkillLevelChange = (skillName, level) => {
    if (isReadOnly) return
    setFormData((prev) => ({
      ...prev,
      selectedSkills: {
        ...prev.selectedSkills,
        [skillName]: level,
      },
    }))
  }

  // Interests
  const handleAddInterest = () => {
    if (isReadOnly) return
    const trimmed = newInterestInput.trim()
    if (!trimmed) return
    if (!formData.interests.includes(trimmed)) {
      setFormData((prev) => ({
        ...prev,
        interests: [...prev.interests, trimmed],
      }))
    }
    setNewInterestInput('')
  }

  const handleRemoveInterest = (idx) => {
    if (isReadOnly) return
    setFormData((prev) => ({
      ...prev,
      interests: prev.interests.filter((_, i) => i !== idx),
    }))
  }

  // Validation
  const validate = () => {
    const newErrors = {}

    if (!formData.phone.trim()) {
      newErrors.phone = 'Phone number is required.'
    }

    if (!formData.department.trim()) {
      newErrors.department = 'Academic department is required.'
    }

    const pastCount = Number(formData.pastEventsCount)
    if (isNaN(pastCount) || pastCount < 0) {
      newErrors.pastEventsCount = 'Past events count must be 0 or greater.'
    }

    const hours = Number(formData.maxHoursPerEvent)
    if (isNaN(hours) || hours < 1) {
      newErrors.maxHoursPerEvent = 'Max hours per event must be at least 1.'
    }

    if (!formData.consentChecked) {
      newErrors.consentChecked = 'You must confirm and consent before submitting.'
    }

    // Check extra questions length
    ;(form.extraQuestions || []).forEach((q) => {
      const ans = formData.extraAnswers[q.id] || ''
      if (ans.length > 300) {
        newErrors[`q_${q.id}`] = 'Answer cannot exceed 300 characters.'
      }
    })

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  // Submit / Update handler
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (isReadOnly) {
      onClose()
      return
    }

    if (!validate()) return

    setIsSubmitting(true)
    try {
      // Build skill array
      const skillsArray = Object.entries(formData.selectedSkills).map(([name, level]) => ({
        name,
        level,
      }))

      // Build extra answers array
      const extraAnswersArray = (form.extraQuestions || []).map((q) => ({
        questionId: q.id,
        questionLabel: q.label,
        answer: formData.extraAnswers[q.id] || '',
      }))

      const payload = {
        phone: formData.phone.trim(),
        department: formData.department.trim(),
        yearOfStudy: formData.yearOfStudy,
        skills: skillsArray,
        pastEventsCount: Number(formData.pastEventsCount) || 0,
        availability: formData.availability,
        maxHoursPerEvent: Number(formData.maxHoursPerEvent) || 8,
        interests: formData.interests,
        notificationPreference: formData.notificationPreference,
        extraAnswers: extraAnswersArray,
      }

      if (isRegistered) {
        await onUpdate(payload)
      } else {
        await onSubmit(payload)
      }
      onClose()
    } catch (err) {
      setErrors({ general: err.message || 'Failed to submit registration.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  // Withdraw handler
  const handleWithdraw = async () => {
    setIsSubmitting(true)
    try {
      await onWithdraw()
      setShowWithdrawConfirm(false)
      onClose()
    } catch (err) {
      setErrors({ general: err.message || 'Failed to withdraw registration.' })
      setShowWithdrawConfirm(false)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-space-md sm:p-space-lg bg-inverse-surface/45 backdrop-blur-sm transition-opacity"
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting) onClose()
      }}
    >
      <div className="bg-surface-container-lowest w-full max-w-3xl rounded-2xl shadow-xl overflow-hidden flex flex-col max-h-[92vh]">
        {/* Modal Header */}
        <div className="px-space-xl py-space-lg bg-surface-container-lowest border-b border-surface-container-low flex items-start justify-between">
          <div className="flex flex-col gap-space-xs">
            <div className="flex items-center gap-2">
              <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">
                Event Registration
              </span>
              <span
                className={`px-2 py-0.5 rounded-full font-label-sm text-[11px] font-semibold ${
                  isReadOnly
                    ? 'bg-surface-container-high text-outline'
                    : isRegistered
                    ? 'bg-secondary-fixed text-on-secondary-fixed-variant'
                    : 'bg-primary-fixed text-on-primary-fixed-variant'
                }`}
              >
                {isReadOnly ? 'Read-only' : isRegistered ? 'Edit Registration' : 'New Registration'}
              </span>
            </div>
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight font-semibold">
              {event.name}
            </h2>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              {event.startDate}
              {event.endDate && event.endDate !== event.startDate ? ` - ${event.endDate}` : ''}
              {event.description ? ` • ${event.description}` : ''}
            </p>
          </div>

          <button
            aria-label="Close dialog"
            className="text-outline hover:text-on-surface p-space-xs rounded-lg hover:bg-surface-container-low transition-colors cursor-pointer"
            type="button"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Modal Body */}
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
          <div className="px-space-xl py-space-lg overflow-y-auto flex flex-col gap-space-xl">
            {/* General error banner */}
            {errors.general && (
              <div className="p-3.5 rounded-xl bg-error-container text-error flex items-center gap-2.5">
                <span className="material-symbols-outlined text-[20px]">error</span>
                <span className="font-label-md text-label-md font-medium">{errors.general}</span>
              </div>
            )}

            {isReadOnly && (
              <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/30 flex items-center gap-2.5">
                <span className="material-symbols-outlined text-outline text-[20px]">info</span>
                <span className="font-body-sm text-body-sm text-on-surface-variant">
                  Registration is currently closed for this event. You are viewing your submitted details in read-only mode.
                </span>
              </div>
            )}

            {/* Read-only Identity Card */}
            <div className="p-4 rounded-xl bg-surface-container-low/60 border border-outline-variant/25 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-primary-fixed text-primary flex items-center justify-center font-bold text-label-md">
                  {currentUser?.name
                    ? currentUser.name
                        .split(' ')
                        .map((n) => n[0])
                        .slice(0, 2)
                        .join('')
                        .toUpperCase()
                    : 'U'}
                </div>
                <div className="flex flex-col">
                  <span className="font-label-md text-label-md font-semibold text-on-surface">
                    {currentUser?.name || 'Registered Volunteer'}
                  </span>
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    {currentUser?.email || 'volunteer@college.edu'}
                  </span>
                </div>
              </div>
              <span className="font-label-sm text-[12px] text-outline italic self-start sm:self-auto">
                Identity verified from account (Read-only)
              </span>
            </div>

            {/* Basic Academic & Contact Info */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md">
              {/* Phone */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-phone">
                  Phone Number <span className="text-error">*</span>
                </label>
                <input
                  id="v-phone"
                  type="tel"
                  disabled={isReadOnly}
                  value={formData.phone}
                  onChange={(e) => {
                    setFormData((prev) => ({ ...prev, phone: e.target.value }))
                    if (errors.phone) setErrors((prev) => ({ ...prev, phone: null }))
                  }}
                  placeholder="+91 98765 43210"
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                />
                {errors.phone && (
                  <span className="font-body-sm text-[12px] text-error">{errors.phone}</span>
                )}
              </div>

              {/* Department */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-dept">
                  Department <span className="text-error">*</span>
                </label>
                <input
                  id="v-dept"
                  type="text"
                  disabled={isReadOnly}
                  value={formData.department}
                  onChange={(e) => {
                    setFormData((prev) => ({ ...prev, department: e.target.value }))
                    if (errors.department) setErrors((prev) => ({ ...prev, department: null }))
                  }}
                  placeholder="e.g. Computer Science"
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                />
                {errors.department && (
                  <span className="font-body-sm text-[12px] text-error">{errors.department}</span>
                )}
              </div>

              {/* Year of study (1 to 5) */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-year">
                  Year of Study <span className="text-error">*</span>
                </label>
                <select
                  id="v-year"
                  disabled={isReadOnly}
                  value={formData.yearOfStudy}
                  onChange={(e) => setFormData((prev) => ({ ...prev, yearOfStudy: Number(e.target.value) }))}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                >
                  <option value={1}>1st Year (Freshman)</option>
                  <option value={2}>2nd Year (Sophomore)</option>
                  <option value={3}>3rd Year (Junior)</option>
                  <option value={4}>4th Year (Senior)</option>
                  <option value={5}>5th Year (Postgrad / Dual)</option>
                </select>
              </div>
            </div>

            {/* Event Offered Skills Selection */}
            <div className="flex flex-col gap-space-sm pb-space-md border-b border-outline-variant/15">
              <div className="flex flex-col">
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Event Skills &amp; Proficiencies
                </h3>
                <p className="font-body-sm text-body-sm text-on-surface-variant">
                  Select which of this event’s offered skills you can contribute, along with your proficiency level.
                </p>
              </div>

              <div className="flex flex-col gap-2.5 pt-1">
                {(form.offeredSkills || []).map((skillName) => {
                  const isSelected = Boolean(formData.selectedSkills[skillName])
                  const currentLevel = formData.selectedSkills[skillName] || 'Intermediate'

                  return (
                    <div
                      key={skillName}
                      className={`p-3 rounded-xl border transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-2 ${
                        isSelected
                          ? 'bg-primary-fixed/20 border-primary/40'
                          : 'bg-surface-container-low/40 border-outline-variant/25'
                      }`}
                    >
                      <label className="flex items-center gap-2.5 cursor-pointer select-none">
                        <input
                          type="checkbox"
                          disabled={isReadOnly}
                          checked={isSelected}
                          onChange={() => handleSkillToggle(skillName)}
                          className="w-4 h-4 accent-primary rounded cursor-pointer"
                        />
                        <span className="font-label-md text-label-md font-medium text-on-surface">
                          {skillName}
                        </span>
                      </label>

                      {isSelected && (
                        <div className="flex items-center gap-1.5 self-end sm:self-auto">
                          <span className="font-label-sm text-[12px] text-outline mr-1">Level:</span>
                          {SKILL_LEVELS.map((lvl) => (
                            <button
                              key={lvl}
                              type="button"
                              disabled={isReadOnly}
                              onClick={() => handleSkillLevelChange(skillName, lvl)}
                              className={`px-2.5 py-1 rounded-lg font-label-sm text-[12px] font-semibold transition-colors cursor-pointer ${
                                currentLevel === lvl
                                  ? 'bg-primary-container text-on-primary shadow-xs'
                                  : 'bg-surface-container-low hover:bg-surface-container text-on-surface-variant'
                              }`}
                            >
                              {lvl}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  )
                })}

                {(!form.offeredSkills || form.offeredSkills.length === 0) && (
                  <p className="font-body-sm text-body-sm text-outline italic">
                    The organiser has not specified mandatory skill tags for this event.
                  </p>
                )}
              </div>
            </div>

            {/* Weekly Availability Grid */}
            <div className="flex flex-col gap-space-sm pb-space-md border-b border-outline-variant/15">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Availability Slots
                  </h3>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    {isReadOnly
                      ? 'Shift slots indicated for this event.'
                      : 'Click blocks to mark the shifts you are available to work.'}
                  </p>
                </div>

                <div className="flex items-center gap-3 font-label-sm text-label-sm">
                  <div className="flex items-center gap-1">
                    <span className="w-3 h-3 rounded bg-secondary-container border border-secondary" />
                    <span>Available</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <span className="w-3 h-3 rounded bg-surface-container-low border border-outline-variant/40" />
                    <span className="text-outline">Unavailable</span>
                  </div>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[520px]">
                  <thead>
                    <tr className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase">
                      <th className="py-2 px-3 rounded-l-lg">Time Block</th>
                      {DAYS_OF_WEEK.map((d) => (
                        <th key={d.key} className="py-2 px-2 text-center">
                          {d.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/15 font-body-sm text-body-sm">
                    {TIME_BLOCKS.map((block) => (
                      <tr key={block.name}>
                        <td className="py-2.5 px-3">
                          <div className="font-label-md text-label-md text-on-surface font-medium">
                            {block.name}
                          </div>
                          <div className="text-outline font-body-sm text-[11px]">{block.hours}</div>
                        </td>
                        {DAYS_OF_WEEK.map((day) => {
                          const available = isSlotAvailable(day.key, block.name)
                          return (
                            <td key={day.key} className="p-1.5 text-center">
                              {isReadOnly ? (
                                <div
                                  className={`w-full py-1.5 rounded-lg font-label-sm text-[11px] font-semibold flex items-center justify-center gap-1 ${
                                    available
                                      ? 'bg-secondary-container/40 text-secondary border border-secondary/30'
                                      : 'bg-surface-container-low text-outline/50 border border-transparent'
                                  }`}
                                >
                                  {available ? 'Yes' : '—'}
                                </div>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => handleToggleAvailability(day.key, block.name)}
                                  className={`w-full py-1.5 rounded-lg font-label-sm text-[11px] font-semibold transition-all flex items-center justify-center gap-1 cursor-pointer ${
                                    available
                                      ? 'bg-secondary-container/50 text-secondary border-2 border-secondary/60 hover:bg-secondary-container/70'
                                      : 'bg-surface-container-low text-outline/50 border border-outline-variant/30 hover:border-outline hover:text-on-surface'
                                  }`}
                                >
                                  {available ? 'Yes' : '—'}
                                </button>
                              )}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Experience, Max Hours, Preference, Interests */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md pb-space-md border-b border-outline-variant/15">
              {/* Past events count */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-past-events">
                  Past Events Staffed (&gt;= 0)
                </label>
                <input
                  id="v-past-events"
                  type="number"
                  min="0"
                  disabled={isReadOnly}
                  value={formData.pastEventsCount}
                  onChange={(e) => setFormData((prev) => ({ ...prev, pastEventsCount: e.target.value }))}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                />
                {errors.pastEventsCount && (
                  <span className="font-body-sm text-[12px] text-error">{errors.pastEventsCount}</span>
                )}
              </div>

              {/* Max hours per event */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-max-hours">
                  Max Hours Committed (&gt;= 1)
                </label>
                <input
                  id="v-max-hours"
                  type="number"
                  min="1"
                  disabled={isReadOnly}
                  value={formData.maxHoursPerEvent}
                  onChange={(e) => setFormData((prev) => ({ ...prev, maxHoursPerEvent: e.target.value }))}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                />
                {errors.maxHoursPerEvent && (
                  <span className="font-body-sm text-[12px] text-error">{errors.maxHoursPerEvent}</span>
                )}
              </div>

              {/* Notification Preference */}
              <div className="flex flex-col gap-1">
                <label className="font-label-md text-label-md text-on-surface font-medium" htmlFor="v-notification">
                  Notification Preference
                </label>
                <select
                  id="v-notification"
                  disabled={isReadOnly}
                  value={formData.notificationPreference}
                  onChange={(e) => setFormData((prev) => ({ ...prev, notificationPreference: e.target.value }))}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                >
                  <option value="email">Email</option>
                  <option value="whatsapp">WhatsApp</option>
                </select>
              </div>
            </div>

            {/* Interests Chips */}
            <div className="flex flex-col gap-2 pb-space-md border-b border-outline-variant/15">
              <label className="font-label-md text-label-md text-on-surface font-medium">
                Operational Interests &amp; Preferences
              </label>
              <div className="flex flex-wrap gap-2">
                {formData.interests.map((interest, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-container-low text-on-surface font-label-md text-label-md border border-outline-variant/30"
                  >
                    <span>{interest}</span>
                    {!isReadOnly && (
                      <button
                        type="button"
                        onClick={() => handleRemoveInterest(idx)}
                        className="hover:opacity-75 cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[14px]">close</span>
                      </button>
                    )}
                  </span>
                ))}
              </div>
              {!isReadOnly && (
                <div className="flex items-center gap-2 pt-1 max-w-md">
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
                    placeholder="e.g. Stage Management, Hackathons..."
                    className="h-9 px-3 rounded-lg bg-surface-container-low text-on-surface font-body-sm text-body-sm flex-1 focus:outline-none focus:ring-1 focus:ring-primary/20"
                  />
                  <button
                    type="button"
                    onClick={handleAddInterest}
                    className="px-3 h-9 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-sm text-label-sm cursor-pointer"
                  >
                    Add
                  </button>
                </div>
              )}
            </div>

            {/* Extra Organiser Questions */}
            {(form.extraQuestions || []).length > 0 && (
              <div className="flex flex-col gap-space-md pb-space-md border-b border-outline-variant/15">
                <div className="flex flex-col">
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Organiser Specific Questions
                  </h3>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    Please provide short answers to these event-specific questions (max 300 characters each).
                  </p>
                </div>

                <div className="flex flex-col gap-3">
                  {form.extraQuestions.map((q, idx) => {
                    const ans = formData.extraAnswers[q.id] || ''
                    return (
                      <div key={q.id || idx} className="flex flex-col gap-1.5">
                        <label className="font-label-md text-label-md text-on-surface font-medium">
                          {idx + 1}. {q.label}
                        </label>
                        <textarea
                          rows={2}
                          maxLength={300}
                          disabled={isReadOnly}
                          value={ans}
                          onChange={(e) => {
                            const val = e.target.value
                            setFormData((prev) => ({
                              ...prev,
                              extraAnswers: {
                                ...prev.extraAnswers,
                                [q.id]: val,
                              },
                            }))
                          }}
                          placeholder="Your answer..."
                          className="p-3 rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:outline-none focus:ring-2 focus:ring-primary/20 resize-none disabled:opacity-60"
                        />
                        <div className="flex items-center justify-between">
                          {errors[`q_${q.id}`] && (
                            <span className="font-body-sm text-[12px] text-error">
                              {errors[`q_${q.id}`]}
                            </span>
                          )}
                          <span className="font-body-sm text-[11px] text-outline ml-auto">
                            {ans.length} / 300 chars
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}

            {/* Consent Checkbox */}
            <div className="flex flex-col gap-1">
              <label className="flex items-start gap-2.5 cursor-pointer select-none">
                <input
                  type="checkbox"
                  disabled={isReadOnly}
                  checked={formData.consentChecked}
                  onChange={(e) => {
                    setFormData((prev) => ({ ...prev, consentChecked: e.target.checked }))
                    if (errors.consentChecked) setErrors((prev) => ({ ...prev, consentChecked: null }))
                  }}
                  className="w-4 h-4 mt-0.5 accent-primary rounded cursor-pointer"
                />
                <span className="font-body-sm text-body-sm text-on-surface leading-normal">
                  I confirm that the scheduling availability and contact details provided are accurate, and I agree to fulfill assigned tasks for this collegiate event on {APP_NAME}.
                </span>
              </label>
              {errors.consentChecked && (
                <span className="font-body-sm text-[12px] text-error pl-6.5">{errors.consentChecked}</span>
              )}
            </div>
          </div>

          {/* Modal Footer */}
          <div className="px-space-xl py-space-md bg-surface-container-lowest border-t border-surface-container-low flex items-center justify-between">
            <div>
              {/* Withdraw Button if editing and form is open */}
              {isRegistered && isFormOpen && (
                <button
                  type="button"
                  disabled={isSubmitting}
                  onClick={() => setShowWithdrawConfirm(true)}
                  className="text-error hover:bg-error/10 px-3 py-1.5 rounded-lg font-label-md text-label-md transition-colors cursor-pointer"
                >
                  Withdraw registration
                </button>
              )}
            </div>

            <div className="flex items-center gap-space-sm">
              <button
                type="button"
                disabled={isSubmitting}
                onClick={onClose}
                className="px-space-lg h-10 rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-md text-label-md transition-colors cursor-pointer"
              >
                {isReadOnly ? 'Close' : 'Cancel'}
              </button>

              {!isReadOnly && (
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="flex items-center gap-1.5 px-space-xl h-10 rounded-lg bg-primary-container text-on-primary hover:bg-primary font-label-lg text-label-lg font-semibold shadow-sm transition-all cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? (
                    'Saving...'
                  ) : isRegistered ? (
                    'Update Registration'
                  ) : (
                    'Submit Registration'
                  )}
                </button>
              )}
            </div>
          </div>
        </form>
      </div>

      {/* Confirmation Dialog for Withdraw */}
      {showWithdrawConfirm && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center p-space-md bg-inverse-surface/50 backdrop-blur-xs"
          onClick={(e) => {
            if (e.target === e.currentTarget && !isSubmitting) setShowWithdrawConfirm(false)
          }}
        >
          <div className="bg-surface-container-lowest w-full max-w-md rounded-2xl shadow-xl p-6 flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-error-container text-error flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">warning</span>
              </div>
              <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                Withdraw Registration?
              </h3>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
              Are you sure you want to withdraw your volunteer application for <span className="font-semibold text-on-surface">{event.name}</span>? You can register again while the form remains open.
            </p>
            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                disabled={isSubmitting}
                onClick={() => setShowWithdrawConfirm(false)}
                className="px-4 py-2 rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-md"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isSubmitting}
                onClick={handleWithdraw}
                className="px-4 py-2 rounded-lg bg-error text-on-error font-label-md font-semibold hover:bg-error/90 shadow-sm cursor-pointer disabled:opacity-50"
              >
                {isSubmitting ? 'Withdrawing...' : 'Yes, Withdraw'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

