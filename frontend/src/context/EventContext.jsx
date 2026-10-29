import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react'
import { getEvents, updateEventFormLink as apiUpdateEventFormLink } from '../services/api'
import { useAuth } from './AuthContext'

const EventContext = createContext(null)

export function EventProvider({ children }) {
  const { selectedEventId, setSelectedEventId } = useAuth()
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchEvents = useCallback(async () => {
    try {
      setLoading(true)
      const data = await getEvents()
      setEvents(data)
      setError(null)
      return data
    } catch (err) {
      setError(err.message || 'Failed to fetch events')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let ignore = false
    getEvents()
      .then((data) => {
        if (!ignore) {
          setEvents(data)
          setError(null)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!ignore) {
          setError(err.message || 'Failed to fetch events')
          setLoading(false)
        }
      })
    return () => {
      ignore = true
    }
  }, [])

  const selectedEvent = useMemo(() => {
    return events.find((e) => e.id === selectedEventId) || null
  }, [events, selectedEventId])

  const updateRegistrationLink = useCallback(async (eventId, url) => {
    const updated = await apiUpdateEventFormLink(eventId, url)
    // Update local events state immediately
    setEvents((prev) =>
      prev.map((evt) => (evt.id === eventId ? { ...evt, registrationFormUrl: url } : evt))
    )
    return updated
  }, [])

  return (
    <EventContext.Provider
      value={{
        events,
        selectedEventId,
        setSelectedEventId,
        selectedEvent,
        loading,
        error,
        refreshEvents: fetchEvents,
        updateRegistrationLink,
      }}
    >
      {children}
    </EventContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useEvent() {
  const context = useContext(EventContext)
  if (!context) {
    throw new Error('useEvent must be used within an EventProvider')
  }
  return context
}
