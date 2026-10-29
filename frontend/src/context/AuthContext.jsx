import { createContext, useContext, useState } from 'react'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null) // null when logged out
  const [role, setRole] = useState('organiser') // 'organiser' | 'volunteer'
  const [selectedEventId, setSelectedEventId] = useState('') // selected event id for topbar picker

  const loginUser = (userData) => {
    const assignedRole = userData?.role || role || 'organiser'
    setRole(assignedRole)
    setUser({
      name: userData?.name || 'Rishiraj Jhawar',
      email: userData?.email || 'rishiraj@college.edu',
      role: assignedRole,
      displayRole: assignedRole === 'organiser' ? 'Lead Organiser' : 'Student Volunteer',
    })
  }

  const logoutUser = () => {
    setUser(null)
    setSelectedEventId('')
  }

  const toggleRole = () => {
    setRole((prevRole) => {
      const nextRole = prevRole === 'organiser' ? 'volunteer' : 'organiser'
      setUser((prevUser) => {
        if (!prevUser) return null
        return {
          ...prevUser,
          role: nextRole,
          displayRole: nextRole === 'organiser' ? 'Lead Organiser' : 'Student Volunteer',
        }
      })
      return nextRole
    })
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role || role,
        selectedEventId,
        setSelectedEventId,
        setUser,
        loginUser,
        logoutUser,
        setRole,
        toggleRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
