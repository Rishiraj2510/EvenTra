/**
 * Checks whether an event's registration form is effectively open.
 * A form is open only if:
 * 1. Form status is 'open'
 * 2. The current timestamp is within [opensAt, closesAt] (if defined)
 * 3. responseCount is less than maxResponses (if maxResponses is a positive integer)
 * 
 * @param {Object} form - The registration form object
 * @param {Date|string|number} [nowDate=new Date()] - Reference timestamp
 * @returns {boolean}
 */
export function isRegistrationOpen(form, nowDate = new Date()) {
  if (!form || form.status !== 'open') {
    return false
  }

  const now = new Date(nowDate).getTime()

  if (form.opensAt) {
    const opens = new Date(form.opensAt).getTime()
    if (!isNaN(opens) && now < opens) {
      return false
    }
  }

  if (form.closesAt) {
    const closes = new Date(form.closesAt).getTime()
    if (!isNaN(closes) && now > closes) {
      return false
    }
  }

  if (
    form.maxResponses !== null &&
    form.maxResponses !== undefined &&
    form.maxResponses !== ''
  ) {
    const max = Number(form.maxResponses)
    if (!isNaN(max) && max > 0) {
      const current = Number(form.responseCount || 0)
      if (current >= max) {
        return false
      }
    }
  }

  return true
}

/**
 * Computes a user-friendly status badge key for a registration form in the volunteer catalog.
 * Possible returns: 'Registered' | 'Open' | 'Full' | 'Closed'
 * 
 * @param {Object} form - The registration form
 * @param {boolean} isRegistered - Whether the current user already registered
 * @param {Date|string|number} [nowDate=new Date()]
 * @returns {'Registered'|'Open'|'Full'|'Closed'}
 */
export function getVolunteerEventFormStatus(form, isRegistered, nowDate = new Date()) {
  if (isRegistered) {
    return 'Registered'
  }

  if (!form || form.status === 'draft') {
    return 'Closed'
  }

  const now = new Date(nowDate).getTime()

  if (form.maxResponses) {
    const max = Number(form.maxResponses)
    const current = Number(form.responseCount || 0)
    if (!isNaN(max) && max > 0 && current >= max) {
      return 'Full'
    }
  }

  if (form.status === 'closed') {
    return 'Closed'
  }

  if (form.opensAt) {
    const opens = new Date(form.opensAt).getTime()
    if (!isNaN(opens) && now < opens) {
      return 'Closed'
    }
  }

  if (form.closesAt) {
    const closes = new Date(form.closesAt).getTime()
    if (!isNaN(closes) && now > closes) {
      return 'Closed'
    }
  }

  return 'Open'
}

