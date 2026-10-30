import { useState, useEffect, useRef, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import {
  getAgentHistory,
  getAuditLog,
  sendAgentMessage,
  resolveApproval,
} from '../services/api'

export default function AiAssistantPage() {
  const { role } = useAuth()
  const { events, selectedEventId, setSelectedEventId, selectedEvent } = useEvent()

  // Chat message & audit state
  const [messages, setMessages] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [inputText, setInputText] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [resolvingApprovalId, setResolvingApprovalId] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [lastFailedMessage, setLastFailedMessage] = useState(null)

  const messagesEndRef = useRef(null)

  // Auto-scroll to bottom of chat
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, isSending])

  // Load chat history & audit log when event changes
  useEffect(() => {
    let ignore = false
    if (!selectedEventId) {
      Promise.resolve().then(() => {
        if (!ignore) {
          setMessages([])
          setAuditLogs([])
        }
      })
      return () => {
        ignore = true
      }
    }

    Promise.all([getAgentHistory(selectedEventId), getAuditLog(selectedEventId)])
      .then(([history, audit]) => {
        if (!ignore) {
          setMessages(history || [])
          setAuditLogs(audit || [])
          setErrorMsg('')
        }
      })
      .catch((err) => {
        if (!ignore) {
          setErrorMsg(err.message || 'Failed to load conversation history')
        }
      })

    return () => {
      ignore = true
    }
  }, [selectedEventId])

  // Handle message sending
  const handleSend = useCallback(async (textToSend) => {
    const text = (textToSend || inputText).trim()
    if (!text || isSending || !selectedEventId) return

    setInputText('')
    setErrorMsg('')
    setLastFailedMessage(null)

    // Optimistically append user message
    const tempUserMsg = {
      id: `temp-${Math.random().toString(36).substring(2, 9)}`,
      role: 'user',
      text,
      createdAt: new Date().toISOString(),
      toolCalls: [],
      approval: null,
    }
    setMessages((prev) => [...prev, tempUserMsg])
    setIsSending(true)

    try {
      const assistantMsg = await sendAgentMessage(selectedEventId, text)
      setMessages((prev) => [...prev.filter((m) => m.id !== tempUserMsg.id), tempUserMsg, assistantMsg])
      // Refresh audit logs
      const updatedAudit = await getAuditLog(selectedEventId)
      setAuditLogs(updatedAudit || [])
    } catch (err) {
      setErrorMsg(err.message || 'Failed to send message')
      setLastFailedMessage(text)
    } finally {
      setIsSending(false)
    }
  }, [inputText, isSending, selectedEventId])

  // Handle keypress (Enter to send, Shift+Enter for new line)
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  // Handle human approval resolution
  const handleApproval = async (approvalId, decision) => {
    if (!approvalId || resolvingApprovalId) return
    setResolvingApprovalId(approvalId)
    setErrorMsg('')

    try {
      await resolveApproval(approvalId, decision)
      // Update local message approval status
      setMessages((prev) =>
        prev.map((msg) => {
          if (msg.approval && msg.approval.id === approvalId) {
            return {
              ...msg,
              approval: { ...msg.approval, status: decision },
            }
          }
          return msg
        })
      )
      // Append an assistant follow-up note into chat
      const followUpText =
        decision === 'approved'
          ? 'Action approved and committed to the workspace successfully.'
          : 'Action was rejected. No changes were made.'
      setMessages((prev) => [
        ...prev,
        {
          id: `msg-${Math.random().toString(36).substring(2, 9)}`,
          role: 'assistant',
          text: followUpText,
          createdAt: new Date().toISOString(),
          toolCalls: [],
          approval: null,
        },
      ])
      // Refresh audit logs
      const updatedAudit = await getAuditLog(selectedEventId)
      setAuditLogs(updatedAudit || [])
    } catch (err) {
      setErrorMsg(err.message || 'Failed to process approval')
    } finally {
      setResolvingApprovalId(null)
    }
  }

  // Example prompt chip click
  const handlePromptChipClick = (prompt) => {
    handleSend(prompt)
  }

  const exampleChips = [
    'We need 3 people for stage management on Friday evening',
    'Run the solver for this event',
    'Explain the first assignment',
    'A volunteer dropped out, find a replacement',
  ]

  // Redirect volunteers away (role protection: organiser/coordinator only)
  if (role === 'volunteer') {
    return <Navigate to="/events" replace />
  }

  return (
    <div className="flex flex-col w-full">
      <div className="p-margin-lg flex flex-col gap-space-lg">
        {/* Top Header Banner */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="flex flex-col min-w-0">
            <div className="flex items-center gap-space-sm">
              <div className="w-8 h-8 rounded-lg bg-primary-fixed flex items-center justify-center text-primary">
                <span className="material-symbols-outlined text-[20px]">smart_toy</span>
              </div>
              <h1 className="font-headline-md text-headline-md text-on-surface leading-tight">
                AI Assistant
              </h1>
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
              Plan and adjust volunteer assignments with conversational AI assistance.
            </p>
          </div>
        </div>

        {/* Empty state: No event selected */}
        {!selectedEventId && (
          <section className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-16 flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">event_available</span>
            </div>
            <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight mb-space-xs">
              Select an Event
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xl">
              Choose an event from the topbar or select one below to start collaborating with the AI assistant.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-space-sm max-w-lg">
              {events?.map((evt) => (
                <button
                  key={evt.id}
                  onClick={() => setSelectedEventId(evt.id)}
                  className="px-space-md py-space-xs rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer border border-outline-variant/30"
                >
                  {evt.name}
                </button>
              ))}
            </div>
          </section>
        )}

        {/* Main 2-Column Workspace when event is selected */}
        {selectedEventId && (
          <div className="flex flex-col lg:flex-row gap-gutter-lg items-start w-full">
            {/* Left / Center Chat Workspace */}
            <section className="flex-1 min-w-0 w-full flex flex-col bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden border border-surface-container/60">
              {/* Event Scope Indicator Sub-bar */}
              <div className="px-space-xl py-space-md bg-surface-container-low/50 flex items-center justify-between border-b border-surface-container/60">
                <div className="flex items-center gap-space-sm min-w-0">
                  <span className="w-2 h-2 rounded-full bg-secondary" />
                  <span className="font-label-sm text-label-sm text-on-surface-variant truncate">
                    Active Scope:{' '}
                    <span className="font-medium text-on-surface">
                      {selectedEvent ? selectedEvent.name : 'Selected Event'}
                    </span>
                  </span>
                </div>
              </div>

              {/* Error Banner with Retry */}
              {errorMsg && (
                <div className="bg-error-container text-on-error-container p-3 px-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[18px]">error</span>
                    <span className="font-body-sm text-body-sm">{errorMsg}</span>
                  </div>
                  {lastFailedMessage && (
                    <button
                      onClick={() => handleSend(lastFailedMessage)}
                      className="px-3 py-1 bg-surface-container-lowest text-on-surface rounded-lg font-label-sm text-label-sm shadow-sm cursor-pointer hover:opacity-90"
                    >
                      Retry
                    </button>
                  )}
                </div>
              )}

              {/* Message Stream */}
              <div className="p-space-lg flex flex-col gap-space-lg min-h-[520px] max-h-[640px] overflow-y-auto">
                {/* Empty Chat / Welcome State with Prompt Chips */}
                {messages.length === 0 && !isSending && (
                  <div className="flex flex-col items-center justify-center py-space-xl text-center max-w-lg mx-auto my-auto">
                    <div className="w-16 h-16 rounded-2xl bg-primary-fixed text-primary flex items-center justify-center mb-space-md shadow-sm">
                      <span className="material-symbols-outlined text-[36px]">smart_toy</span>
                    </div>
                    <h2 className="font-headline-md text-headline-md text-on-surface">
                      Eventra AI Assistant
                    </h2>
                    <p className="font-body-md text-body-md text-on-surface-variant mt-space-xs max-w-md">
                      Ask me to plan volunteers, replace someone, or check for skill gaps.
                    </p>
                    <div className="flex flex-wrap items-center justify-center gap-space-sm mt-space-lg">
                      {exampleChips.map((chip, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => handlePromptChipClick(chip)}
                          className="px-3.5 py-1.5 rounded-full bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors flex items-center gap-1.5 shadow-xs cursor-pointer border border-surface-container"
                        >
                          <span className="material-symbols-outlined text-primary text-[16px]">
                            auto_awesome
                          </span>
                          <span>{chip}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* Render Messages */}
                {messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex gap-space-md items-start ${
                      msg.role === 'user' ? 'justify-end' : 'justify-start'
                    }`}
                  >
                    {msg.role === 'assistant' && (
                      <div className="w-8 h-8 rounded-lg bg-primary-container text-on-primary flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                        <span className="material-symbols-outlined text-[18px]">smart_toy</span>
                      </div>
                    )}

                    <div
                      className={`flex flex-col gap-space-xs max-w-2xl ${
                        msg.role === 'user' ? 'items-end' : 'items-start'
                      }`}
                    >
                      <div className="flex items-center gap-space-sm mb-1 px-1">
                        <span className="font-label-md text-label-md text-on-surface font-semibold">
                          {msg.role === 'user' ? 'You' : 'Eventra Assistant'}
                        </span>
                        <span className="text-on-surface-variant font-label-sm text-label-sm">
                          {new Date(msg.createdAt).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>

                      {/* Message Bubble */}
                      <div
                        className={`rounded-2xl p-space-md ${
                          msg.role === 'user'
                            ? 'bg-primary-container text-on-primary rounded-tr-sm'
                            : 'bg-surface-container-low text-on-surface rounded-tl-sm'
                        }`}
                      >
                        <p className="font-body-md text-body-md whitespace-pre-wrap leading-relaxed">
                          {msg.text}
                        </p>
                      </div>

                      {/* Tool Calls inside assistant message */}
                      {msg.toolCalls && msg.toolCalls.length > 0 && (
                        <div className="flex flex-col gap-2 w-full mt-2">
                          {msg.toolCalls.map((tc) => (
                            <div
                              key={tc.id}
                              className="p-3 rounded-xl bg-surface-container-low/70 border border-surface-container flex items-start gap-3 text-on-surface"
                            >
                              <div className="mt-0.5 text-primary">
                                {tc.status === 'running' ? (
                                  <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                                ) : tc.status === 'done' ? (
                                  <span className="material-symbols-outlined text-secondary text-[18px]">
                                    check_circle
                                  </span>
                                ) : (
                                  <span className="material-symbols-outlined text-error text-[18px]">
                                    error
                                  </span>
                                )}
                              </div>
                              <div className="flex flex-col min-w-0 flex-1">
                                <div className="flex items-center justify-between">
                                  <span className="font-mono text-label-sm font-semibold text-primary">
                                    {tc.tool}
                                  </span>
                                  <span className="font-label-sm text-outline capitalize">
                                    {tc.status}
                                  </span>
                                </div>
                                {tc.resultSummary && (
                                  <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
                                    {tc.resultSummary}
                                  </p>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Approval Card (if attached to message) */}
                      {msg.approval && (
                        <div className="w-full mt-2 p-space-lg rounded-xl bg-surface-container-lowest border border-surface-container shadow-md flex flex-col gap-space-md">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-space-sm">
                              <span className="material-symbols-outlined text-primary text-[20px]">
                                verified_user
                              </span>
                              <span className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                                Approval Required
                              </span>
                            </div>
                            <span
                              className={`px-2 py-0.5 rounded-full font-label-sm text-label-sm capitalize font-medium ${
                                msg.approval.status === 'approved'
                                  ? 'bg-secondary-container text-secondary'
                                  : msg.approval.status === 'rejected'
                                  ? 'bg-error-container text-on-error-container'
                                  : 'bg-tertiary-fixed text-on-tertiary-fixed-variant'
                              }`}
                            >
                              {msg.approval.status}
                            </span>
                          </div>

                          <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                            {msg.approval.description}
                          </p>

                          {/* Approval Actions (only if pending) */}
                          {msg.approval.status === 'pending' ? (
                            <div className="flex items-center justify-end gap-space-sm pt-space-xs">
                              <button
                                type="button"
                                onClick={() => handleApproval(msg.approval.id, 'rejected')}
                                disabled={resolvingApprovalId === msg.approval.id}
                                className="px-4 py-2 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md cursor-pointer transition-colors disabled:opacity-50"
                              >
                                Reject
                              </button>
                              <button
                                type="button"
                                onClick={() => handleApproval(msg.approval.id, 'approved')}
                                disabled={resolvingApprovalId === msg.approval.id}
                                className="px-5 py-2 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md cursor-pointer transition-all shadow-sm flex items-center gap-1.5 disabled:opacity-50"
                              >
                                <span className="material-symbols-outlined text-[18px]">
                                  check_circle
                                </span>
                                <span>
                                  {resolvingApprovalId === msg.approval.id
                                    ? 'Committing...'
                                    : 'Approve & Commit'}
                                </span>
                              </button>
                            </div>
                          ) : (
                            <div className="pt-space-xs text-right">
                              <span className="font-label-sm text-label-sm text-outline italic">
                                Decision finalized: {msg.approval.status}
                              </span>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    {msg.role === 'user' && (
                      <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-on-primary shadow-sm shrink-0 mt-0.5">
                        <span className="material-symbols-outlined text-[18px]">person</span>
                      </div>
                    )}
                  </div>
                ))}

                {/* Assistant Thinking Indicator */}
                {isSending && (
                  <div className="flex gap-space-md items-start">
                    <div className="w-8 h-8 rounded-lg bg-primary-container text-on-primary flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                      <span className="material-symbols-outlined text-[18px]">smart_toy</span>
                    </div>
                    <div className="flex flex-col gap-1 items-start">
                      <span className="font-label-sm text-label-sm text-outline px-1">
                        Eventra Assistant is thinking...
                      </span>
                      <div className="rounded-2xl rounded-tl-sm p-4 bg-surface-container-low flex items-center gap-2">
                        <div className="w-2 h-2 rounded-full bg-primary animate-bounce" />
                        <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.2s]" />
                        <div className="w-2 h-2 rounded-full bg-primary animate-bounce [animation-delay:0.4s]" />
                      </div>
                    </div>
                  </div>
                )}

                <div ref={messagesEndRef} />
              </div>

              {/* Chat Input Bar */}
              <div className="p-space-lg bg-surface-container-low/60 border-t border-surface-container/60">
                <form
                  onSubmit={(e) => {
                    e.preventDefault()
                    handleSend()
                  }}
                  className="flex flex-col gap-space-xs"
                >
                  <div className="relative flex items-center bg-surface-container-lowest rounded-xl shadow-sm px-space-md py-2 focus-within:ring-2 focus-within:ring-primary/20 border border-surface-container/60">
                    <textarea
                      value={inputText}
                      onChange={(e) => setInputText(e.target.value)}
                      onKeyDown={handleKeyDown}
                      rows={1}
                      disabled={isSending}
                      placeholder="Ask Eventra Assistant or type a command... (Enter to send)"
                      className="w-full bg-transparent px-space-sm py-1.5 text-on-surface placeholder:text-outline focus:outline-none font-body-md text-body-md resize-none"
                    />

                    <button
                      type="submit"
                      disabled={!inputText.trim() || isSending}
                      className="h-9 px-4 rounded-lg bg-primary hover:bg-primary-container text-on-primary transition-all flex items-center justify-center gap-1 shadow-sm font-label-md text-label-md ml-2 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
                    >
                      <span className="material-symbols-outlined text-[18px]">send</span>
                      <span className="hidden sm:inline">Send</span>
                    </button>
                  </div>
                  <div className="px-space-xs text-on-surface-variant font-label-sm text-label-sm mt-1">
                    Press <kbd className="px-1.5 py-0.5 rounded bg-surface-container font-mono text-[10px]">Enter</kbd> to send, <kbd className="px-1.5 py-0.5 rounded bg-surface-container font-mono text-[10px]">Shift+Enter</kbd> for a new line.
                  </div>
                </form>
              </div>
            </section>

            {/* Right Panel: Audit Log & Tool Trace */}
            <aside className="w-full lg:w-[380px] shrink-0 bg-surface-container-lowest rounded-xl shadow-sm flex flex-col border border-surface-container/60 overflow-hidden sticky top-20">
              {/* Panel Header */}
              <div className="px-space-lg py-4 bg-surface-container-low/60 border-b border-surface-container/60 flex items-center justify-between">
                <div className="flex items-center gap-space-sm">
                  <span className="material-symbols-outlined text-outline text-[20px]">
                    history_edu
                  </span>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Tool Audit Log
                  </h3>
                </div>
                <span className="px-2 py-0.5 rounded-full bg-surface-container text-on-surface font-label-sm text-label-sm font-semibold">
                  {auditLogs.length}
                </span>
              </div>

              {/* Panel Body */}
              <div className="flex flex-col p-space-md gap-space-sm max-h-[600px] overflow-y-auto">
                {auditLogs.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-space-xl text-center px-space-md">
                    <div className="w-12 h-12 rounded-xl bg-surface-container-low text-outline flex items-center justify-center mb-space-sm">
                      <span className="material-symbols-outlined text-[24px]">terminal</span>
                    </div>
                    <p className="font-body-md text-body-md text-on-surface font-medium">No activity yet</p>
                    <p className="font-body-sm text-body-sm text-on-surface-variant mt-1 max-w-[240px]">
                      Tool execution traces and human approval decisions will appear here.
                    </p>
                  </div>
                ) : (
                  <div className="flex flex-col gap-space-sm relative">
                    <div className="absolute left-4 top-4 bottom-4 w-0.5 bg-surface-container" />
                    {auditLogs.map((entry) => (
                      <div key={entry.id} className="relative flex items-start gap-space-md">
                        <div
                          className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10 shadow-xs text-on-primary ${
                            entry.status === 'committed' || entry.status === 'success'
                              ? 'bg-secondary'
                              : entry.status === 'rejected'
                              ? 'bg-error'
                              : 'bg-primary'
                          }`}
                        >
                          <span className="material-symbols-outlined text-[16px]">
                            {entry.status === 'committed' || entry.status === 'success'
                              ? 'check'
                              : entry.status === 'rejected'
                              ? 'close'
                              : 'smart_toy'}
                          </span>
                        </div>

                        <div className="flex flex-col gap-0.5 flex-1 min-w-0 bg-surface-container-low p-space-sm rounded-lg border border-surface-container/60">
                          <div className="flex items-center justify-between">
                            <span className="font-mono font-label-md text-label-md text-primary font-medium truncate">
                              {entry.tool}
                            </span>
                            <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0">
                              {new Date(entry.createdAt).toLocaleTimeString([], {
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit',
                              })}
                            </span>
                          </div>

                          <div className="flex items-center justify-between mt-1">
                            <span
                              className={`font-label-sm text-label-sm uppercase font-semibold ${
                                entry.status === 'committed' || entry.status === 'success'
                                  ? 'text-secondary'
                                  : entry.status === 'rejected'
                                  ? 'text-error'
                                  : 'text-outline'
                              }`}
                            >
                              {entry.status}
                            </span>
                            {entry.approvedBy && (
                              <span className="font-body-sm text-body-sm text-outline">
                                By {entry.approvedBy}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </aside>
          </div>
        )}
      </div>
    </div>
  )
}
