import { useState, useRef, useEffect } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useEvent } from '../../context/EventContext'

export default function Topbar() {
  const { user, role, toggleRole } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent } = useEvent()
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const dropdownRef = useRef(null)

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <header className="fixed top-0 left-64 right-0 h-16 bg-surface-container-lowest/90 backdrop-blur-xl border-b border-slate-200 shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-40 flex items-center justify-between px-gutter-lg">
      <div className="flex items-center gap-space-md">
        <div className="relative flex items-center" ref={dropdownRef}>
          <button
            onClick={() => setIsDropdownOpen((prev) => !prev)}
            className="flex items-center gap-space-sm px-space-md py-space-xs bg-surface-container-low hover:bg-surface-container text-on-surface-variant hover:text-on-surface rounded-lg transition-colors cursor-pointer"
            type="button"
            aria-haspopup="listbox"
            aria-expanded={isDropdownOpen}
          >
            <span className="material-symbols-outlined text-[18px] text-outline">event</span>
            <span className="font-label-md text-label-md text-on-surface font-medium">
              {selectedEvent ? selectedEvent.name : 'Select event'}
            </span>
            <span className="material-symbols-outlined text-[18px] text-outline">expand_more</span>
          </button>

          {isDropdownOpen && (
            <div className="absolute top-full left-0 mt-1 w-64 bg-surface-container-lowest rounded-xl shadow-lg border border-slate-200 py-1 z-50 overflow-hidden">
              <button
                type="button"
                onClick={() => {
                  setSelectedEventId('')
                  setIsDropdownOpen(false)
                }}
                className={`w-full text-left px-space-md py-2 text-label-md flex items-center justify-between hover:bg-surface-container-low transition-colors cursor-pointer ${
                  !selectedEventId ? 'text-primary font-semibold bg-surface-container-low/50' : 'text-on-surface-variant'
                }`}
              >
                <span>None (Clear selection)</span>
                {!selectedEventId && (
                  <span className="material-symbols-outlined text-[16px] text-primary">check</span>
                )}
              </button>
              {events && events.length > 0 && (
                <div className="border-t border-slate-100 my-1" />
              )}
              {events?.map((evt) => (
                <button
                  key={evt.id}
                  type="button"
                  onClick={() => {
                    setSelectedEventId(evt.id)
                    setIsDropdownOpen(false)
                  }}
                  className={`w-full text-left px-space-md py-2 text-label-md flex items-center justify-between hover:bg-surface-container-low transition-colors cursor-pointer ${
                    selectedEventId === evt.id ? 'text-primary font-semibold bg-surface-container-low/50' : 'text-on-surface'
                  }`}
                >
                  <span className="truncate pr-2">{evt.name}</span>
                  {selectedEventId === evt.id && (
                    <span className="material-symbols-outlined text-[16px] text-primary shrink-0">check</span>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="flex items-center gap-space-lg">
        {/* Role Toggle Switch / Button for Demo */}
        <button
          onClick={toggleRole}
          title="Click to toggle between Organiser and Volunteer role"
          className="flex items-center gap-space-xs px-space-md py-space-xs rounded-full border border-outline-variant bg-surface-container-low hover:bg-surface-container text-on-surface text-label-sm font-label-sm transition-colors cursor-pointer"
          type="button"
        >
          <span className="material-symbols-outlined text-[16px] text-primary">sync_alt</span>
          <span>Role: <strong className="capitalize text-primary">{role}</strong></span>
        </button>

        <button
          className="flex items-center gap-space-xs px-space-md py-space-xs rounded-full bg-surface-container text-primary hover:bg-surface-container-high transition-colors cursor-pointer"
          type="button"
        >
          <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
          <span className="font-label-sm text-label-sm">AI Assistant</span>
        </button>

        {/* Notification button with permanent red dot removed as instructed */}
        <button
          aria-label="Notifications"
          className="relative p-space-xs text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low rounded-full transition-colors cursor-pointer"
          type="button"
        >
          <span className="material-symbols-outlined text-[22px]">notifications</span>
        </button>

        <div className="flex items-center gap-space-md pl-space-md">
          <div className="w-8 h-8 rounded-full bg-surface-container-low flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[20px]">person</span>
          </div>
          <div className="flex flex-col text-left">
            <span className="font-label-md text-label-md text-on-surface leading-tight">
              {user?.name || 'User name'}
            </span>
            <span className="font-label-sm text-label-sm text-outline leading-tight capitalize">
              {user?.displayRole || role}
            </span>
          </div>
        </div>
      </div>
    </header>
  )
}
