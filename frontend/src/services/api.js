import { initialEvents } from '../mocks/events.js'
import { demoUser } from '../mocks/users.js'
import { initialTasks } from '../mocks/tasks.js'

export const USE_MOCKS = true

// In-memory store for mocks during session
let mockEventsStore = [...initialEvents]
let mockTasksStore = [...initialTasks]

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

export async function getTasks(eventId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    if (!eventId) {
      return [...mockTasksStore]
    }
    return mockTasksStore.filter((t) => t.eventId === eventId)
  }

  const url = eventId ? `/api/tasks?eventId=${encodeURIComponent(eventId)}` : '/api/tasks'
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Failed to fetch tasks: ${response.statusText}`)
  }
  return response.json()
}

export async function createTask(taskData) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const newTask = {
      id: `tsk-${Date.now()}`,
      status: 'pending',
      ...taskData,
      mandatorySkills: Array.isArray(taskData.mandatorySkills) ? taskData.mandatorySkills : [],
      preferredSkills: Array.isArray(taskData.preferredSkills) ? taskData.preferredSkills : [],
      critical: Boolean(taskData.critical),
      headcount: Number(taskData.headcount) || 1,
    }
    mockTasksStore = [newTask, ...mockTasksStore]
    return newTask
  }

  const response = await fetch('/api/tasks', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(taskData),
  })
  if (!response.ok) {
    throw new Error(`Failed to create task: ${response.statusText}`)
  }
  return response.json()
}

export async function updateTask(id, taskData) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 250))
    const index = mockTasksStore.findIndex((t) => t.id === id)
    if (index === -1) {
      throw new Error(`Task with ID ${id} not found`)
    }
    const updated = {
      ...mockTasksStore[index],
      ...taskData,
      mandatorySkills: Array.isArray(taskData.mandatorySkills) ? taskData.mandatorySkills : mockTasksStore[index].mandatorySkills,
      preferredSkills: Array.isArray(taskData.preferredSkills) ? taskData.preferredSkills : mockTasksStore[index].preferredSkills,
      critical: taskData.critical !== undefined ? Boolean(taskData.critical) : mockTasksStore[index].critical,
      headcount: taskData.headcount !== undefined ? Number(taskData.headcount) : mockTasksStore[index].headcount,
    }
    mockTasksStore[index] = updated
    return updated
  }

  const response = await fetch(`/api/tasks/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(taskData),
  })
  if (!response.ok) {
    throw new Error(`Failed to update task: ${response.statusText}`)
  }
  return response.json()
}

export async function deleteTask(id) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    const index = mockTasksStore.findIndex((t) => t.id === id)
    if (index === -1) {
      throw new Error(`Task with ID ${id} not found`)
    }
    mockTasksStore = mockTasksStore.filter((t) => t.id !== id)
    return { success: true, id }
  }

  const response = await fetch(`/api/tasks/${id}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(`Failed to delete task: ${response.statusText}`)
  }
  return response.json()
}
