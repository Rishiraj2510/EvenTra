import { useState, useCallback, useMemo } from 'react'
import { Navigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getFeedback, saveFeedback } from '../services/api'
import { useFetch } from '../hooks/useFetch'

export default function FeedbackPage() {
  const { role } = useAuth()
  const { selectedEventId, selectedEvent } = useEvent()

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('all') // 'all' | 'unrated' | 'rated'

  // Row edit state: map of assignmentId -> { rating, comment, isEditing, isSaving, savedNotification, error }
  const [editStates, setEditStates] = useState({})

  // Fetch feedback callback
  const fetchFeedbackCallback = useCallback(() => {
    return getFeedback(selectedEventId)
  }, [selectedEventId])

  const { data: feedbackRows, loading, error, reload } = useFetch(fetchFeedbackCallback)

  // Filter rows
  const filteredRows = useMemo(() => {
    if (!feedbackRows) return []
    return feedbackRows.filter((row) => {
      // Status filter
      const isRated = row.rating !== null && row.rating !== undefined
      if (statusFilter === 'unrated' && isRated) return false
      if (statusFilter === 'rated' && !isRated) return false

      // Search filter (volunteer name or task title)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase()
        const matchName = row.volunteerName?.toLowerCase().includes(q)
        const matchTask = row.taskTitle?.toLowerCase().includes(q)
        if (!matchName && !matchTask) return false
      }

      return true
    })
  }, [feedbackRows, statusFilter, searchQuery])

  // Aggregate metrics
  const totalCompletedShifts = feedbackRows ? feedbackRows.length : 0
  const awaitingRatingCount = feedbackRows
    ? feedbackRows.filter((r) => r.rating === null || r.rating === undefined).length
    : 0
  const ratedRows = feedbackRows
    ? feedbackRows.filter((r) => typeof r.rating === 'number' && !isNaN(r.rating))
    : []
  const averageRating =
    ratedRows.length > 0
      ? Math.round((ratedRows.reduce((acc, r) => acc + r.rating, 0) / ratedRows.length) * 10) / 10
      : null

  // Ensure role guard is evaluated after hooks
  if (role === 'volunteer') {
    return <Navigate to="/my-duties" replace />
  }

  // Row edit handlers
  const handleStartEdit = (row) => {
    setEditStates((prev) => ({
      ...prev,
      [row.assignmentId]: {
        rating: row.rating || null,
        comment: row.comment || '',
        isEditing: true,
        isSaving: false,
        savedNotification: false,
        error: null,
      },
    }))
  }

  const handleCancelEdit = (assignmentId) => {
    setEditStates((prev) => {
      const copy = { ...prev }
      delete copy[assignmentId]
      return copy
    })
  }

  const handleSetRating = (assignmentId, ratingVal) => {
    setEditStates((prev) => ({
      ...prev,
      [assignmentId]: {
        ...(prev[assignmentId] || { comment: '', isEditing: true }),
        rating: ratingVal,
        error: null,
      },
    }))
  }

  const handleCommentChange = (assignmentId, text) => {
    if (text.length > 300) return
    setEditStates((prev) => ({
      ...prev,
      [assignmentId]: {
        ...(prev[assignmentId] || { rating: null, isEditing: true }),
        comment: text,
        error: null,
      },
    }))
  }

  const handleSave = async (assignmentId) => {
    const currentState = editStates[assignmentId]
    if (!currentState || !currentState.rating) {
      setEditStates((prev) => ({
        ...prev,
        [assignmentId]: {
          ...(prev[assignmentId] || {}),
          error: 'Please select a rating between 1 and 5 stars.',
        },
      }))
      return
    }

    try {
      setEditStates((prev) => ({
        ...prev,
        [assignmentId]: {
          ...prev[assignmentId],
          isSaving: true,
          error: null,
        },
      }))

      await saveFeedback(assignmentId, {
        rating: currentState.rating,
        comment: currentState.comment,
      })

      // Show temporary "Saved" notification
      setEditStates((prev) => ({
        ...prev,
        [assignmentId]: {
          ...prev[assignmentId],
          isSaving: false,
          isEditing: false,
          savedNotification: true,
        },
      }))

      // Reload feedback table data to reflect updated rating/comment
      await reload()

      setTimeout(() => {
        setEditStates((prev) => {
          if (!prev[assignmentId]) return prev
          return {
            ...prev,
            [assignmentId]: {
              ...prev[assignmentId],
              savedNotification: false,
            },
          }
        })
      }, 2500)
    } catch (err) {
      setEditStates((prev) => ({
        ...prev,
        [assignmentId]: {
          ...prev[assignmentId],
          isSaving: false,
          error: err.message || 'Failed to save feedback.',
        },
      }))
    }
  }

  // 1. Empty state: No event selected in topbar picker
  if (!selectedEventId) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-outline mb-space-md">
            <span className="material-symbols-outlined text-[32px]">event_busy</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Choose an event to view feedback
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
            Please select an event from the topbar dropdown to view volunteer shift appraisals and assign ratings.
          </p>
        </div>
      </div>
    )
  }

  // 2. Loading state
  if (loading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading feedback & appraisals...</p>
        </div>
      </div>
    )
  }

  // 3. Error state
  if (error) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-error-container text-error flex items-center justify-center mb-space-md shadow-sm">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Unable to load feedback
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {error || 'An unexpected error occurred while fetching shifts for this event.'}
          </p>
          <button
            onClick={() => reload()}
            type="button"
            className="px-space-lg py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg shadow-sm hover:opacity-90 active:scale-95 transition-all"
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col w-full">
      {/* Top Header Banner & Context */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-lg mb-space-xl">
        <div className="flex flex-col gap-space-xs">
          <div className="flex items-center gap-space-sm text-outline">
            <span className="material-symbols-outlined text-[16px]">domain_verification</span>
            <span className="font-label-sm text-label-sm uppercase tracking-wider">
              Event Scope: {selectedEvent?.name || 'Selected Event'}
            </span>
            <span className="w-1.5 h-1.5 rounded-full bg-outline-variant" />
            <span className="font-label-sm text-label-sm text-outline">Post-Event Appraisal Engine</span>
          </div>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">Feedback</h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
            Review completed volunteer shifts, assign star ratings, and record post-event feedback.
          </p>
        </div>
      </div>

      {/* Aggregate Metric Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-gutter-lg mb-space-xl">
        {/* Card 1: Completed Shifts */}
        <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm border border-outline-variant/30 flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="font-label-md text-label-md text-on-surface-variant font-medium">
              Total completed shifts
            </span>
            <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[20px]">assignment_turned_in</span>
            </div>
          </div>
          <div className="mt-space-md">
            <span className="font-headline-xl text-headline-xl text-on-surface font-semibold tracking-tight">
              {totalCompletedShifts}
            </span>
          </div>
          <div className="mt-space-xs flex items-center gap-space-xs text-outline">
            <span className="material-symbols-outlined text-[14px]">event_available</span>
            <span className="font-body-sm text-body-sm">Published shifts in schedule</span>
          </div>
        </div>

        {/* Card 2: Awaiting Rating */}
        <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm border border-outline-variant/30 flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="font-label-md text-label-md text-on-surface-variant font-medium">
              Awaiting rating
            </span>
            <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[20px]">pending_actions</span>
            </div>
          </div>
          <div className="mt-space-md">
            <span className="font-headline-xl text-headline-xl text-on-surface font-semibold tracking-tight">
              {awaitingRatingCount}
            </span>
          </div>
          <div className="mt-space-xs flex items-center gap-space-xs text-outline">
            <span className="material-symbols-outlined text-[14px]">rate_review</span>
            <span className="font-body-sm text-body-sm">Pending appraisal submissions</span>
          </div>
        </div>

        {/* Card 3: Average Rating */}
        <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm border border-outline-variant/30 flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="font-label-md text-label-md text-on-surface-variant font-medium">
              Average rating
            </span>
            <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[20px]">star_half</span>
            </div>
          </div>
          <div className="mt-space-md flex items-baseline gap-space-xs">
            <span className="font-headline-xl text-headline-xl text-on-surface font-semibold tracking-tight">
              {averageRating !== null ? averageRating.toFixed(1) : '--'}
            </span>
            {averageRating !== null && (
              <span className="font-headline-sm text-headline-sm text-outline">/ 5.0</span>
            )}
          </div>
          <div className="mt-space-xs flex items-center gap-space-xs text-outline">
            <span className="material-symbols-outlined text-[14px]">reviews</span>
            <span className="font-body-sm text-body-sm">
              {ratedRows.length > 0 ? `${ratedRows.length} rated shifts` : 'No ratings recorded yet'}
            </span>
          </div>
        </div>
      </div>

      {/* Operational Filter Toolbar */}
      <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/30 mb-space-lg flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-space-md flex-1 max-w-3xl">
          {/* Search Input */}
          <div className="relative flex-1">
            <span className="material-symbols-outlined absolute left-space-md top-1/2 -translate-y-1/2 text-outline text-[18px]">
              search
            </span>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by volunteer name or task..."
              className="w-full h-10 pl-10 pr-space-md bg-surface-container-low text-on-surface placeholder:text-outline rounded-lg font-body-md text-body-md focus:outline-none focus:ring-1 focus:ring-primary transition-all"
            />
          </div>

          {/* Segmented Category Filters */}
          <div className="flex items-center gap-space-xs p-1 bg-surface-container-low rounded-lg overflow-x-auto">
            <button
              onClick={() => setStatusFilter('all')}
              type="button"
              className={`px-space-md py-space-xs rounded-md font-label-md text-label-md transition-all whitespace-nowrap cursor-pointer ${
                statusFilter === 'all'
                  ? 'bg-surface-container-lowest text-on-surface shadow-xs font-semibold'
                  : 'text-outline hover:text-on-surface'
              }`}
            >
              All ({totalCompletedShifts})
            </button>
            <button
              onClick={() => setStatusFilter('unrated')}
              type="button"
              className={`px-space-md py-space-xs rounded-md font-label-md text-label-md transition-all whitespace-nowrap cursor-pointer ${
                statusFilter === 'unrated'
                  ? 'bg-surface-container-lowest text-on-surface shadow-xs font-semibold'
                  : 'text-outline hover:text-on-surface'
              }`}
            >
              Unrated ({awaitingRatingCount})
            </button>
            <button
              onClick={() => setStatusFilter('rated')}
              type="button"
              className={`px-space-md py-space-xs rounded-md font-label-md text-label-md transition-all whitespace-nowrap cursor-pointer ${
                statusFilter === 'rated'
                  ? 'bg-surface-container-lowest text-on-surface shadow-xs font-semibold'
                  : 'text-outline hover:text-on-surface'
              }`}
            >
              Rated ({ratedRows.length})
            </button>
          </div>
        </div>

        {/* Counter Badge */}
        <div className="flex items-center gap-space-sm justify-between sm:justify-end text-outline font-label-sm text-label-sm">
          <span>Showing {filteredRows.length} of {totalCompletedShifts} shifts</span>
        </div>
      </div>

      {/* Main Table / Feedback List */}
      <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 overflow-hidden mb-space-xl">
        {/* Table Header */}
        <div className="hidden md:grid grid-cols-12 px-space-lg py-space-md bg-surface-container-low font-label-sm text-label-sm text-outline uppercase tracking-wider border-b border-outline-variant/20">
          <div className="col-span-3">Volunteer</div>
          <div className="col-span-3">Task</div>
          <div className="col-span-2">Time</div>
          <div className="col-span-2">Rating</div>
          <div className="col-span-2 text-right">Actions</div>
        </div>

        {/* Empty State when no rows exist */}
        {filteredRows.length === 0 ? (
          <div className="py-16 px-space-lg flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-outline mb-space-md">
              <span className="material-symbols-outlined text-[32px]">rate_review</span>
            </div>
            <h3 className="font-headline-sm text-headline-sm text-on-surface tracking-tight mb-space-xs">
              {totalCompletedShifts === 0
                ? 'No completed tasks to rate yet'
                : 'No shifts match your search criteria'}
            </h3>
            <p className="font-body-md text-body-md text-outline max-w-md mx-auto mb-space-lg">
              {totalCompletedShifts === 0
                ? 'Only published assignment plans display shifts for performance appraisal and accreditation verification.'
                : 'Try adjusting your search query or switching the status filter tab.'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-outline-variant/20">
            {filteredRows.map((row) => {
              const state = editStates[row.assignmentId] || {}
              const isRated = row.rating !== null && row.rating !== undefined
              const isEditing = state.isEditing || (!isRated && state.rating !== undefined)

              const activeRating = state.rating !== undefined ? state.rating : row.rating
              const activeComment = state.comment !== undefined ? state.comment : row.comment

              return (
                <div
                  key={row.assignmentId}
                  className="p-space-lg transition-colors hover:bg-surface-container-lowest/70 flex flex-col gap-space-md"
                >
                  {/* Row summary info */}
                  <div className="grid grid-cols-1 md:grid-cols-12 gap-space-md items-center">
                    {/* Volunteer Column */}
                    <div className="md:col-span-3 flex items-center gap-space-sm">
                      <div className="w-10 h-10 rounded-full bg-primary/10 text-primary flex items-center justify-center shrink-0 font-label-md font-semibold">
                        {row.volunteerName
                          .split(' ')
                          .map((n) => n[0])
                          .slice(0, 2)
                          .join('')}
                      </div>
                      <div className="flex flex-col min-w-0">
                        <Link
                          to={`/volunteers/${row.volunteerId}`}
                          className="font-label-md text-label-md text-on-surface hover:text-primary font-medium truncate"
                        >
                          {row.volunteerName}
                        </Link>
                        <span className="font-body-sm text-body-sm text-outline truncate">
                          {row.department ? `${row.department} ${row.year ? `· Year ${row.year}` : ''}` : 'Volunteer'}
                        </span>
                      </div>
                    </div>

                    {/* Task Column */}
                    <div className="md:col-span-3 flex flex-col justify-center min-w-0">
                      <span className="font-label-md text-label-md text-on-surface font-medium truncate">
                        {row.taskTitle}
                      </span>
                      <span className="font-label-sm text-label-sm text-primary truncate">
                        {row.eventName}
                      </span>
                    </div>

                    {/* Time Column */}
                    <div className="md:col-span-2 flex flex-col justify-center min-w-0 text-outline">
                      <span className="font-label-md text-label-md text-on-surface truncate">
                        {row.timeRange}
                      </span>
                      <span className="font-body-sm text-body-sm text-outline truncate">
                        Shift Verified
                      </span>
                    </div>

                    {/* Rating display / Stars */}
                    <div className="md:col-span-2 flex items-center gap-space-xs">
                      {isEditing ? (
                        <div className="flex items-center gap-1">
                          {[1, 2, 3, 4, 5].map((starVal) => {
                            const isFilled = activeRating !== null && starVal <= activeRating
                            return (
                              <button
                                key={starVal}
                                type="button"
                                onClick={() => handleSetRating(row.assignmentId, starVal)}
                                className="p-0.5 text-outline hover:text-amber-500 transition-colors cursor-pointer focus:outline-none"
                                title={`${starVal} Star${starVal > 1 ? 's' : ''}`}
                              >
                                <span
                                  className={`material-symbols-outlined text-[24px] ${
                                    isFilled ? 'text-amber-500' : 'text-outline'
                                  }`}
                                  style={{ fontVariationSettings: isFilled ? "'FILL' 1" : "'FILL' 0" }}
                                >
                                  star
                                </span>
                              </button>
                            )
                          })}
                          <span className="font-mono text-label-sm text-outline ml-1">
                            {activeRating ? `[${activeRating}.0]` : '[--]'}
                          </span>
                        </div>
                      ) : (
                        <div className="flex items-center gap-space-xs">
                          {isRated ? (
                            <div className="flex items-center gap-0.5 text-amber-500">
                              {[1, 2, 3, 4, 5].map((starVal) => (
                                <span
                                  key={starVal}
                                  className="material-symbols-outlined text-[20px]"
                                  style={{
                                    fontVariationSettings: starVal <= row.rating ? "'FILL' 1" : "'FILL' 0",
                                  }}
                                >
                                  star
                                </span>
                              ))}
                              <span className="font-mono text-label-sm text-on-surface font-semibold ml-1">
                                [{row.rating}.0]
                              </span>
                            </div>
                          ) : (
                            <span className="px-space-sm py-0.5 rounded-full bg-surface-container text-outline font-label-sm text-label-sm">
                              Unrated
                            </span>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Action Column */}
                    <div className="md:col-span-2 flex items-center justify-end gap-space-xs">
                      {state.savedNotification && (
                        <span className="flex items-center gap-1 text-emerald-600 font-label-sm text-label-sm animate-fade-in mr-2">
                          <span className="material-symbols-outlined text-[16px]">check_circle</span>
                          Saved
                        </span>
                      )}

                      {!isEditing && isRated && (
                        <button
                          type="button"
                          onClick={() => handleStartEdit(row)}
                          className="flex items-center gap-space-xs px-space-md py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[16px]">edit</span>
                          <span>Edit</span>
                        </button>
                      )}

                      {!isEditing && !isRated && (
                        <button
                          type="button"
                          onClick={() => handleStartEdit(row)}
                          className="flex items-center gap-space-xs px-space-md py-1.5 rounded-lg bg-primary text-on-primary font-label-md text-label-md shadow-sm hover:opacity-95 transition-all cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[16px]">star</span>
                          <span>Rate</span>
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Persisted Quote Comment when not editing */}
                  {!isEditing && isRated && row.comment && (
                    <div className="bg-surface-container-low p-space-md rounded-xl flex items-start gap-space-sm ml-0 md:ml-12 border border-outline-variant/20">
                      <span className="material-symbols-outlined text-outline text-[18px] shrink-0 mt-0.5">
                        format_quote
                      </span>
                      <p className="font-body-sm text-body-sm text-on-surface-variant italic leading-relaxed">
                        &quot;{row.comment}&quot;
                      </p>
                    </div>
                  )}

                  {/* Active Edit / Appraisal Form Form Controls */}
                  {isEditing && (
                    <div className="bg-surface-container-low p-space-md rounded-xl flex flex-col gap-space-md ml-0 md:ml-12 border border-outline-variant/30 animate-fade-in">
                      <div className="flex flex-col gap-space-xs">
                        <div className="flex items-center justify-between">
                          <label className="font-label-sm text-label-sm text-outline uppercase tracking-wider font-semibold">
                            Coordinator Evaluation Remarks (Optional, max 300 characters)
                          </label>
                          <span className="font-mono text-label-sm text-outline">
                            {(activeComment || '').length} / 300
                          </span>
                        </div>
                        <textarea
                          rows={2}
                          value={activeComment || ''}
                          onChange={(e) => handleCommentChange(row.assignmentId, e.target.value)}
                          placeholder="Add coordinator notes or feedback regarding performance, punctuality, or teamwork..."
                          className="w-full p-space-sm bg-surface-container-lowest text-on-surface placeholder:text-outline rounded-lg font-body-md text-body-md resize-none border border-outline-variant/30 focus:outline-none focus:ring-1 focus:ring-primary transition-all"
                        />
                      </div>

                      {/* Inline Error Message */}
                      {state.error && (
                        <div className="flex items-center gap-space-xs text-error font-body-sm text-body-sm">
                          <span className="material-symbols-outlined text-[16px]">error</span>
                          <span>{state.error}</span>
                        </div>
                      )}

                      {/* Form Action Buttons */}
                      <div className="flex items-center justify-end gap-space-sm pt-space-xs">
                        {isRated && (
                          <button
                            type="button"
                            onClick={() => handleCancelEdit(row.assignmentId)}
                            className="px-space-md py-1.5 rounded-lg border border-outline-variant/40 text-on-surface-variant hover:bg-surface-container font-label-md text-label-md transition-colors cursor-pointer"
                          >
                            Cancel
                          </button>
                        )}
                        <button
                          type="button"
                          disabled={!activeRating || state.isSaving}
                          onClick={() => handleSave(row.assignmentId)}
                          className="flex items-center gap-space-xs px-space-lg py-1.5 rounded-lg bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm hover:opacity-95 active:scale-95 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {state.isSaving ? (
                            <>
                              <div className="w-4 h-4 rounded-full border-2 border-white/20 border-t-white animate-spin" />
                              <span>Saving...</span>
                            </>
                          ) : (
                            <>
                              <span className="material-symbols-outlined text-[18px]">check</span>
                              <span>Save rating</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
