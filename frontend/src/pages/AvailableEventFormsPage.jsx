import { useState, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import {
  getAvailableEvents,
  getRegistrationView,
  getVolunteer,
  submitRegistration,
  updateRegistration,
  withdrawRegistration,
} from '../services/api'
import { useFetch } from '../hooks/useFetch'
import { getVolunteerEventFormStatus, isRegistrationOpen } from '../utils/registration'
import VolunteerRegistrationModal from '../components/events/VolunteerRegistrationModal'

export default function AvailableEventFormsPage() {
  const { role, user } = useAuth()

  // Redirect organisers away (volunteers only)
  if (role !== 'volunteer') {
    return <Navigate to={getHomeRoute(role)} replace />
  }

  return <AvailableEventFormsContent user={user} />
}

function AvailableEventFormsContent({ user }) {
  const fetchAvailableEventsCallback = useCallback(() => {
    return getAvailableEvents()
  }, [])

  const {
    data: availableEvents,
    loading,
    error,
    reload,
  } = useFetch(fetchAvailableEventsCallback)

  // Fetch volunteer profile for prefill
  const volunteerId = user?.volunteerId
  const fetchProfileCallback = useCallback(() => {
    if (!volunteerId) return Promise.resolve(null)
    return getVolunteer(volunteerId)
  }, [volunteerId])

  const { data: userProfile } = useFetch(fetchProfileCallback)

  // Modal State
  const [selectedEventRegistration, setSelectedEventRegistration] = useState(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [modalLoading, setModalLoading] = useState(false)

  // Open Registration Modal
  const handleOpenRegistration = async (eventId) => {
    setModalLoading(true)
    try {
      const regView = await getRegistrationView(eventId)
      setSelectedEventRegistration(regView)
      setIsModalOpen(true)
    } catch (err) {
      console.error('Failed to open registration view:', err)
    } finally {
      setModalLoading(false)
    }
  }

  // Submit Registration
  const handleSubmitRegistration = async (payload) => {
    if (!selectedEventRegistration?.event?.id) return
    await submitRegistration(selectedEventRegistration.event.id, payload)
    await reload()
  }

  // Update Registration
  const handleUpdateRegistration = async (payload) => {
    if (!selectedEventRegistration?.event?.id) return
    await updateRegistration(selectedEventRegistration.event.id, payload)
    await reload()
  }

  // Withdraw Registration
  const handleWithdrawRegistration = async () => {
    if (!selectedEventRegistration?.event?.id) return
    await withdrawRegistration(selectedEventRegistration.event.id)
    await reload()
  }

  const getStatusBadgeStyle = (status) => {
    switch (status) {
      case 'Registered':
        return 'bg-secondary-fixed text-on-secondary-fixed-variant border border-secondary/30'
      case 'Open':
        return 'bg-primary-fixed text-on-primary-fixed-variant border border-primary/20'
      case 'Full':
        return 'bg-amber-500/15 text-amber-900 border border-amber-500/30'
      case 'Closed':
      default:
        return 'bg-surface-container-high text-outline border border-outline-variant/30'
    }
  }

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Header */}
        <div className="flex flex-col gap-space-xs max-w-2xl">
          <span className="text-outline font-label-sm text-label-sm uppercase tracking-wider">
            Volunteer Opportunities
          </span>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
            Available Event Forms
          </h1>
          <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
            Discover upcoming campus symposiums, festivals, and tournaments looking for volunteer staff. Register or edit your availability details.
          </p>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center">
            <div className="w-12 h-12 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-space-md" />
            <p className="font-body-md text-body-md text-on-surface-variant">Loading available events...</p>
          </div>
        )}

        {/* Error State */}
        {!loading && error && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-12 flex flex-col items-center justify-center text-center border border-error/20">
            <span className="material-symbols-outlined text-[40px] text-error mb-space-sm">
              error_outline
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
              Failed to load available events
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg">
              {error}
            </p>
            <button
              onClick={() => reload()}
              className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              <span>Try again</span>
            </button>
          </div>
        )}

        {/* Empty State */}
        {!loading && !error && (!availableEvents || availableEvents.length === 0) && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-outline mb-space-md">
              <span className="material-symbols-outlined text-[36px]">event_busy</span>
            </div>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
              No forms available right now
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md">
              No event registration forms have been opened yet. Check back soon for upcoming campus gatherings!
            </p>
          </div>
        )}

        {/* Events Grid */}
        {!loading && !error && availableEvents && availableEvents.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-space-lg">
            {availableEvents.map(({ event, form, isRegistered }) => {
              const statusKey = getVolunteerEventFormStatus(form, isRegistered)
              const isOpen = isRegistrationOpen(form)

              let buttonLabel = 'Register'
              if (isRegistered) {
                buttonLabel = isOpen ? 'View or edit' : 'View only'
              } else if (!isOpen) {
                buttonLabel = 'View details'
              }

              return (
                <div
                  key={event.id}
                  className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/20 p-6 flex flex-col justify-between transition-all hover:shadow-md hover:border-outline-variant/40"
                >
                  <div className="flex flex-col gap-3">
                    {/* Top Row: Category & Status Chip */}
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider capitalize">
                        {event.category || 'Collegiate Event'}
                      </span>
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full font-label-sm text-[12px] font-semibold ${getStatusBadgeStyle(
                          statusKey
                        )}`}
                      >
                        {statusKey}
                      </span>
                    </div>

                    {/* Event Name */}
                    <h2 className="font-headline-md text-headline-md text-on-surface font-semibold tracking-tight">
                      {event.name}
                    </h2>

                    {/* Dates */}
                    <div className="flex items-center gap-1.5 text-on-surface-variant font-body-sm text-body-sm">
                      <span className="material-symbols-outlined text-[16px] text-outline">
                        calendar_month
                      </span>
                      <span>
                        {event.startDate}
                        {event.endDate && event.endDate !== event.startDate
                          ? ` - ${event.endDate}`
                          : ''}
                      </span>
                    </div>

                    {/* Description */}
                    {event.description && (
                      <p className="font-body-sm text-body-sm text-on-surface-variant line-clamp-2 mt-1">
                        {event.description}
                      </p>
                    )}

                    {/* Offered Skills preview */}
                    {form.offeredSkills && form.offeredSkills.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 pt-2">
                        {form.offeredSkills.slice(0, 3).map((sk) => (
                          <span
                            key={sk}
                            className="px-2 py-0.5 rounded-full bg-surface-container-low text-on-surface-variant font-label-sm text-[11px]"
                          >
                            {sk}
                          </span>
                        ))}
                        {form.offeredSkills.length > 3 && (
                          <span className="px-1.5 py-0.5 rounded-full bg-surface-container text-outline font-label-sm text-[11px]">
                            +{form.offeredSkills.length - 3}
                          </span>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Card Bottom CTA */}
                  <div className="pt-6 mt-4 border-t border-outline-variant/15 flex items-center justify-between">
                    <span className="font-body-sm text-[12px] text-outline">
                      {form.maxResponses
                        ? `${form.responseCount || 0} / ${form.maxResponses} slots`
                        : `${form.responseCount || 0} registered`}
                    </span>

                    <button
                      type="button"
                      disabled={modalLoading}
                      onClick={() => handleOpenRegistration(event.id)}
                      className={`inline-flex items-center gap-1.5 px-4 h-9 rounded-lg font-label-md text-label-md font-semibold transition-all cursor-pointer ${
                        isRegistered
                          ? 'bg-surface-container-low hover:bg-surface-container text-on-surface'
                          : isOpen
                          ? 'bg-primary-container hover:bg-primary text-on-primary shadow-xs'
                          : 'bg-surface-container-low text-outline'
                      }`}
                    >
                      <span>{buttonLabel}</span>
                      <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Registration Modal */}
      {selectedEventRegistration && (
        <VolunteerRegistrationModal
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false)
            setSelectedEventRegistration(null)
          }}
          event={selectedEventRegistration.event}
          form={selectedEventRegistration.form}
          existingRegistration={selectedEventRegistration.existingRegistration}
          userProfile={userProfile}
          currentUser={user}
          onSubmit={handleSubmitRegistration}
          onUpdate={handleUpdateRegistration}
          onWithdraw={handleWithdrawRegistration}
        />
      )}
    </div>
  )
}

