import { useState, useMemo } from 'react'
import { getEvents, createEvent } from '../services/api'
import { useFetch } from '../hooks/useFetch'
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
  const { data: events, loading, error, reload } = useFetch(getEvents)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFilter, setSelectedFilter] = useState('all')

  const handleCreateEvent = async (eventData) => {
    await createEvent(eventData)
    await reload()
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
              <span className="text-outline font-label-sm text-label-sm">No event selected</span>
            </div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Events</h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
              Manage collegiate festivals, symposiums, and departmental gatherings.
            </p>
          </div>
          <div className="flex items-center gap-space-sm self-start md:self-auto">
            <button
              onClick={() => setIsModalOpen(true)}
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
              onClick={() => reload()}
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
          <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden flex flex-col">
            {/* Table Header */}
            <div className="overflow-x-auto">
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
                      return (
                        <tr
                          key={evt.id}
                          className="hover:bg-surface-container-low/50 transition-colors"
                        >
                          <td className="py-space-md px-space-lg">
                            <div className="flex flex-col">
                              <span className="font-label-lg text-label-lg text-on-surface font-medium">
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
                          <td className="py-space-md px-space-lg text-right">
                            <button
                              aria-label="View event options"
                              className="p-space-xs rounded-lg text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                              type="button"
                            >
                              <span className="material-symbols-outlined text-[20px]">
                                more_vert
                              </span>
                            </button>
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
                    onClick={() => setIsModalOpen(true)}
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

      {/* Create Event Modal */}
      <CreateEventModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreate={handleCreateEvent}
      />
    </div>
  )
}
