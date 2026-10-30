import { useState, useMemo, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getTasks, createTask, updateTask, deleteTask, getEvents } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import CreateTaskModal from '../components/tasks/CreateTaskModal'

export default function TasksPage() {
  const { role, selectedEventId, setSelectedEventId } = useAuth()

  // Fetch events for dropdown context and labels
  const { data: events } = useFetch(getEvents)

  // Fetch tasks matching the selected event
  const fetchTasksCallback = useCallback(() => {
    return getTasks(selectedEventId)
  }, [selectedEventId])

  const { data: tasks, loading, error, reload } = useFetch(fetchTasksCallback)

  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingTask, setEditingTask] = useState(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState(null)

  // Search & Filter controls
  const [searchQuery, setSearchQuery] = useState('')
  const [criticalOnly, setCriticalOnly] = useState(false)

  const selectedEvent = events?.find((e) => e.id === selectedEventId)

  // Filter tasks by search query & critical filter
  const filteredTasks = useMemo(() => {
    if (!tasks) return []
    return tasks.filter((task) => {
      if (criticalOnly && !task.critical) {
        return false
      }
      if (!searchQuery.trim()) {
        return true
      }
      const q = searchQuery.toLowerCase()
      const matchTitle = task.title?.toLowerCase().includes(q)
      const matchLoc = task.location?.toLowerCase().includes(q)
      const matchSkills = task.mandatorySkills?.some((s) => s.toLowerCase().includes(q))
      return matchTitle || matchLoc || matchSkills
    })
  }, [tasks, criticalOnly, searchQuery])

  // Redirect volunteers away (organiser only)
  if (role === 'volunteer') {
    return <Navigate to="/my-duties" replace />
  }

  // Handlers
  const handleOpenCreateModal = () => {
    setEditingTask(null)
    setIsModalOpen(true)
  }

  const handleOpenEditModal = (task) => {
    setEditingTask(task)
    setIsModalOpen(true)
  }

  const handleSubmitTask = async (taskData) => {
    if (editingTask?.id) {
      await updateTask(editingTask.id, taskData)
    } else {
      await createTask({
        ...taskData,
        eventId: selectedEventId || taskData.eventId,
      })
    }
    await reload()
  }

  const handleDeleteTask = async (id) => {
    try {
      await deleteTask(id)
      setDeleteConfirmId(null)
      await reload()
    } catch (err) {
      console.error('Failed to delete task:', err)
    }
  }

  return (
    <div className="flex flex-col w-full relative">
      {/* Top Context & Header */}
      <div className="flex flex-col gap-space-sm mb-space-xl">
        <div className="flex items-center justify-between flex-wrap gap-space-md">
          <div className="flex items-center gap-space-sm text-outline font-label-md text-label-md">
            <span className="text-outline font-label-sm text-label-sm">
              {selectedEvent ? selectedEvent.name : 'No event selected'}
            </span>
          </div>
        </div>

        <div className="flex items-start justify-between flex-wrap gap-space-lg mt-space-xs">
          <div className="flex flex-col gap-space-xs max-w-2xl">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Task Management
            </h1>
            <p className="font-body-md text-body-md text-on-surface-variant">
              Define operational tasks, assign headcount, and set algorithmic skill constraints for the solver.
            </p>
          </div>

          <div className="flex items-center gap-space-md">
            <button
              onClick={handleOpenCreateModal}
              disabled={!selectedEventId}
              className="h-10 px-space-lg rounded-lg bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm hover:bg-primary transition-all flex items-center gap-space-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              id="open-drawer-btn"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Create task</span>
            </button>
          </div>
        </div>
      </div>

      {/* If No Event is Selected: Prompt State */}
      {!selectedEventId && (
        <section className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center mb-space-xl">
          <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
            <span className="material-symbols-outlined text-[32px]">event_available</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
            Please select an event
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
            Tasks are organized per collegiate event. Choose an event from the topbar or select one below to view and manage its task roster.
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

      {/* Main Content when an Event is Selected */}
      {selectedEventId && (
        <>
          {/* Controls & Filters Bar */}
          <section className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm mb-space-xl flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            {/* Search Input */}
            <div className="relative flex-1 max-w-md">
              <span className="material-symbols-outlined text-[18px] text-outline absolute left-space-md top-1/2 -translate-y-1/2 select-none">
                search
              </span>
              <input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search tasks by title, skill, location..."
                type="text"
                className="w-full h-10 pl-10 pr-space-md rounded-lg bg-surface-container-low text-on-surface placeholder:text-outline font-body-md text-body-md focus:bg-surface-container-lowest focus:outline-none transition-colors"
              />
            </div>

            {/* Critical Only Filter Toggle */}
            <div className="flex items-center gap-space-md">
              <button
                type="button"
                onClick={() => setCriticalOnly((prev) => !prev)}
                className={`flex items-center gap-space-xs px-space-md py-space-xs rounded-lg font-label-md text-label-md transition-colors cursor-pointer ${
                  criticalOnly
                    ? 'bg-error-container text-on-error-container font-semibold'
                    : 'bg-surface-container-low hover:bg-surface-container text-on-surface-variant'
                }`}
              >
                <span className="material-symbols-outlined text-[18px]">
                  {criticalOnly ? 'priority_high' : 'filter_alt'}
                </span>
                <span>Critical tasks only</span>
              </button>
            </div>
          </section>

          {/* Loading State */}
          {loading && (
            <div className="bg-surface-container-lowest rounded-xl shadow-sm p-16 flex flex-col items-center justify-center text-center mb-space-xl">
              <div className="w-12 h-12 border-4 border-surface-container border-t-primary-container rounded-full animate-spin mb-space-md" />
              <p className="font-body-md text-body-md text-on-surface-variant">Loading task roster...</p>
            </div>
          )}

          {/* Error State */}
          {!loading && error && (
            <div className="bg-surface-container-lowest rounded-xl shadow-sm p-12 flex flex-col items-center justify-center text-center border border-error/20 mb-space-xl">
              <span className="material-symbols-outlined text-[40px] text-error mb-space-sm">
                error_outline
              </span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
                Failed to load tasks
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

          {/* Tasks Table */}
          {!loading && !error && (
            <section className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden flex flex-col mb-space-xl">
              <div className="px-space-xl py-space-lg flex items-center justify-between bg-surface-container-lowest border-b border-surface-container-low">
                <div className="flex items-center gap-space-md">
                  <h2 className="font-headline-sm text-headline-sm text-on-surface">
                    Event Task Roster
                  </h2>
                  <span className="px-2.5 py-0.5 rounded-full bg-surface-container-high font-label-sm text-label-sm text-outline">
                    {filteredTasks.length} {filteredTasks.length === 1 ? 'Task' : 'Total Tasks'}
                  </span>
                </div>
              </div>

              {/* Table Wrapper */}
              <div className="w-full overflow-x-auto">
                <table className="w-full text-left min-w-[800px] border-collapse">
                  <thead>
                    <tr className="bg-surface-container-low text-outline font-label-sm text-label-sm uppercase tracking-wider h-10">
                      <th className="px-space-lg font-medium w-1/4">Title</th>
                      <th className="px-space-lg font-medium w-1/6">Time Window</th>
                      <th className="px-space-lg font-medium w-28">Headcount</th>
                      <th className="px-space-lg font-medium">Mandatory Skills</th>
                      <th className="px-space-lg font-medium w-28">Critical</th>
                      <th className="px-space-lg font-medium w-28">Status</th>
                      <th className="px-space-lg font-medium text-right w-28">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTasks.length > 0 ? (
                      filteredTasks.map((task) => (
                        <tr
                          key={task.id}
                          className="border-b border-surface-container-low hover:bg-surface-container-low/40 transition-colors"
                        >
                          {/* Title & Location */}
                          <td className="py-space-md px-space-lg align-top">
                            <div className="flex flex-col">
                              <span className="font-label-lg text-label-lg text-on-surface font-semibold">
                                {task.title}
                              </span>
                              {task.location && (
                                <span className="font-body-sm text-body-sm text-outline flex items-center gap-0.5 mt-0.5">
                                  <span className="material-symbols-outlined text-[14px]">location_on</span>
                                  <span>{task.location}</span>
                                </span>
                              )}
                              {task.description && (
                                <span className="font-body-sm text-body-sm text-on-surface-variant line-clamp-1 mt-1">
                                  {task.description}
                                </span>
                              )}
                            </div>
                          </td>

                          {/* Time window */}
                          <td className="py-space-md px-space-lg align-top">
                            <div className="flex flex-col text-on-surface-variant font-body-sm text-body-sm">
                              {task.date && (
                                <span className="font-medium text-on-surface">{task.date}</span>
                              )}
                              <span>
                                {task.startTime} – {task.endTime}
                              </span>
                            </div>
                          </td>

                          {/* Headcount */}
                          <td className="py-space-md px-space-lg align-top">
                            <span className="inline-flex items-center gap-1 font-label-md text-label-md text-on-surface">
                              <span className="material-symbols-outlined text-[16px] text-outline">group</span>
                              <span>{task.headcount}</span>
                            </span>
                          </td>

                          {/* Mandatory skills chips */}
                          <td className="py-space-md px-space-lg align-top">
                            <div className="flex flex-wrap gap-1">
                              {task.mandatorySkills && task.mandatorySkills.length > 0 ? (
                                task.mandatorySkills.map((skill) => (
                                  <span
                                    key={skill}
                                    className="px-2 py-0.5 rounded-full bg-primary-fixed text-on-primary-fixed-variant font-label-sm text-label-sm"
                                  >
                                    {skill}
                                  </span>
                                ))
                              ) : (
                                <span className="text-outline font-body-sm text-body-sm italic">None</span>
                              )}
                            </div>
                          </td>

                          {/* Critical */}
                          <td className="py-space-md px-space-lg align-top">
                            {task.critical ? (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-error-container text-on-error-container font-label-sm text-label-sm font-semibold">
                                <span className="material-symbols-outlined text-[14px]">priority_high</span>
                                <span>Critical</span>
                              </span>
                            ) : (
                              <span className="font-label-sm text-label-sm text-outline">Standard</span>
                            )}
                          </td>

                          {/* Status */}
                          <td className="py-space-md px-space-lg align-top">
                            <span className="capitalize font-label-sm text-label-sm px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant">
                              {task.status || 'pending'}
                            </span>
                          </td>

                          {/* Actions */}
                          <td className="py-space-md px-space-lg align-top text-right">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                type="button"
                                onClick={() => handleOpenEditModal(task)}
                                className="p-1.5 rounded-lg text-outline hover:text-primary hover:bg-surface-container transition-colors cursor-pointer"
                                aria-label="Edit task"
                                title="Edit task"
                              >
                                <span className="material-symbols-outlined text-[18px]">edit</span>
                              </button>
                              <button
                                type="button"
                                onClick={() => setDeleteConfirmId(task.id)}
                                className="p-1.5 rounded-lg text-outline hover:text-error hover:bg-surface-container transition-colors cursor-pointer"
                                aria-label="Delete task"
                                title="Delete task"
                              >
                                <span className="material-symbols-outlined text-[18px]">delete</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    ) : (
                      /* Empty State Row */
                      <tr>
                        <td className="py-20 px-space-xl" colSpan="7">
                          <div className="flex flex-col items-center justify-center text-center max-w-md mx-auto">
                            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
                              <span className="material-symbols-outlined text-[32px]">assignment_add</span>
                            </div>
                            <h3 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
                              {searchQuery || criticalOnly ? 'No matching tasks' : 'No tasks created yet'}
                            </h3>
                            <p className="font-body-md text-body-md text-on-surface-variant mb-space-xl">
                              {searchQuery || criticalOnly
                                ? 'Try adjusting your search query or disabling the critical tasks filter.'
                                : 'Get started by defining roles, schedules, and required skills for this event.'}
                            </p>
                            <button
                              className="h-10 px-space-xl rounded-lg bg-primary-container text-on-primary font-label-lg text-label-lg shadow-sm hover:bg-primary transition-all flex items-center gap-space-xs cursor-pointer"
                              id="empty-state-create-btn"
                              type="button"
                              onClick={handleOpenCreateModal}
                            >
                              <span className="material-symbols-outlined text-[18px]">add</span>
                              <span>Create task</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}

      {/* Delete Confirmation Dialog */}
      {deleteConfirmId && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-inverse-surface/45 backdrop-blur-sm"
          onClick={() => setDeleteConfirmId(null)}
        >
          <div
            className="bg-surface-container-lowest max-w-sm w-full rounded-xl p-space-lg shadow-xl flex flex-col gap-space-md"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-space-sm text-error">
              <span className="material-symbols-outlined text-[24px]">warning</span>
              <h3 className="font-headline-sm text-headline-sm text-on-surface">Delete Task?</h3>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant">
              Are you sure you want to remove this task? This action cannot be undone.
            </p>
            <div className="flex items-center justify-end gap-space-sm mt-2">
              <button
                type="button"
                onClick={() => setDeleteConfirmId(null)}
                className="px-space-md py-2 rounded-lg text-on-surface-variant hover:bg-surface-container-low font-label-md text-label-md cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleDeleteTask(deleteConfirmId)}
                className="px-space-md py-2 rounded-lg bg-error text-on-error font-label-md text-label-md shadow-sm hover:opacity-90 cursor-pointer"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create / Edit Modal */}
      <CreateTaskModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleSubmitTask}
        initialData={editingTask}
        selectedEventId={selectedEventId}
        events={events || []}
      />
    </div>
  )
}
