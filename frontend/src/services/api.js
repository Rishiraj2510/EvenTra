import { initialEvents } from '../mocks/events.js'
import { demoUser } from '../mocks/users.js'
import { initialTasks } from '../mocks/tasks.js'
import { initialVolunteers } from '../mocks/volunteers.js'
import { createEmptyDashboardSummary } from '../mocks/dashboard.js'
import { initialPlans, createEmptyPlan, computePlanSummary } from '../mocks/plan.js'
import { calculateFitScore } from '../utils/scoring.js'

export const USE_MOCKS = true

// In-memory store for mocks during session
let mockEventsStore = [...initialEvents]
let mockTasksStore = [...initialTasks]
let mockVolunteersStore = [...initialVolunteers]
let mockPlansStore = initialPlans.map((p) => ({
  ...p,
  assignments: p.assignments.map((a) => ({ ...a })),
  skillGaps: p.skillGaps.map((sg) => ({ ...sg })),
}))

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

export async function getVolunteers(eventId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    if (!eventId) {
      return [...mockVolunteersStore]
    }
    return mockVolunteersStore.filter((v) => v.eventId === eventId)
  }

  const url = eventId ? `/api/volunteers?eventId=${encodeURIComponent(eventId)}` : '/api/volunteers'
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Failed to fetch volunteers: ${response.statusText}`)
  }
  return response.json()
}

export async function updateEventFormLink(eventId, registrationFormUrl) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 250))
    const index = mockEventsStore.findIndex((e) => e.id === eventId)
    if (index === -1) {
      throw new Error(`Event with ID ${eventId} not found`)
    }
    const updated = {
      ...mockEventsStore[index],
      registrationFormUrl: registrationFormUrl || '',
    }
    mockEventsStore[index] = updated
    return updated
  }

  const response = await fetch(`/api/events/${eventId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ registrationFormUrl }),
  })
  if (!response.ok) {
    throw new Error(`Failed to update registration form link: ${response.statusText}`)
  }
  return response.json()
}

export async function getDashboardSummary(eventId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    const summary = createEmptyDashboardSummary()
    if (!eventId) {
      return summary
    }

    const volunteers = mockVolunteersStore.filter((v) => v.eventId === eventId)
    const tasks = mockTasksStore.filter((t) => t.eventId === eventId)
    const currentPlan = mockPlansStore.find((p) => p.eventId === eventId)

    const totalHeadcountRequired = tasks.reduce((sum, t) => sum + (Number(t.headcount) || 1), 0)
    const headcountFilled = currentPlan?.status && currentPlan.status !== 'none'
      ? (currentPlan.summary?.headcountFilled ?? currentPlan.assignments.length)
      : null
    const openSkillGaps = currentPlan?.status && currentPlan.status !== 'none'
      ? (currentPlan.summary?.skillGapCount ?? currentPlan.skillGaps.length)
      : null

    return {
      ...summary,
      volunteersRegistered: volunteers.length,
      activeTasks: tasks.length,
      headcountFilled,
      headcountRequired: totalHeadcountRequired > 0 ? totalHeadcountRequired : null,
      openSkillGaps,
      alerts: [],
      planStatus: currentPlan?.status || 'none',
      workloadPerVolunteer: [],
    }
  }

  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/dashboard`)
  if (!response.ok) {
    throw new Error(`Failed to fetch dashboard summary: ${response.statusText}`)
  }
  return response.json()
}

export async function getPlan(eventId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    if (!eventId) return null
    let plan = mockPlansStore.find((p) => p.eventId === eventId)
    if (!plan) {
      plan = createEmptyPlan(eventId)
      mockPlansStore.push(plan)
    }
    return JSON.parse(JSON.stringify(plan))
  }

  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/plan`)
  if (!response.ok) {
    throw new Error(`Failed to fetch assignment plan: ${response.statusText}`)
  }
  return response.json()
}

