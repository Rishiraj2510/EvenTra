import { initialEvents } from '../mocks/events.js'
import { demoUser } from '../mocks/users.js'

export const USE_MOCKS = true

// In-memory store for mocks during session
let mockEventsStore = [...initialEvents]

export async function login(email, password) {
  if (!email || !email.trim()) {
    throw new Error('Email address is required')
  }
  if (!password || !password.trim()) {
    throw new Error('Password is required')
  }

  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 350))
    // Return a copy of demo user with provided email
    return {
      ...demoUser,
      email: email.trim(),
    }
  }

  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email: email.trim(), password }),
  })

  if (!response.ok) {
    let errorMsg = `Login failed: ${response.statusText}`
    try {
      const errorData = await response.json()
      if (errorData?.message) errorMsg = errorData.message
    } catch {
      // Use fallback errorMsg
    }
    throw new Error(errorMsg)
  }

  return response.json()
}

export async function getEvents() {
  if (USE_MOCKS) {
    // Simulate brief network delay
    await new Promise((resolve) => setTimeout(resolve, 200))
    return [...mockEventsStore]
  }

  const response = await fetch('/api/events')
  if (!response.ok) {
    throw new Error(`Failed to fetch events: ${response.statusText}`)
  }
  return response.json()
}

export async function createEvent(eventData) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const newEvent = {
      id: `evt-${Date.now()}`,
      ...eventData,
      status: eventData.status || 'draft',
    }
    mockEventsStore = [newEvent, ...mockEventsStore]
    return newEvent
  }

  const response = await fetch('/api/events', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(eventData),
  })
  if (!response.ok) {
    throw new Error(`Failed to create event: ${response.statusText}`)
  }
  return response.json()
}
