import { createContext, useContext, useState } from 'react'
import { useNavigate } from 'react-router-dom'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [user, setUser] = useState(null) // null when logged out
  const [role, setRole] = useState('organiser') // 'organiser' | 'volunteer'
  const [selectedEventId, setSelectedEventId] = useState('') // selected event id for topbar picker

  const loginUser = (userData) => {
    const assignedRole = userData?.role || role || 'organiser'
    setRole(assignedRole)
    const userObj = {
      id: userData?.id || 'usr-1',
      name: userData?.name || 'Rishiraj Jhawar',
      email: userData?.email || 'rishiraj@college.edu',
      role: assignedRole,
      displayRole: assignedRole === 'organiser' ? 'Lead Organiser' : 'Student Volunteer',
      volunteerId: userData?.volunteerId || 'vol-1',
    }
    setUser(userObj)
    try {
      localStorage.setItem('eventra_user', JSON.stringify(userObj))
      sessionStorage.setItem('eventra_user', JSON.stringify(userObj))
    } catch {
      // storage unavailable or quota exceeded
    }
  }

  const logout = () => {
    setUser(null)
    setSelectedEventId('')
    try {
      localStorage.removeItem('eventra_user')
      sessionStorage.removeItem('eventra_user')
    } catch {
      // ignore
    }
    navigate('/login')
  }

  const logoutUser = () => {
    logout()
  }

  const toggleRole = () => {
    setRole((prevRole) => {
      const nextRole = prevRole === 'organiser' ? 'volunteer' : 'organiser'
      setUser((prevUser) => {
        if (!prevUser) return null
        const updated = {
          ...prevUser,
          role: nextRole,
          displayRole: nextRole === 'organiser' ? 'Lead Organiser' : 'Student Volunteer',
          volunteerId: prevUser.volunteerId || 'vol-1',
        }
        try {
          localStorage.setItem('eventra_user', JSON.stringify(updated))
          sessionStorage.setItem('eventra_user', JSON.stringify(updated))
        } catch {
          // ignore
        }
        return updated
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
        logout,
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

