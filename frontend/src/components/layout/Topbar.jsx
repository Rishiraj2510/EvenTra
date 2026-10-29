import { useAuth } from '../../context/AuthContext'

export default function Topbar() {
  const { user, role, toggleRole } = useAuth()

  return (
    <header className="fixed top-0 left-64 right-0 h-16 bg-surface-container-lowest/90 backdrop-blur-xl border-b border-slate-200 shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-40 flex items-center justify-between px-gutter-lg">
      <div className="flex items-center gap-space-md">
        <div className="relative flex items-center">
          <button
            className="flex items-center gap-space-sm px-space-md py-space-xs bg-surface-container-low hover:bg-surface-container text-on-surface-variant hover:text-on-surface rounded-lg transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px] text-outline">event</span>
            <span className="font-label-md text-label-md text-on-surface-variant">Select event</span>
            <span className="material-symbols-outlined text-[18px] text-outline">expand_more</span>
          </button>
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
