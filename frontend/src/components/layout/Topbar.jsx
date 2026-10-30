import { useState, useRef, useEffect } from 'react'
import { useLocation, matchPath } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useEvent } from '../../context/EventContext'

// Routes where the event selector is needed and active
const EVENT_PICKER_ROUTES = [
  '/dashboard',
  '/events',
  '/tasks',
  '/volunteers',
  '/assignment-plan',
  '/ai-assistant',
  '/feedback',
  '/certificates',
  '/my-duties',
]

export default function Topbar() {
  const location = useLocation()
  const { user, role, logout } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent } = useEvent()
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false)
  const dropdownRef = useRef(null)
  const userMenuRef = useRef(null)

  // Check if current route should display the event picker
  const showEventPicker = EVENT_PICKER_ROUTES.some((pattern) =>
    Boolean(matchPath({ path: pattern, end: true }, location.pathname))
  )

  // Close event dropdown & user dropdown on outside click or Escape
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsDropdownOpen(false)
      }
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setIsUserMenuOpen(false)
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        setIsDropdownOpen(false)
        setIsUserMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [])

  return (
    <header className="fixed top-0 left-64 right-0 h-16 bg-surface-container-lowest/90 backdrop-blur-xl border-b border-slate-200 shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-40 flex items-center justify-between px-gutter-lg">
      <div className="flex items-center gap-space-md">
        {showEventPicker && (
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
        )}
      </div>

      <div className="flex items-center gap-space-lg">
        {/* AI Assistant - Visible only for organiser/coordinator roles */}
        {role !== 'volunteer' && (
          <button
            className="flex items-center gap-space-xs px-space-md py-space-xs rounded-full bg-surface-container text-primary hover:bg-surface-container-high transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">auto_awesome</span>
            <span className="font-label-sm text-label-sm">AI Assistant</span>
          </button>
        )}

        {/* Notification button */}
        <button
          aria-label="Notifications"
          className="relative p-space-xs text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low rounded-full transition-colors cursor-pointer"
          type="button"
        >
          <span className="material-symbols-outlined text-[22px]">notifications</span>
        </button>

        {/* User dropdown area */}
        <div className="relative" ref={userMenuRef}>
          <button
            type="button"
            onClick={() => setIsUserMenuOpen((prev) => !prev)}
            aria-haspopup="menu"
            aria-expanded={isUserMenuOpen}
            className="flex items-center gap-space-md pl-space-md py-1 pr-2 rounded-xl hover:bg-surface-container-low transition-colors cursor-pointer text-left select-none"
          >
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
            <span className="material-symbols-outlined text-[18px] text-outline ml-0.5">
              expand_more
            </span>
          </button>

          {isUserMenuOpen && (
            <div
              role="menu"
              className="absolute right-0 top-full mt-2 w-52 bg-surface-container-lowest rounded-xl shadow-lg border border-slate-200 py-1.5 z-50 overflow-hidden"
            >
              <div className="px-space-md py-2 border-b border-slate-100">
                <p className="font-label-md text-label-md font-semibold text-on-surface truncate">
                  {user?.name || 'User name'}
                </p>
                <p className="font-body-sm text-body-sm text-outline truncate">
                  {user?.email || ''}
                </p>
              </div>

              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setIsUserMenuOpen(false)
                  logout()
                }}
                className="w-full text-left px-space-md py-2 text-label-md text-error hover:bg-error-container/40 flex items-center gap-space-sm transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">logout</span>
                <span className="font-medium">Log out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}

