/**
 * Mock certificates store.
 * Maps certificate id or volunteerId/eventId to certificate record:
 * {
 *   id: string,
 *   eventId: string,
 *   eventName: string,
 *   volunteerId: string,
 *   volunteerName: string,
 *   tasksCompleted: number,
 *   hours: number,
 *   issuedAt: string, // ISO date string
 * }
 */

export const mockCertificatesStore = []
