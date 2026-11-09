import { useState, useMemo, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import {
  getRegistrationForm,
  saveRegistrationForm,
  openRegistration,
  closeRegistration,
  getTasks,
  getPlan,
} from '../services/api'
import { useFetch } from '../hooks/useFetch'
import SkillMultiSelect from '../components/common/SkillMultiSelect'
import { PREDEFINED_SKILLS, MAX_SKILL_LENGTH, MAX_OFFERED_SKILLS } from '../constants/skills'

export default function RegistrationFormPage() {
  const { role } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent } = useEvent()

  // Fetch form data
  const fetchFormCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve(null)
    return getRegistrationForm(selectedEventId)
  }, [selectedEventId])

  // Fetch tasks to extract available skills from the event
  const fetchTasksCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve([])
    return getTasks(selectedEventId)
  }, [selectedEventId])

  // Fetch plan to see if published
  const fetchPlanCallback = useCallback(() => {
    if (!selectedEventId) return Promise.resolve(null)
    return getPlan(selectedEventId)
  }, [selectedEventId])

  const { data: formData, loading: formLoading, error: formError, reload: reloadForm } = useFetch(fetchFormCallback)
  const { data: tasks } = useFetch(fetchTasksCallback)
  const { data: plan } = useFetch(fetchPlanCallback)

  const isPlanPublished = plan?.status === 'published'

  // Redirect volunteers
  if (role === 'volunteer') {
    return <Navigate to={getHomeRoute(role)} replace />
  }

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-space-lg">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-space-xs text-outline font-label-sm text-label-sm tracking-wide">
              <span>{selectedEvent ? selectedEvent.name : 'No event selected'}</span>
            </div>
            <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
              Registration Form
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
              Configure volunteer recruitment, select offered skills, and manage registration availability for this event.
            </p>
          </div>

          {selectedEventId && (
            <div className="flex items-center gap-space-md shrink-0">
              <div className="flex items-center gap-space-sm px-4 py-2 rounded-xl bg-surface-container-lowest shadow-xs border border-outline-variant/30">
                <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">Status:</span>
                <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-label-sm text-label-sm font-semibold capitalize ${getStatusBadge(formData?.status).cls}`}>
                  {getStatusBadge(formData?.status).label}
                </span>
                <span className="h-4 w-[1px] bg-outline-variant/40 mx-1" />
                <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">Responses:</span>
                <span className="font-label-md text-label-md font-bold text-on-surface">
                  {formData?.responseCount || 0}
                  {formData?.maxResponses ? ` / ${formData.maxResponses}` : ''}
                </span>
              </div>
            </div>
          )}
        </div>

        {/* If No Event is Selected */}
        {!selectedEventId && (
          <section className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">how_to_reg</span>
            </div>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
              Please select an event
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
              Registration forms are customized per collegiate event. Choose an event from the topbar or select one below.
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

        {/* Loading State */}
        {selectedEventId && formLoading && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center">
            <div className="w-12 h-12 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-space-md" />
            <p className="font-body-md text-body-md text-on-surface-variant">Loading registration form...</p>
          </div>
        )}

        {/* Error State */}
        {selectedEventId && !formLoading && formError && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-12 flex flex-col items-center justify-center text-center border border-error/20">
            <span className="material-symbols-outlined text-[40px] text-error mb-space-sm">
              error_outline
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
              Failed to load registration form
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg">
              {formError}
            </p>
            <button
              onClick={() => reloadForm()}
              className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              <span>Try again</span>
            </button>
          </div>
        )}

        {/* Main Form Content */}
        {selectedEventId && !formLoading && !formError && (
          <RegistrationFormEditor
            key={`${selectedEventId}_${formData?.status || 'draft'}`}
            selectedEventId={selectedEventId}
            formData={formData}
            tasks={tasks || []}
            isPlanPublished={isPlanPublished}
            reloadForm={reloadForm}
          />
        )}
      </div>
    </div>
  )
}

function getStatusBadge(status) {
  if (status === 'open') {
    return {
      label: 'Open',
      cls: 'bg-secondary/15 text-secondary border border-secondary/30',
    }
  }
  if (status === 'closed') {
    return {
      label: 'Closed',
      cls: 'bg-error/10 text-error border border-error/20',
    }
  }
  return {
    label: 'Draft',
    cls: 'bg-surface-container-high text-on-surface-variant border border-outline-variant/40',
  }
}

function toDateTimeLocal(isoStr) {
  if (!isoStr) return ''
  try {
    const d = new Date(isoStr)
    if (isNaN(d.getTime())) return ''
    return d.toISOString().slice(0, 16)
  } catch {
    return ''
  }
}

function RegistrationFormEditor({ selectedEventId, formData, tasks, isPlanPublished, reloadForm }) {
  // Local form state initialized directly from props.
  // offeredSkills: start from formData, then merge task skills (mandatory+preferred)
  // using case-insensitive deduplication; canonical spelling from PREDEFINED_SKILLS wins.
  const [formState, setFormState] = useState(() => {
    // Resolve a skill name to its canonical PREDEFINED_SKILLS spelling (if any)
    const toCanonical = (name) => {
      const lower = name.toLowerCase()
      return PREDEFINED_SKILLS.find((p) => p.toLowerCase() === lower) || name
    }

    // Gather existing offered skills (canonical)
    const existing = Array.isArray(formData?.offeredSkills)
      ? formData.offeredSkills.map(toCanonical)
      : []

    // Merge task skills using case-insensitive deduplication
    const seen = new Set(existing.map((s) => s.toLowerCase()))
    const merged = [...existing]
    ;(tasks || []).forEach((t) => {
      ;[...(t.mandatorySkills || []), ...(t.preferredSkills || [])].forEach((s) => {
        const canonical = toCanonical(s)
        const lower = canonical.toLowerCase()
        if (!seen.has(lower)) {
          seen.add(lower)
          merged.push(canonical)
        }
      })
    })

    return {
      opensAt: toDateTimeLocal(formData?.opensAt),
      closesAt: toDateTimeLocal(formData?.closesAt),
      maxResponses: formData?.maxResponses ? String(formData.maxResponses) : '',
      offeredSkills: merged,
      extraQuestions: Array.isArray(formData?.extraQuestions)
        ? formData.extraQuestions.map((q) => ({ ...q }))
        : [],
    }
  })

  const [validationErrors, setValidationErrors] = useState({})
  const [saveSuccessMessage, setSaveSuccessMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Derive skill options: PREDEFINED_SKILLS + any task skills that aren't
  // already represented (case-insensitive), keeping canonical PREDEFINED_SKILLS spelling
  const skillOptions = useMemo(() => {
    if (!tasks) return PREDEFINED_SKILLS
    const extra = []
    tasks.forEach((t) => {
      ;[...(t.mandatorySkills || []), ...(t.preferredSkills || [])].forEach((s) => {
        const lower = s.toLowerCase()
        const inPredefined = PREDEFINED_SKILLS.some((p) => p.toLowerCase() === lower)
        const inExtra = extra.some((e) => e.toLowerCase() === lower)
        if (!inPredefined && !inExtra) extra.push(s)
      })
    })
    return [...PREDEFINED_SKILLS, ...extra.sort()]
  }, [tasks])

  // Extra questions management (max 2)
  const handleAddQuestion = () => {
    if (formState.extraQuestions.length >= 2 || isPlanPublished) return
    setFormState((prev) => ({
      ...prev,
      extraQuestions: [
        ...prev.extraQuestions,
        { id: `q_${Date.now()}`, label: '' },
      ],
    }))
  }

  const handleUpdateQuestion = (index, label) => {
    if (isPlanPublished) return
    setFormState((prev) => {
      const copy = [...prev.extraQuestions]
      copy[index] = { ...copy[index], label: label.slice(0, 100) }
      return { ...prev, extraQuestions: copy }
    })
  }

  const handleRemoveQuestion = (index) => {
    if (isPlanPublished) return
    setFormState((prev) => ({
      ...prev,
      extraQuestions: prev.extraQuestions.filter((_, idx) => idx !== index),
    }))
  }

  // Validate form inputs
  const validateForm = () => {
    const errors = {}

    if (formState.opensAt && formState.closesAt) {
      const opens = new Date(formState.opensAt).getTime()
      const closes = new Date(formState.closesAt).getTime()
      if (closes <= opens) {
        errors.closesAt = 'Closes date/time must be after opens date/time.'
      }
    }

    if (formState.maxResponses !== '') {
      const max = Number(formState.maxResponses)
      if (isNaN(max) || max < 1 || !Number.isInteger(max)) {
        errors.maxResponses = 'Max responses must be an integer of 1 or greater.'
      }
    }

    for (let i = 0; i < formState.extraQuestions.length; i++) {
      const q = formState.extraQuestions[i]
      if (!q.label || !q.label.trim()) {
        errors[`question_${i}`] = 'Question prompt cannot be blank.'
      } else if (q.label.length > 100) {
        errors[`question_${i}`] = 'Question prompt cannot exceed 100 characters.'
      }
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  // Save Draft
  const handleSaveDraft = async () => {
    if (isPlanPublished) return
    if (!validateForm()) return

    setIsSubmitting(true)
    setSaveSuccessMessage('')
    try {
      const payload = {
        opensAt: formState.opensAt ? new Date(formState.opensAt).toISOString() : '',
        closesAt: formState.closesAt ? new Date(formState.closesAt).toISOString() : '',
        maxResponses: formState.maxResponses !== '' ? Number(formState.maxResponses) : null,
        offeredSkills: formState.offeredSkills,
        extraQuestions: formState.extraQuestions.map((q) => ({
          id: q.id,
          label: q.label.trim(),
        })),
      }
      await saveRegistrationForm(selectedEventId, payload)
      await reloadForm()
      setSaveSuccessMessage('Registration form draft saved successfully.')
      setTimeout(() => setSaveSuccessMessage(''), 3000)
    } catch (err) {
      setValidationErrors({ general: err.message || 'Failed to save form.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  // Open Registration
  const handleOpenRegistration = async () => {
    if (isPlanPublished) return
    if (!validateForm()) return

    setIsSubmitting(true)
    setSaveSuccessMessage('')
    try {
      // First save current updates
      const payload = {
        opensAt: formState.opensAt ? new Date(formState.opensAt).toISOString() : '',
        closesAt: formState.closesAt ? new Date(formState.closesAt).toISOString() : '',
        maxResponses: formState.maxResponses !== '' ? Number(formState.maxResponses) : null,
        offeredSkills: formState.offeredSkills,
        extraQuestions: formState.extraQuestions.map((q) => ({
          id: q.id,
          label: q.label.trim(),
        })),
      }
      await saveRegistrationForm(selectedEventId, payload)
      await openRegistration(selectedEventId)
      await reloadForm()
      setSaveSuccessMessage('Registration is now OPEN for volunteers.')
      setTimeout(() => setSaveSuccessMessage(''), 3000)
    } catch (err) {
      setValidationErrors({ general: err.message || 'Failed to open registration.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  // Close Registration
  const handleCloseRegistration = async () => {
    setIsSubmitting(true)
    setSaveSuccessMessage('')
    try {
      await closeRegistration(selectedEventId)
      await reloadForm()
      setSaveSuccessMessage('Registration is now CLOSED.')
      setTimeout(() => setSaveSuccessMessage(''), 3000)
    } catch (err) {
      setValidationErrors({ general: err.message || 'Failed to close registration.' })
    } finally {
      setIsSubmitting(false)
    }
  }

  // Status helper badge
  const status = formData?.status || 'draft'
  const isFormOpen = status === 'open'
  const canOpen = (status === 'draft' || status === 'closed') && !isPlanPublished
  const canClose = status === 'open' && !isPlanPublished

  return (
    <div className="flex flex-col gap-space-xl">
      {/* Notice if Assignment Plan is Published */}
      {isPlanPublished && (
        <div className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-3">
          <span className="material-symbols-outlined text-amber-600 text-[22px] shrink-0 mt-0.5">
            lock
          </span>
          <div className="flex flex-col">
            <span className="font-label-lg text-label-lg text-amber-900 font-semibold">
              Registration Locked
            </span>
            <p className="font-body-md text-body-md text-amber-800">
              The assignment plan for this event has been published. All volunteer registration fields are locked to preserve roster integrity and constraint allocations.
            </p>
          </div>
        </div>
      )}

      {/* Success Message Banner */}
      {saveSuccessMessage && (
        <div className="p-4 rounded-xl bg-secondary-container/40 border border-secondary/30 flex items-center gap-3">
          <span className="material-symbols-outlined text-secondary text-[22px]">check_circle</span>
          <span className="font-body-md text-body-md text-on-surface">{saveSuccessMessage}</span>
        </div>
      )}

      {/* General Error Banner */}
      {validationErrors.general && (
        <div className="p-4 rounded-xl bg-error-container/30 border border-error/30 flex items-center gap-3">
          <span className="material-symbols-outlined text-error text-[22px]">error</span>
          <span className="font-body-md text-body-md text-error">{validationErrors.general}</span>
        </div>
      )}

      {/* Form Card */}
      <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-space-xl flex flex-col gap-space-xl">
        {/* Section 1: Offered Skills */}
        <div className="flex flex-col gap-space-md">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <span className="material-symbols-outlined text-primary text-[22px]">psychology</span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                Offered Skills
              </h2>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-1">
              Select the skills volunteers can choose when registering. Prefilled from this event&apos;s tasks.
            </p>
          </div>

          <SkillMultiSelect
            value={formState.offeredSkills}
            onChange={(skills) =>
              setFormState((prev) => ({ ...prev, offeredSkills: skills }))
            }
            options={skillOptions}
            disabled={isPlanPublished}
            maxItems={MAX_OFFERED_SKILLS}
            maxLength={MAX_SKILL_LENGTH}
          />

          {formState.offeredSkills.length === 0 && (
            <span className="font-body-sm text-body-sm text-outline italic">
              No skills offered yet.
            </span>
          )}
        </div>

        {/* Section 2: Registration Schedule & Capacity */}
        <div className="flex flex-col gap-space-md">
          <div className="flex items-center gap-space-sm">
            <span className="material-symbols-outlined text-primary text-[22px]">calendar_month</span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
              Schedule &amp; Capacity
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-space-md">
            {/* Opens At */}
            <div className="flex flex-col gap-1.5">
              <label className="font-label-md text-label-md text-on-surface font-medium">
                Opens At
              </label>
              <input
                type="datetime-local"
                disabled={isPlanPublished}
                value={formState.opensAt}
                onChange={(e) =>
                  setFormState((prev) => ({ ...prev, opensAt: e.target.value }))
                }
                className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md border border-outline-variant/30 focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60 disabled:cursor-not-allowed"
              />
              <span className="font-body-sm text-body-sm text-outline">
                When volunteers can begin applying
              </span>
            </div>

            {/* Closes At */}
            <div className="flex flex-col gap-1.5">
              <label className="font-label-md text-label-md text-on-surface font-medium">
                Closes At
              </label>
              <input
                type="datetime-local"
                disabled={isPlanPublished}
                value={formState.closesAt}
                onChange={(e) =>
                  setFormState((prev) => ({ ...prev, closesAt: e.target.value }))
                }
                className={`h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md border focus:outline-none focus:ring-2 disabled:opacity-60 disabled:cursor-not-allowed ${
                  validationErrors.closesAt
                    ? 'border-error focus:ring-error/20'
                    : 'border-outline-variant/30 focus:ring-primary/20'
                }`}
              />
              {validationErrors.closesAt ? (
                <span className="font-body-sm text-body-sm text-error">
                  {validationErrors.closesAt}
                </span>
              ) : (
                <span className="font-body-sm text-body-sm text-outline">
                  Must be after opens date
                </span>
              )}
            </div>

            {/* Max Responses */}
            <div className="flex flex-col gap-1.5">
              <label className="font-label-md text-label-md text-on-surface font-medium">
                Max Responses (Optional)
              </label>
              <input
                type="number"
                min="1"
                step="1"
                disabled={isPlanPublished}
                value={formState.maxResponses}
                onChange={(e) =>
                  setFormState((prev) => ({ ...prev, maxResponses: e.target.value }))
                }
                placeholder="e.g. 50"
                className={`h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md border focus:outline-none focus:ring-2 disabled:opacity-60 disabled:cursor-not-allowed ${
                  validationErrors.maxResponses
                    ? 'border-error focus:ring-error/20'
                    : 'border-outline-variant/30 focus:ring-primary/20'
                }`}
              />
              {validationErrors.maxResponses ? (
                <span className="font-body-sm text-body-sm text-error">
                  {validationErrors.maxResponses}
                </span>
              ) : (
                <span className="font-body-sm text-body-sm text-outline">
                  Integer &gt;= 1 or empty for no cap
                </span>
              )}
            </div>
          </div>

          {/* Section 3: Extra Questions (Up to 2) */}
          <div className="flex flex-col gap-space-md mt-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="flex items-center gap-space-sm">
                <span className="material-symbols-outlined text-primary text-[22px]">quiz</span>
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Extra Questions (Up to 2 optional text prompts)
                </h2>
              </div>
              <span className="font-body-sm text-body-sm text-outline">
                {formState.extraQuestions.length} / 2 configured (max 100 characters each)
              </span>
            </div>

            <div className="flex flex-col gap-3">
              {formState.extraQuestions.map((q, idx) => (
                <div key={q.id || idx} className="flex flex-col gap-1 p-4 rounded-xl bg-surface-container-low/60 border border-outline-variant/30">
                  <div className="flex items-center justify-between gap-2">
                    <label className="font-label-sm text-label-sm text-on-surface font-semibold uppercase">
                      Question {idx + 1}
                    </label>
                    {!isPlanPublished && (
                      <button
                        type="button"
                        onClick={() => handleRemoveQuestion(idx)}
                        className="text-error hover:opacity-80 font-label-sm text-label-sm flex items-center gap-0.5 cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[16px]">delete</span>
                        <span>Remove</span>
                      </button>
                    )}
                  </div>
                  <input
                    type="text"
                    maxLength={100}
                    disabled={isPlanPublished}
                    value={q.label}
                    onChange={(e) => handleUpdateQuestion(idx, e.target.value)}
                    placeholder="e.g. T-Shirt Size, dietary preferences, or specific equipment familiarity..."
                    className="h-10 px-space-md rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md border border-outline-variant/30 focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60 disabled:cursor-not-allowed"
                  />
                  <div className="flex items-center justify-between">
                    {validationErrors[`question_${idx}`] && (
                      <span className="font-body-sm text-[12px] text-error">
                        {validationErrors[`question_${idx}`]}
                      </span>
                    )}
                    <span className="font-body-sm text-[11px] text-outline ml-auto">
                      {q.label.length} / 100 chars
                    </span>
                  </div>
                </div>
              ))}

              {formState.extraQuestions.length === 0 && (
                <div className="p-4 rounded-xl bg-surface-container-low/40 border border-dashed border-outline-variant/40 text-center">
                  <span className="font-body-md text-body-md text-outline">
                    No extra questions configured. Volunteers will only answer standard profile and skill questions.
                  </span>
                </div>
              )}

              {!isPlanPublished && formState.extraQuestions.length < 2 && (
                <button
                  type="button"
                  onClick={handleAddQuestion}
                  className="self-start flex items-center gap-1.5 px-4 h-9 rounded-lg border border-dashed border-primary text-primary hover:bg-primary/5 font-label-md text-label-md transition-colors cursor-pointer mt-1"
                >
                  <span className="material-symbols-outlined text-[18px]">add</span>
                  <span>Add extra question ({formState.extraQuestions.length}/2)</span>
                </button>
              )}
            </div>
          </div>

          {/* Action Buttons Strip */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-space-md pt-space-lg border-t border-outline-variant/15 mt-4">
            {/* Left/Status Helper Text */}
            <div className="flex flex-col gap-0.5">
              <span className="font-label-md text-label-md text-on-surface font-medium">
                Form Control
              </span>
              <span className="font-body-sm text-body-sm text-outline">
                {isPlanPublished
                  ? 'Registration is locked because the assignment plan is published.'
                  : isFormOpen
                  ? 'Form is active and open for volunteer submissions.'
                  : 'Form is closed. Save draft or open when ready.'}
              </span>
            </div>

            {/* Buttons */}
            <div className="flex flex-wrap items-center gap-space-sm">
              {/* Save Draft Button */}
              <button
                type="button"
                onClick={handleSaveDraft}
                disabled={isPlanPublished || isSubmitting}
                title={isPlanPublished ? 'Registration is locked' : undefined}
                className="flex items-center gap-1.5 px-space-lg h-10 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span className="material-symbols-outlined text-[18px]">save</span>
                <span>Save draft</span>
              </button>

              {/* Open Registration Button */}
              <button
                type="button"
                onClick={handleOpenRegistration}
                disabled={!canOpen || isSubmitting}
                title={
                  isPlanPublished
                    ? 'Plan is published; registration locked.'
                    : isFormOpen
                    ? 'Registration is already open.'
                    : undefined
                }
                className="flex items-center gap-1.5 px-space-lg h-10 rounded-lg bg-primary-container text-on-primary hover:bg-primary font-label-md text-label-md shadow-sm transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span className="material-symbols-outlined text-[18px]">lock_open</span>
                <span>Open registration</span>
              </button>

              {/* Close Registration Button */}
              <button
                type="button"
                onClick={handleCloseRegistration}
                disabled={!canClose || isSubmitting}
                title={!isFormOpen ? 'Registration is not currently open.' : undefined}
                className="flex items-center gap-1.5 px-space-lg h-10 rounded-lg bg-error-container text-error hover:bg-error hover:text-on-error font-label-md text-label-md shadow-sm transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span className="material-symbols-outlined text-[18px]">lock</span>
                <span>Close registration</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}