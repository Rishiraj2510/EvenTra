import { useCallback } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import { getVolunteer, getVolunteerHistory } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import VolunteerProfileView from '../components/volunteers/VolunteerProfileView'

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
    return <Navigate to={getHomeRoute(role)} replace />
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
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
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
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
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

  return (
    <VolunteerProfileView
      volunteer={volunteer}
      history={history}
      showBackLink={true}
      isEditable={false}
    />
  )
}
