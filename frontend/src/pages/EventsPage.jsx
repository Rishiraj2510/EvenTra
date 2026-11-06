import { useState, useMemo, useRef, useEffect } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { createEvent, updateEvent, deleteEvent } from '../services/api'
import CreateEventModal from '../components/events/CreateEventModal'

const STATUS_CONFIG = {
  draft: {
    label: 'Draft',
    bg: 'bg-surface-container-high text-on-surface-variant',
  },
  planning: {
    label: 'Planning',
    bg: 'bg-primary-fixed text-on-primary-fixed-variant',
  },
  live: {
    label: 'Live',
    bg: 'bg-secondary-fixed text-on-secondary-fixed-variant',
  },
  done: {
    label: 'Done',
    bg: 'bg-surface-container-low text-outline',
  },
}

export default function EventsPage() {
  const { role } = useAuth()
  const { events, loading, error, refreshEvents, selectEvent, selectedEventId } = useEvent()
  const navigate = useNavigate()

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFilter, setSelectedFilter] = useState('all')

  // Action menu state: { eventId, statusSubmenuOpen }
  const [openMenuEventId, setOpenMenuEventId] = useState(null)
  const [isStatusSubmenuOpen, setIsStatusSubmenuOpen] = useState(false)
  const menuContainerRef = useRef(null)

  // Confirm delete dialog state
  const [eventToDelete, setEventToDelete] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)

  // Close action dropdown on outside click or Escape
  useEffect(() => {
    function handleClickOutside(e) {
      if (menuContainerRef.current && !menuContainerRef.current.contains(e.target)) {
        setOpenMenuEventId(null)
        setIsStatusSubmenuOpen(false)
      }
    }

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        setOpenMenuEventId(null)
        setIsStatusSubmenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [])

  const handleOpenCreateModal = () => {
    setEditingEvent(null)
    setIsModalOpen(true)
  }

  const handleOpenEditModal = (evt) => {
    setEditingEvent(evt)
    setIsModalOpen(true)
    setOpenMenuEventId(null)
    setIsStatusSubmenuOpen(false)
  }

  const handleSaveEvent = async (eventData) => {
    if (editingEvent) {
      await updateEvent(editingEvent.id, eventData)
      await refreshEvents()
    } else {
      const newEvent = await createEvent(eventData)
      await refreshEvents()
      if (newEvent?.id) {
        selectEvent(newEvent.id)
      }
    }
  }

  const handleRowClick = (eventId) => {
    selectEvent(eventId)
    navigate('/dashboard')
  }

  const handleOpenDashboard = (eventId) => {
    selectEvent(eventId)
    setOpenMenuEventId(null)
    navigate('/dashboard')
  }

  const handleChangeStatus = async (eventId, newStatus) => {
    setOpenMenuEventId(null)
    setIsStatusSubmenuOpen(false)
    await updateEvent(eventId, { status: newStatus })
    await refreshEvents()
  }

  const handleDeleteConfirm = async () => {
    if (!eventToDelete) return
    setIsDeleting(true)
    try {
      await deleteEvent(eventToDelete.id)
      if (selectedEventId === eventToDelete.id) {
        selectEvent('')
      }
      await refreshEvents()
      setEventToDelete(null)
    } catch (err) {
      console.error('Failed to delete event:', err)
    } finally {
      setIsDeleting(false)
    }
  }

  // Filter & Search
  const filteredEvents = useMemo(() => {
    if (!events) return []
    return events.filter((evt) => {
      const matchesFilter =
        selectedFilter === 'all' ||
        evt.status?.toLowerCase() === selectedFilter.toLowerCase()

      const query = searchQuery.trim().toLowerCase()
      const matchesSearch =
        !query ||
        evt.name?.toLowerCase().includes(query) ||
        evt.description?.toLowerCase().includes(query) ||
        evt.category?.toLowerCase().includes(query)

      return matchesFilter && matchesSearch
    })
  }, [events, selectedFilter, searchQuery])

  // Redirect volunteers away (organiser only)
  if (role === 'volunteer') {
    return <Navigate to={getHomeRoute(role)} replace />
  }

  // Stat metrics
  const totalCount = events ? events.length : 0
  const activeCount = events
    ? events.filter((e) => e.status === 'live' || e.status === 'planning').length
    : 0
  const completedCount = events
    ? events.filter((e) => e.status === 'done').length
    : 0

  const filterOptions = ['all', 'draft', 'planning', 'live', 'done']

  return (
    <div className="flex flex-col w-full">
      <div className="flex flex-col gap-space-lg w-full">
        {/* Top Context & Breadcrumb Bar */}
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-md">
          <div className="flex flex-col gap-space-xs">
            <div className="flex items-center gap-space-xs text-outline font-label-sm text-label-sm tracking-wide">
              <span className="text-outline font-label-sm text-label-sm">
                {selectedEventId
                  ? (events?.find((e) => e.id === selectedEventId)?.name || 'Event selected')
                  : 'No event selected'}
              </span>
            </div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Events</h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
              Manage collegiate festivals, symposiums, and departmental gatherings.
            </p>
          </div>
          <div className="flex items-center gap-space-sm self-start md:self-auto">
            <button
              onClick={handleOpenCreateModal}
              className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer"
              id="open-modal-btn"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>New event</span>
            </button>
          </div>
        </div>

        {/* Metrics Stat Strip */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md w-full">
          <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex items-center justify-between">
            <div className="flex flex-col">
              <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">
                Total Events
              </span>
              <span className="font-headline-lg text-headline-lg text-on-surface font-bold mt-space-xs">
                {totalCount}
              </span>
            </div>
            <div className="w-10 h-10 rounded-xl bg-surface-container-low flex items-center justify-center text-primary-container">
              <span className="material-symbols-outlined text-[22px]">calendar_month</span>
            </div>
          </div>
          <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex items-center justify-between">
            <div className="flex flex-col">
              <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">
                Active
              </span>
              <span className="font-headline-lg text-headline-lg text-on-surface font-bold mt-space-xs">
                {activeCount}
              </span>
            </div>
            <div className="w-10 h-10 rounded-xl bg-surface-container-low flex items-center justify-center text-secondary">
              <span className="material-symbols-outlined text-[22px]">schedule</span>
            </div>
          </div>
          <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex items-center justify-between">
            <div className="flex flex-col">
              <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">
                Completed
              </span>
              <span className="font-headline-lg text-headline-lg text-on-surface font-bold mt-space-xs">
                {completedCount}
              </span>
            </div>
            <div className="w-10 h-10 rounded-xl bg-surface-container-low flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[22px]">task_alt</span>
            </div>
          </div>
        </div>

        {/* Filter & Control Toolbar */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
          {/* Search Input */}
          <div className="relative flex-1 max-w-md">
            <span className="material-symbols-outlined text-[18px] text-outline absolute left-space-md top-1/2 -translate-y-1/2 select-none">
              search
            </span>
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full h-10 pl-10 pr-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
              placeholder="Search events by title or venue..."
              type="text"
            />
          </div>

          {/* Filter Controls */}
          <div className="flex items-center gap-space-xs overflow-x-auto pb-1 lg:pb-0">
            <span className="font-label-sm text-label-sm text-outline uppercase px-space-xs hidden sm:inline-block">
              Filter:
            </span>
            {filterOptions.map((opt) => {
              const isSelected = selectedFilter === opt
              return (
                <button
                  key={opt}
                  onClick={() => setSelectedFilter(opt)}
                  className={`px-space-md py-space-xs rounded-lg font-label-md text-label-md capitalize transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-primary-container text-on-primary'
                      : 'bg-surface-container-low hover:bg-surface-container text-on-surface-variant'
                  }`}
                  type="button"
                >
                  {opt}
                </button>
              )
            })}
          </div>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center">
            <div className="w-12 h-12 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-space-md"></div>
            <p className="font-body-md text-body-md text-on-surface-variant">Loading events...</p>
          </div>
        )}

        {/* Error State */}
        {!loading && error && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm p-12 flex flex-col items-center justify-center text-center border border-error/20">
            <span className="material-symbols-outlined text-[40px] text-error mb-space-sm">
              error_outline
            </span>
            <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
              Failed to load events
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-lg">
              {error}
            </p>
            <button
              onClick={() => refreshEvents()}
              className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all cursor-pointer"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              <span>Try again</span>
            </button>
          </div>
        )}

        {/* Content State: Table or Empty */}
        {!loading && !error && (
          <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-visible flex flex-col">
            {/* Table Header */}
            <div className="overflow-x-auto overflow-y-visible">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-surface-container-low text-outline font-label-sm text-label-sm uppercase tracking-wider h-10">
                    <th className="py-space-xs px-space-lg w-2/5" scope="col">
                      Event name
                    </th>
                    <th className="py-space-xs px-space-lg w-1/4" scope="col">
                      Dates
                    </th>
                    <th className="py-space-xs px-space-lg w-1/5" scope="col">
                      Status
                    </th>
                    <th className="py-space-xs px-space-lg text-right w-1/6" scope="col">
                      Actions
                    </th>
                  </tr>
                </thead>
                {filteredEvents.length > 0 && (
                  <tbody className="divide-y divide-surface-container-low">
                    {filteredEvents.map((evt) => {
                      const statusBadge = STATUS_CONFIG[evt.status?.toLowerCase()] || {
                        label: evt.status,
                        bg: 'bg-surface-container-low text-on-surface-variant',
                      }
                      const isMenuOpen = openMenuEventId === evt.id
                      return (
                        <tr
                          key={evt.id}
                          onClick={() => handleRowClick(evt.id)}
                          className="hover:bg-surface-container-low/60 hover:shadow-[inset_2px_0_0_0_var(--color-primary,#4f46e5)] transition-all cursor-pointer group"
                        >
                          <td className="py-space-md px-space-lg">
                            <div className="flex flex-col">
                              <span className="font-label-lg text-label-lg text-on-surface group-hover:text-primary font-medium transition-colors">
                                {evt.name}
                              </span>
                              {evt.description && (
                                <span className="font-body-sm text-body-sm text-on-surface-variant line-clamp-1 max-w-md">
                                  {evt.description}
                                </span>
                              )}
                              {evt.category && (
                                <span className="font-label-sm text-label-sm text-outline capitalize mt-0.5">
                                  {evt.category}
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="py-space-md px-space-lg">
                            <div className="flex items-center gap-space-xs text-on-surface-variant font-body-md text-body-md">
                              <span className="material-symbols-outlined text-[16px] text-outline">
                                event
                              </span>
                              <span>
                                {evt.startDate}
                                {evt.endDate && evt.endDate !== evt.startDate
                                  ? ` - ${evt.endDate}`
                                  : ''}
                              </span>
                            </div>
                          </td>
                          <td className="py-space-md px-space-lg">
                            <span
                              className={`inline-flex items-center px-space-sm py-0.5 rounded-full font-label-sm text-label-sm capitalize font-medium ${statusBadge.bg}`}
                            >
                              {statusBadge.label}
                            </span>
                          </td>
                          <td
                            className="py-space-md px-space-lg text-right relative"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <div
                              className="relative inline-block text-left"
                              ref={isMenuOpen ? menuContainerRef : null}
                            >
                              <button
                                aria-label={`Actions for ${evt.name}`}
                                aria-haspopup="menu"
                                aria-expanded={isMenuOpen}
                                onClick={(e) => {
                                  e.stopPropagation()
                                  setOpenMenuEventId(isMenuOpen ? null : evt.id)
                                  setIsStatusSubmenuOpen(false)
                                }}
                                className="p-space-xs rounded-lg text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[20px]">
                                  more_vert
                                </span>
                              </button>

                              {isMenuOpen && (
                                <div
                                  className="absolute right-0 top-full mt-1 w-48 bg-surface-container-lowest rounded-xl shadow-xl border border-slate-200 py-1.5 z-50 animate-in fade-in zoom-in-95 duration-100"
                                  role="menu"
                                >
                                  {/* Open dashboard */}
                                  <button
                                    type="button"
                                    role="menuitem"
                                    onClick={() => handleOpenDashboard(evt.id)}
                                    className="w-full text-left px-space-md py-2 text-label-md text-on-surface hover:bg-surface-container-low flex items-center gap-space-sm transition-colors cursor-pointer"
                                  >
                                    <span className="material-symbols-outlined text-[18px] text-outline">
                                      dashboard
                                    </span>
                                    <span>Open dashboard</span>
                                  </button>

                                  {/* Edit */}
                                  <button
                                    type="button"
                                    role="menuitem"
                                    onClick={() => handleOpenEditModal(evt)}
                                    className="w-full text-left px-space-md py-2 text-label-md text-on-surface hover:bg-surface-container-low flex items-center gap-space-sm transition-colors cursor-pointer"
                                  >
                                    <span className="material-symbols-outlined text-[18px] text-outline">
                                      edit
                                    </span>
                                    <span>Edit</span>
                                  </button>

                                  {/* Change status */}
                                  <div className="relative">
                                    <button
                                      type="button"
                                      role="menuitem"
                                      onClick={() => setIsStatusSubmenuOpen((prev) => !prev)}
                                      className="w-full text-left px-space-md py-2 text-label-md text-on-surface hover:bg-surface-container-low flex items-center justify-between transition-colors cursor-pointer"
                                    >
                                      <div className="flex items-center gap-space-sm">
                                        <span className="material-symbols-outlined text-[18px] text-outline">
                                          published_with_changes
                                        </span>
                                        <span>Change status</span>
                                      </div>
                                      <span className="material-symbols-outlined text-[16px] text-outline">
                                        {isStatusSubmenuOpen ? 'expand_less' : 'chevron_right'}
                                      </span>
                                    </button>

                                    {isStatusSubmenuOpen && (
                                      <div className="bg-surface-container-low/70 py-1 pl-space-md border-y border-slate-100">
                                        {['draft', 'planning', 'live', 'done'].map((st) => (
                                          <button
                                            key={st}
                                            type="button"
                                            onClick={() => handleChangeStatus(evt.id, st)}
                                            className={`w-full text-left px-space-md py-1.5 text-label-sm capitalize flex items-center justify-between hover:bg-surface-container transition-colors cursor-pointer ${
                                              evt.status === st
                                                ? 'text-primary font-semibold'
                                                : 'text-on-surface-variant'
                                            }`}
                                          >
                                            <span>{st}</span>
                                            {evt.status === st && (
                                              <span className="material-symbols-outlined text-[14px] text-primary pr-2">
                                                check
                                              </span>
                                            )}
                                          </button>
                                        ))}
                                      </div>
                                    )}
                                  </div>

                                  <div className="border-t border-slate-100 my-1" />

                                  {/* Delete */}
                                  <button
                                    type="button"
                                    role="menuitem"
                                    onClick={() => {
                                      setOpenMenuEventId(null)
                                      setIsStatusSubmenuOpen(false)
                                      setEventToDelete(evt)
                                    }}
                                    className="w-full text-left px-space-md py-2 text-label-md text-error hover:bg-error/10 flex items-center gap-space-sm transition-colors cursor-pointer"
                                  >
                                    <span className="material-symbols-outlined text-[18px]">
                                      delete
                                    </span>
                                    <span>Delete</span>
                                  </button>
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                )}
              </table>
            </div>

            {/* Empty State Container */}
            {filteredEvents.length === 0 && (
              <div className="py-16 px-space-lg flex flex-col items-center justify-center text-center">
                <div className="w-16 h-16 rounded-full bg-surface-container-low flex items-center justify-center text-outline mb-space-md">
                  <span className="material-symbols-outlined text-[36px]">event_busy</span>
                </div>
                <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
                  {searchQuery || selectedFilter !== 'all' ? 'No matching events' : 'No events yet'}
                </h2>
                <p className="font-body-md text-body-md text-on-surface-variant max-w-sm mb-space-lg">
                  {searchQuery || selectedFilter !== 'all'
                    ? 'Try adjusting your search criteria or filter to find events.'
                    : 'No events yet. Create your first event'}
                </p>
                <div className="flex flex-col sm:flex-row items-center gap-space-md">
                  <button
                    onClick={handleOpenCreateModal}
                    className="inline-flex items-center gap-space-xs h-10 px-space-lg rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer"
                    id="empty-state-cta-btn"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[18px]">add</span>
                    <span>Create event</span>
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Create / Edit Event Modal */}
      <CreateEventModal
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false)
          setEditingEvent(null)
        }}
        onCreate={handleSaveEvent}
        initialData={editingEvent}
      />

      {/* Delete Confirmation Modal */}
      {eventToDelete && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-space-md sm:p-space-lg bg-inverse-surface/45 backdrop-blur-sm transition-opacity"
          onClick={(e) => {
            if (e.target === e.currentTarget && !isDeleting) setEventToDelete(null)
          }}
        >
          <div className="bg-surface-container-lowest w-full max-w-md rounded-xl shadow-xl overflow-hidden flex flex-col p-space-xl">
            <div className="flex items-center gap-space-md mb-space-md">
              <div className="w-10 h-10 rounded-full bg-error/10 text-error flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">warning</span>
              </div>
              <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                Delete event?
              </h3>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mb-space-xl leading-relaxed">
              Are you sure you want to delete{' '}
              <span className="font-semibold text-on-surface">{eventToDelete.name}</span>? This
              action cannot be undone.
            </p>
            <div className="flex items-center justify-end gap-space-md">
              <button
                type="button"
                onClick={() => setEventToDelete(null)}
                disabled={isDeleting}
                className="h-10 px-space-lg rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-lg text-label-lg transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeleteConfirm}
                disabled={isDeleting}
                className="h-10 px-space-lg rounded-lg bg-error hover:bg-error/90 text-on-error font-label-lg text-label-lg shadow-sm transition-all active:scale-[0.99] cursor-pointer disabled:opacity-50"
              >
                {isDeleting ? 'Deleting...' : 'Delete event'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
