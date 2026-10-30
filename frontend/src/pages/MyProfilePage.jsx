import { useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getVolunteer, getVolunteerHistory, updateVolunteer } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import VolunteerProfileView from '../components/volunteers/VolunteerProfileView'

export default function MyProfilePage() {
  const { role, user } = useAuth()

  // Use volunteer id linked to the logged-in user in AuthContext
  const volunteerId = user?.volunteerId

  // Data fetching callbacks
  const fetchVolunteerCallback = useCallback(() => {
    if (!volunteerId) return Promise.resolve(null)
    return getVolunteer(volunteerId)
  }, [volunteerId])

  const fetchHistoryCallback = useCallback(() => {
    if (!volunteerId) return Promise.resolve({ volunteerId, averageRating: null, assignments: [] })
    return getVolunteerHistory(volunteerId)
  }, [volunteerId])

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

  // Redirect non-volunteers away (volunteer only)
  if (role !== 'volunteer') {
    return <Navigate to="/dashboard" replace />
  }

  const handleRetryAll = () => {
    reloadVolunteer()
    reloadHistory()
  }

  const handleSaveProfile = async (updatedData) => {
    await updateVolunteer(volunteerId, updatedData)
    // Reload profile data to synchronize
    await reloadVolunteer()
  }

  const isLoading = loadingVolunteer || loadingHistory
  const hasError = errorVolunteer || errorHistory

  // Loading state
  if (isLoading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading your profile...</p>
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
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Failed to load profile
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {errorVolunteer || errorHistory || 'An unexpected error occurred while loading your profile data.'}
          </p>
          <button
            type="button"
            onClick={handleRetryAll}
            className="px-space-lg py-2 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors cursor-pointer"
          >
            Retry
          </button>
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
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Profile not found
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            Your volunteer record could not be located. Please contact an event coordinator.
          </p>
          <button
            type="button"
            onClick={handleRetryAll}
            className="px-space-lg py-2.5 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors shadow-sm cursor-pointer"
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return (
    <VolunteerProfileView
      volunteer={volunteer}
      history={history}
      showBackLink={false}
      isEditable={true}
      onSave={handleSaveProfile}
    />
  )
}