export async function runSolver(eventId) {
  if (USE_MOCKS) {
    // Simulate 1.5 second delay with loading state
    await new Promise((resolve) => setTimeout(resolve, 1500))
    if (!eventId) {
      throw new Error('Event ID is required to run the solver')
    }

    let planIndex = mockPlansStore.findIndex((p) => p.eventId === eventId)
    const tasks = mockTasksStore.filter((t) => t.eventId === eventId)
    const volunteers = mockVolunteersStore.filter((v) => v.eventId === eventId)

    // Build fresh assignments from tasks and volunteers
    const sampleBreakdowns = [
      { skill: 0.95, experience: 0.90, performance: 0.85, load: 0.80, interest: 0.90 },
      { skill: 0.92, experience: 0.75, performance: 0.85, load: 0.90, interest: 0.80 },
      { skill: 0.85, experience: 0.70, performance: 0.75, load: 0.85, interest: 0.70 },
      { skill: 0.65, experience: 0.40, performance: 0.60, load: 0.70, interest: 0.50 },
    ]

    const assignments = tasks.slice(0, Math.min(tasks.length, volunteers.length)).map((t, i) => {
      const v = volunteers[i]
      const breakdown = sampleBreakdowns[i % sampleBreakdowns.length]
      const isFlagged = i === tasks.length - 1 || i === 3
      return {
        id: `asg-${eventId}-${i + 1}`,
        taskId: t.id,
        taskTitle: t.title,
        volunteerId: v.id,
        volunteerName: v.fullName,
        breakdown,
        fit: calculateFitScore(breakdown),
        reason: `Matched based on ${v.department} background and schedule availability.`,
        flags: isFlagged ? ['First-timer'] : [],
        locked: false,
      }
    })

    const skillGaps = [
      {
        taskId: tasks[0]?.id || 'tsk-1',
        taskTitle: tasks[0]?.title || 'Task Support',
        missingSkill: tasks[0]?.mandatorySkills?.[0] || 'First Aid Certified',
        slotsUnfilled: 1,
      },
    ]

    const totalHeadcount = tasks.reduce((sum, t) => sum + (Number(t.headcount) || 1), 0)
    const updatedPlan = {
      eventId,
      status: 'draft',
      summary: computePlanSummary(assignments, skillGaps, totalHeadcount || 14),
      assignments,
      skillGaps,
    }

    if (planIndex !== -1) {
      mockPlansStore[planIndex] = updatedPlan
    } else {
      mockPlansStore.push(updatedPlan)
    }

    return JSON.parse(JSON.stringify(updatedPlan))
  }

  const response = await fetch('/api/plan/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ eventId }),
  })
  if (!response.ok) {
    throw new Error(`Solver execution failed: ${response.statusText}`)
  }
  return response.json()
}

export async function approvePlan(eventId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const planIndex = mockPlansStore.findIndex((p) => p.eventId === eventId)
    if (planIndex === -1) {
      throw new Error(`Plan for event ${eventId} not found`)
    }
    if (mockPlansStore[planIndex].status !== 'draft') {
      throw new Error('Only draft plans can be approved')
    }
    mockPlansStore[planIndex] = {
      ...mockPlansStore[planIndex],
      status: 'approved',
    }
    return JSON.parse(JSON.stringify(mockPlansStore[planIndex]))
  }

  const response = await fetch('/api/plan/approve', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ eventId }),
  })
  if (!response.ok) {
    throw new Error(`Plan approval failed: ${response.statusText}`)
  }
  return response.json()
}

export async function swapAssignment(planId, assignmentId, volunteerId) {
  if (USE_MOCKS) {
    await new Promise((resolve) => setTimeout(resolve, 300))
    // planId is either the plan eventId or plan object id
    let plan = mockPlansStore.find((p) => p.eventId === planId || p.id === planId)
    if (!plan) {
      // fallback to plan containing this assignment
      plan = mockPlansStore.find((p) => p.assignments.some((a) => a.id === assignmentId))
    }
    if (!plan) {
      throw new Error(`Plan not found for assignment ${assignmentId}`)
    }

    const asgIndex = plan.assignments.findIndex((a) => a.id === assignmentId)
    if (asgIndex === -1) {
      throw new Error(`Assignment ${assignmentId} not found`)
    }

    const newVolunteer = mockVolunteersStore.find((v) => v.id === volunteerId)
    if (!newVolunteer) {
      throw new Error(`Volunteer ${volunteerId} not found`)
    }

    // Compute updated breakdown & fit for newly assigned volunteer
    const currentAsg = plan.assignments[asgIndex]
    const updatedBreakdown = {
      skill: Math.min(0.98, Math.max(0.6, (currentAsg.breakdown.skill + 0.05) % 1 || 0.8)),
      experience: Math.min(0.95, (newVolunteer.pastEventsCount || 1) * 0.2),
      performance: 0.85,
      load: 0.85,
      interest: 0.75,
    }

    const updatedAssignment = {
      ...currentAsg,
      volunteerId: newVolunteer.id,
      volunteerName: newVolunteer.fullName,
      breakdown: updatedBreakdown,
      fit: calculateFitScore(updatedBreakdown),
      reason: `Manual reassignment to ${newVolunteer.fullName} (${newVolunteer.department}).`,
    }

    plan.assignments[asgIndex] = updatedAssignment
    plan.summary = computePlanSummary(plan.assignments, plan.skillGaps, plan.summary.headcountRequired)

    return JSON.parse(JSON.stringify(plan))
  }

  const response = await fetch(`/api/plan/assignments/${encodeURIComponent(assignmentId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ volunteerId, planId }),
  })
  if (!response.ok) {
    throw new Error(`Failed to swap assignment: ${response.statusText}`)
  }
  return response.json()
}


