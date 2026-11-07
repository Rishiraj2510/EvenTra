const STORAGE_KEY = 'eventra_registration_forms_v1'

export const initialRegistrationForms = [
  {
    eventId: 'evt-1',
    status: 'open',
    opensAt: '2025-01-01T00:00:00.000Z',
    closesAt: '2027-12-31T23:59:59.000Z',
    maxResponses: 25,
    offeredSkills: [
      'Audio Board Operation',
      'Anchoring & Stage Ops',
      'Problem Solving',
      'Crowd Flow Control',
      'Registration & Badging',
      'VIP Hospitality',
      'First Aid Certified',
      'Crisis Management',
    ],
    extraQuestions: [
      { id: 'q1', label: 'Do you have previous experience handling sound systems or audio boards?' },
      { id: 'q2', label: 'T-Shirt Size for the Event Staff Jersey (S/M/L/XL/XXL)' },
    ],
    responseCount: 5,
  },
  {
    eventId: 'evt-2',
    status: 'draft',
    opensAt: '2025-03-01T09:00:00.000Z',
    closesAt: '2025-04-01T18:00:00.000Z',
    maxResponses: 30,
    offeredSkills: [
      'VIP Hospitality',
      'Public Speaking',
      'Bilingual',
      'Stage & Production',
      'Lighting Console',
      'Backstage Coordination',
    ],
    extraQuestions: [
      { id: 'q1', label: 'Which Indian regional languages or foreign languages are you fluent in?' },
    ],
    responseCount: 0,
  },
  {
    eventId: 'evt-3',
    status: 'closed',
    opensAt: '2025-01-15T00:00:00.000Z',
    closesAt: '2025-02-05T23:59:59.000Z',
    maxResponses: 20,
    offeredSkills: ['First Aid Certified', 'Crowd Flow Control', 'Logistics'],
    extraQuestions: [],
    responseCount: 0,
  },
]

export function loadRegistrationForms() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed
      }
    }
  } catch {
    // ignore storage read issues
  }
  return initialRegistrationForms.map((f) => ({
    ...f,
    offeredSkills: [...f.offeredSkills],
    extraQuestions: f.extraQuestions.map((q) => ({ ...q })),
  }))
}

export function saveRegistrationForms(forms) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(forms))
  } catch {
    // ignore storage write issues
  }
}

export function resetMockRegistrations() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
}

