import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import logo from '../../assets/logo.png'

const ORGANISER_NAV_ITEMS = [
  { path: '/dashboard', label: 'Dashboard', icon: 'grid_view' },
  { path: '/events', label: 'Events', icon: 'calendar_month' },
  { path: '/tasks', label: 'Tasks', icon: 'task_alt' },
  { path: '/volunteers', label: 'Volunteers', icon: 'group' },
  { path: '/assignment-plan', label: 'Assignment Plan', icon: 'assignment_ind' },
  { path: '/ai-assistant', label: 'AI Assistant', icon: 'auto_awesome' },
  { path: '/feedback', label: 'Feedback', icon: 'rate_review' },
  { path: '/certificates', label: 'Certificates', icon: 'workspace_premium' },
  { path: '/settings', label: 'Settings', icon: 'settings' },
]

const VOLUNTEER_NAV_ITEMS = [
  { path: '/my-duties', label: 'My Duties', icon: 'task_alt' },
  { path: '/my-profile', label: 'My Profile', icon: 'person' },
  { path: '/my-certificates', label: 'My Certificates', icon: 'workspace_premium' },
]

export default function Sidebar() {
  const { role } = useAuth()
  const navItems = role === 'volunteer' ? VOLUNTEER_NAV_ITEMS : ORGANISER_NAV_ITEMS

  return (
    <aside className="fixed left-0 top-0 h-full w-64 bg-surface-container-lowest z-50 flex flex-col shadow-[0_1px_8px_rgba(0,0,0,0.04)]">
      <div className="flex h-16 items-center px-4 border-b border-slate-200 bg-white">
        <img
          alt="Eventra Logo"
          className="h-8 w-auto object-contain"
          src={logo}
        />
      </div>

      <div className="px-space-md py-space-sm">
        <span className="px-space-sm font-label-sm text-label-sm text-outline uppercase tracking-wider">
          Main Navigation
        </span>
      </div>

      <nav className="flex-1 px-space-md space-y-1 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-space-md px-space-md py-space-sm rounded-lg transition-all ${
                isActive
                  ? 'bg-primary-container text-on-primary font-label-lg shadow-sm'
                  : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
              }`
            }
          >
            <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
            <span className="font-label-lg text-label-lg">{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
