import { useState } from 'react'

export default function CreateEventModal({ isOpen, onClose, onCreate }) {
  const [formData, setFormData] = useState({
    name: '',
    category: '',
    startDate: '',
    endDate: '',
    description: '',
    status: 'draft',
  })

  const [dateError, setDateError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (!isOpen) return null

  const handleChange = (e) => {
    const { name, value } = e.target
    const updated = { ...formData, [name]: value }

    // Date validation: end date must not be before start date
    if (name === 'startDate' || name === 'endDate') {
      const start = name === 'startDate' ? value : updated.startDate
      const end = name === 'endDate' ? value : updated.endDate

      if (start && end && end < start) {
        setDateError('End date cannot be earlier than start date')
      } else {
        setDateError('')
      }
    }

    setFormData(updated)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    if (!formData.name.trim() || !formData.category || !formData.startDate || !formData.endDate) {
      return
    }

    if (formData.endDate < formData.startDate) {
      setDateError('End date cannot be earlier than start date')
      return
    }

    setSubmitting(true)
    try {
      await onCreate({
        name: formData.name.trim(),
        category: formData.category,
        startDate: formData.startDate,
        endDate: formData.endDate,
        description: formData.description.trim(),
        status: formData.status,
      })
      onClose()
      setFormData({
        name: '',
        category: '',
        startDate: '',
        endDate: '',
        description: '',
        status: 'draft',
      })
      setDateError('')
    } catch (err) {
      console.error('Error creating event:', err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-space-md sm:p-space-lg bg-inverse-surface/45 backdrop-blur-sm transition-opacity"
      id="create-event-modal"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="bg-surface-container-lowest w-full max-w-xl rounded-xl shadow-xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="px-space-xl py-space-lg bg-surface-container-lowest flex items-start justify-between">
          <div className="flex flex-col gap-space-xs">
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight">
              Create new event
            </h2>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Set event details and dates to initialize task dispatching.
            </p>
          </div>
          <button
            aria-label="Close dialog"
            className="text-outline hover:text-on-surface p-space-xs rounded-lg hover:bg-surface-container-low transition-colors cursor-pointer"
            id="close-modal-btn"
            type="button"
            onClick={onClose}
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Modal Body / Form */}
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 overflow-hidden">
          <div className="px-space-xl py-space-md overflow-y-auto flex flex-col gap-space-lg">
            {/* Field: Event Name */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface" htmlFor="event-name">
                Event Name <span className="text-error">*</span>
              </label>
              <input
                required
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                id="event-name"
                placeholder="e.g. Annual Tech Symposium 2025"
                type="text"
              />
            </div>

            {/* Field: Category */}
            <div className="flex flex-col gap-space-xs">
              <label
                className="font-label-md text-label-md text-on-surface"
                htmlFor="event-category"
              >
                Event Category / Type <span className="text-error">*</span>
              </label>
              <div className="relative">
                <select
                  required
                  name="category"
                  value={formData.category}
                  onChange={handleChange}
                  className="w-full h-10 pl-space-md pr-10 rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none appearance-none transition-colors"
                  id="event-category"
                >
                  <option disabled value="">
                    Select category (e.g. Cultural, Technical, Sports)
                  </option>
                  <option value="technical">Technical</option>
                  <option value="cultural">Cultural</option>
                  <option value="sports">Sports</option>
                  <option value="academic">Academic &amp; Research</option>
                  <option value="orientation">Orientation &amp; Welcoming</option>
                </select>
                <span className="material-symbols-outlined text-[20px] text-outline absolute right-space-md top-1/2 -translate-y-1/2 pointer-events-none">
                  expand_more
                </span>
              </div>
            </div>

            {/* Row: Start & End Date */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="start-date">
                  Start Date <span className="text-error">*</span>
                </label>
                <div className="relative">
                  <input
                    required
                    name="startDate"
                    value={formData.startDate}
                    onChange={handleChange}
                    className="w-full h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                    id="start-date"
                    type="date"
                  />
                </div>
              </div>
              <div className="flex flex-col gap-space-xs">
                <label className="font-label-md text-label-md text-on-surface" htmlFor="end-date">
                  End Date <span className="text-error">*</span>
                </label>
                <div className="relative">
                  <input
                    required
                    name="endDate"
                    min={formData.startDate || undefined}
                    value={formData.endDate}
                    onChange={handleChange}
                    className="w-full h-10 px-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
                    id="end-date"
                    type="date"
                  />
                </div>
              </div>
            </div>

            {dateError && (
              <p className="font-body-sm text-body-sm text-error -mt-space-sm">{dateError}</p>
            )}

            {/* Field: Description / Notes */}
            <div className="flex flex-col gap-space-xs">
              <label className="font-label-md text-label-md text-on-surface" htmlFor="event-desc">
                Description / Notes
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleChange}
                className="p-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none resize-none transition-colors"
                id="event-desc"
                placeholder="Briefly describe the event scope and target departments..."
                rows="3"
              />
            </div>

            {/* Field: Initial Status (Radio/Chips) */}
            <div className="flex flex-col gap-space-xs">
              <span className="font-label-md text-label-md text-on-surface">Initial Status</span>
              <div className="grid grid-cols-2 gap-space-md pt-space-xs">
                <label className="flex items-center gap-space-sm p-space-md rounded-xl bg-surface-container-low hover:bg-surface-container cursor-pointer transition-colors">
                  <input
                    className="accent-primary w-4 h-4 cursor-pointer"
                    name="status"
                    type="radio"
                    value="draft"
                    checked={formData.status === 'draft'}
                    onChange={handleChange}
                  />
                  <div className="flex flex-col">
                    <span className="font-label-lg text-label-lg text-on-surface">Draft</span>
                    <span className="font-body-sm text-body-sm text-outline">
                      Hidden from volunteers
                    </span>
                  </div>
                </label>
                <label className="flex items-center gap-space-sm p-space-md rounded-xl bg-surface-container-low hover:bg-surface-container cursor-pointer transition-colors">
                  <input
                    className="accent-primary w-4 h-4 cursor-pointer"
                    name="status"
                    type="radio"
                    value="planning"
                    checked={formData.status === 'planning'}
                    onChange={handleChange}
                  />
                  <div className="flex flex-col">
                    <span className="font-label-lg text-label-lg text-on-surface">Planning</span>
                    <span className="font-body-sm text-body-sm text-outline">
                      Ready for slot assignment
                    </span>
                  </div>
                </label>
              </div>
            </div>
          </div>

          {/* Modal Footer */}
          <div className="px-space-xl py-space-lg bg-surface-container-lowest flex items-center justify-end gap-space-md border-t border-surface-container-low">
            <button
              className="h-10 px-space-lg rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-lg text-label-lg transition-colors cursor-pointer"
              id="cancel-modal-btn"
              type="button"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              className="h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer disabled:opacity-50"
              type="submit"
              disabled={submitting || Boolean(dateError)}
            >
              {submitting ? 'Creating...' : 'Create event'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
