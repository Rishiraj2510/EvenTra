import { useState, useEffect } from 'react'

export default function CreateTaskModal({
  isOpen,
  onClose,
  onSubmit,
  initialData = null,
  selectedEventId = '',
  events = [],
}) {
  const isEditing = Boolean(initialData?.id)

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    eventId: selectedEventId || '',
    date: '',
    startTime: '',
    endTime: '',
    headcount: 1,
    location: '',
    critical: false,
    status: 'pending',
  })

  const [mandatorySkills, setMandatorySkills] = useState([])
  const [preferredSkills, setPreferredSkills] = useState([])
  const [mandatoryInput, setMandatoryInput] = useState('')
  const [preferredInput, setPreferredInput] = useState('')

  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  // Populate state when opening modal or editing
  useEffect(() => {
    if (initialData) {
      setFormData({
        title: initialData.title || '',
        description: initialData.description || '',
        eventId: initialData.eventId || selectedEventId || '',
        date: initialData.date || '',
        startTime: initialData.startTime || '',
        endTime: initialData.endTime || '',
        headcount: initialData.headcount || 1,
        location: initialData.location || '',
        critical: Boolean(initialData.critical),
        status: initialData.status || 'pending',
      })
      setMandatorySkills(initialData.mandatorySkills || [])
      setPreferredSkills(initialData.preferredSkills || [])
    } else {
      setFormData({
        title: '',
        description: '',
        eventId: selectedEventId || '',
        date: '',
        startTime: '',
        endTime: '',
        headcount: 1,
        location: '',
        critical: false,
        status: 'pending',
      })
      setMandatorySkills([])
      setPreferredSkills([])
    }
    setMandatoryInput('')
    setPreferredInput('')
    setErrors({})
  }, [initialData, selectedEventId, isOpen])

  if (!isOpen) return null

  const handleFieldChange = (e) => {
    const { name, value, type, checked } = e.target
    const val = type === 'checkbox' ? checked : value
    setFormData((prev) => ({ ...prev, [name]: val }))

    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }))
    }

    // Time validation check on change
    if (name === 'startTime' || name === 'endTime') {
      const s = name === 'startTime' ? value : formData.startTime
      const end = name === 'endTime' ? value : formData.endTime
      if (s && end && end <= s) {
        setErrors((prev) => ({ ...prev, endTime: 'End time must be after start time' }))
      } else {
        setErrors((prev) => ({ ...prev, endTime: '' }))
      }
    }
  }

  // Tag inputs helper
  const addTag = (type) => {
    if (type === 'mandatory') {
      const trimmed = mandatoryInput.trim()
      if (trimmed && !mandatorySkills.includes(trimmed)) {
        setMandatorySkills((prev) => [...prev, trimmed])
        setMandatoryInput('')
      }
    } else {
      const trimmed = preferredInput.trim()
      if (trimmed && !preferredSkills.includes(trimmed)) {
        setPreferredSkills((prev) => [...prev, trimmed])
        setPreferredInput('')
      }
    }
  }

  const removeTag = (type, tagToRemove) => {
    if (type === 'mandatory') {
      setMandatorySkills((prev) => prev.filter((tag) => tag !== tagToRemove))
    } else {
      setPreferredSkills((prev) => prev.filter((tag) => tag !== tagToRemove))
    }
  }

  const validate = () => {
    const newErrors = {}
    if (!formData.title.trim()) {
      newErrors.title = 'Task title is required'
    }
    if (!formData.eventId) {
      newErrors.eventId = 'Event must be selected'
    }
    if (!formData.date) {
      newErrors.date = 'Date is required'
    }
    if (!formData.startTime) {
      newErrors.startTime = 'Start time is required'
    }
    if (!formData.endTime) {
      newErrors.endTime = 'End time is required'
    } else if (formData.startTime && formData.endTime <= formData.startTime) {
      newErrors.endTime = 'End time must be after start time'
    }
    const parsedHeadcount = parseInt(formData.headcount, 10)
    if (isNaN(parsedHeadcount) || parsedHeadcount < 1) {
      newErrors.headcount = 'Headcount must be an integer of at least 1'
    }
    if (!formData.location.trim()) {
      newErrors.location = 'Location is required'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      await onSubmit({
        ...formData,
        headcount: parseInt(formData.headcount, 10),
        mandatorySkills,
        preferredSkills,
      })
      onClose()
    } catch (err) {
      console.error('Error submitting task:', err)
      setErrors((prev) => ({
        ...prev,
        submit: err instanceof Error ? err.message : 'Failed to save task',
      }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-space-md sm:p-space-lg bg-inverse-surface/45 backdrop-blur-sm transition-opacity"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="bg-surface-container-lowest w-full max-w-2xl rounded-xl shadow-xl overflow-hidden flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="px-space-xl py-space-lg bg-surface-container-lowest flex items-start justify-between border-b border-surface-container-low">
          <div className="flex flex-col gap-space-xs">
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight">
              {isEditing ? 'Edit Task' : 'Create New Task'}
            </h2>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Define operational parameters, headcount, and volunteer skill constraints.
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

        {/* Body Form */}
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden" noValidate>
          <div className="px-space-xl py-space-md overflow-y-auto flex flex-col gap-space-lg">
            {errors.submit && (
              <div className="p-3 rounded-lg bg-error-container text-on-error-container text-body-sm">
                {errors.submit}
              </div>
            )}

            {/* Event Association (if not pre-locked) */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface" htmlFor="taskEventId">
                Associated Event <span className="text-error">*</span>
              </label>
              <div className="relative">
                <select
                  id="taskEventId"
                  name="eventId"
                  value={formData.eventId}
                  onChange={handleFieldChange}
                  className="w-full h-10 pl-space-md pr-10 rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none appearance-none transition-colors"
                >
                  <option value="" disabled>Select an event...</option>
                  {events?.map((evt) => (
                    <option key={evt.id} value={evt.id}>
                      {evt.name}
                    </option>
                  ))}
                </select>
                <span className="material-symbols-outlined text-[20px] text-outline absolute right-space-md top-1/2 -translate-y-1/2 pointer-events-none">
                  expand_more
                </span>
              </div>
              {errors.eventId && (
                <span className="font-body-sm text-body-sm text-error">{errors.eventId}</span>
              )}
            </div>

            {/* Task Title */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface" htmlFor="taskTitle">
                Task Title <span className="text-error">*</span>
              </label>
              <input
                id="taskTitle"
                name="title"
                value={formData.title}
                onChange={handleFieldChange}
                placeholder="e.g. Stage Management & Audio Setup"
                type="text"
                className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
              />
              {errors.title && (
                <span className="font-body-sm text-body-sm text-error">{errors.title}</span>
              )}
            </div>

            {/* Description */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface" htmlFor="taskDesc">
                Description
              </label>
              <textarea
                id="taskDesc"
                name="description"
                value={formData.description}
                onChange={handleFieldChange}
                placeholder="Briefly describe operational responsibilities..."
                rows="2"
                className="p-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none resize-none transition-colors"
              />
            </div>

            {/* Date, Start Time, End Time */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md">
              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="taskDate">
                  Date <span className="text-error">*</span>
                </label>
                <input
                  id="taskDate"
                  name="date"
                  type="date"
                  value={formData.date}
                  onChange={handleFieldChange}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                {errors.date && (
                  <span className="font-body-sm text-body-sm text-error">{errors.date}</span>
                )}
              </div>

              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="taskStartTime">
                  Start Time <span className="text-error">*</span>
                </label>
                <input
                  id="taskStartTime"
                  name="startTime"
                  type="time"
                  value={formData.startTime}
                  onChange={handleFieldChange}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                {errors.startTime && (
                  <span className="font-body-sm text-body-sm text-error">{errors.startTime}</span>
                )}
              </div>

              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="taskEndTime">
                  End Time <span className="text-error">*</span>
                </label>
                <input
                  id="taskEndTime"
                  name="endTime"
                  type="time"
                  value={formData.endTime}
                  onChange={handleFieldChange}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                {errors.endTime && (
                  <span className="font-body-sm text-body-sm text-error">{errors.endTime}</span>
                )}
              </div>
            </div>

            {/* Headcount & Location */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="taskHeadcount">
                  Volunteer Headcount <span className="text-error">*</span>
                </label>
                <input
                  id="taskHeadcount"
                  name="headcount"
                  type="number"
                  min="1"
                  step="1"
                  value={formData.headcount}
                  onChange={handleFieldChange}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                {errors.headcount && (
                  <span className="font-body-sm text-body-sm text-error">{errors.headcount}</span>
                )}
              </div>

              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="taskLocation">
                  Location / Venue <span className="text-error">*</span>
                </label>
                <input
                  id="taskLocation"
                  name="location"
                  type="text"
                  placeholder="e.g. Main Auditorium Block A"
                  value={formData.location}
                  onChange={handleFieldChange}
                  className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                {errors.location && (
                  <span className="font-body-sm text-body-sm text-error">{errors.location}</span>
                )}
              </div>
            </div>

            {/* Mandatory Skills (Tag Input) */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface">
                Mandatory Skills
              </label>
              <div className="flex gap-2">
                <input
                  value={mandatoryInput}
                  onChange={(e) => setMandatoryInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      addTag('mandatory')
                    }
                  }}
                  placeholder="Type a skill and click Add or press Enter..."
                  className="flex-1 h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                <button
                  type="button"
                  onClick={() => addTag('mandatory')}
                  className="px-4 h-10 rounded-lg bg-surface-container-high hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
                >
                  Add
                </button>
              </div>
              <div className="flex flex-wrap gap-1.5 mt-1.5 min-h-[28px]">
                {mandatorySkills.length === 0 ? (
                  <span className="font-body-sm text-body-sm text-outline italic">No mandatory skills added</span>
                ) : (
                  mandatorySkills.map((skill) => (
                    <span
                      key={skill}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-primary-fixed text-on-primary-fixed-variant font-label-sm text-label-sm"
                    >
                      <span>{skill}</span>
                      <button
                        type="button"
                        onClick={() => removeTag('mandatory', skill)}
                        className="hover:text-error cursor-pointer"
                        aria-label={`Remove ${skill}`}
                      >
                        <span className="material-symbols-outlined text-[14px]">close</span>
                      </button>
                    </span>
                  ))
                )}
              </div>
            </div>

            {/* Preferred Skills (Tag Input) */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface">
                Preferred Skills
              </label>
              <div className="flex gap-2">
                <input
                  value={preferredInput}
                  onChange={(e) => setPreferredInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      addTag('preferred')
                    }
                  }}
                  placeholder="Type a preferred skill and click Add..."
                  className="flex-1 h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                />
                <button
                  type="button"
                  onClick={() => addTag('preferred')}
                  className="px-4 h-10 rounded-lg bg-surface-container-high hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
                >
                  Add
                </button>
              </div>
              <div className="flex flex-wrap gap-1.5 mt-1.5 min-h-[28px]">
                {preferredSkills.length === 0 ? (
                  <span className="font-body-sm text-body-sm text-outline italic">No preferred skills added</span>
                ) : (
                  preferredSkills.map((skill) => (
                    <span
                      key={skill}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container-high text-on-surface font-label-sm text-label-sm"
                    >
                      <span>{skill}</span>
                      <button
                        type="button"
                        onClick={() => removeTag('preferred', skill)}
                        className="hover:text-error cursor-pointer"
                        aria-label={`Remove ${skill}`}
                      >
                        <span className="material-symbols-outlined text-[14px]">close</span>
                      </button>
                    </span>
                  ))
                )}
              </div>
            </div>

            {/* Critical Task Toggle */}
            <div className="p-space-md rounded-xl bg-surface-container-low flex items-center justify-between gap-space-md">
              <div className="flex flex-col">
                <div className="flex items-center gap-space-xs">
                  <span className="font-label-md text-label-md text-on-surface font-semibold">Critical Task</span>
                  <span className="material-symbols-outlined text-[16px] text-error">priority_high</span>
                </div>
                <span className="font-body-sm text-body-sm text-on-surface-variant">
                  High priority requirement prioritized during volunteer solver allocation.
                </span>
              </div>
              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  name="critical"
                  checked={formData.critical}
                  onChange={handleFieldChange}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-container" />
              </label>
            </div>
          </div>

          {/* Footer */}
          <div className="px-space-xl py-space-lg bg-surface-container-lowest flex items-center justify-end gap-space-md border-t border-surface-container-low">
            <button
              className="h-10 px-space-lg rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-lg text-label-lg transition-colors cursor-pointer"
              type="button"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              className="h-10 px-space-xl rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer disabled:opacity-60"
              type="submit"
              disabled={submitting}
            >
              {submitting ? 'Saving...' : isEditing ? 'Update Task' : 'Confirm & Save Task'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
