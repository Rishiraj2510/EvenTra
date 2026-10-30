/**
 * Mock data structures for the AI Agent.
 *
 * Tool names:
 * 'get_event', 'list_tasks', 'list_volunteers', 'parse_task_request',
 * 'extract_skills', 'check_clashes', 'run_solver', 'explain_assignment',
 * 'find_replacement', 'find_skill_gaps', 'create_task', 'update_task',
 * 'publish_plan', 'send_notifications'
 */

export const ALLOWED_TOOLS = [
  'get_event',
  'list_tasks',
  'list_volunteers',
  'parse_task_request',
  'extract_skills',
  'check_clashes',
  'run_solver',
  'explain_assignment',
  'find_replacement',
  'find_skill_gaps',
  'create_task',
  'update_task',
  'publish_plan',
  'send_notifications',
]

/**
 * Creates a new ToolCall object
 */
export function createToolCall(tool, args = {}, status = 'done', resultSummary = '') {
  return {
    id: `tool-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    tool,
    args,
    status, // "running" | "done" | "error"
    resultSummary,
    createdAt: new Date().toISOString(),
  }
}

/**
 * Creates an Approval object
 */
export function createApproval(action, description, payload) {
  return {
    id: `appr-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    action, // 'create_task' | 'update_task' | 'swap' | 'publish_plan' | 'send_notifications'
    description,
    payload,
    status: 'pending', // 'pending' | 'approved' | 'rejected'
  }
}

/**
 * Creates an AuditEntry object
 */
export function createAuditEntry(tool, args = {}, status = 'success', approvedBy = null) {
  return {
    id: `audit-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    tool,
    args,
    status,
    createdAt: new Date().toISOString(),
    approvedBy,
  }
}

/**
 * Creates a Message object
 */
export function createMessage(role, text, toolCalls = [], approval = null) {
  return {
    id: `msg-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    role, // "user" | "assistant"
    text,
    createdAt: new Date().toISOString(),
    toolCalls,
    approval,
  }
}
