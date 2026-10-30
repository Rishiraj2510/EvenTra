import { useState, useCallback, useMemo } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useEvent } from '../context/EventContext'
import { getCertificates, issueCertificates, updateCertificateTemplate } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import CertificateCard from '../components/certificates/CertificateCard'

export default function CertificatesPage() {
  const { role } = useAuth()
  const { selectedEventId, selectedEvent, refreshEvents } = useEvent()

  // Active design mode tab: 'builtin' | 'upload'
  const [designTab, setDesignTab] = useState('builtin')

  // Track last synced event id to reset draft during render when selected event changes
  const [prevEventId, setPrevEventId] = useState(selectedEventId)
  const [customDraft, setCustomDraft] = useState(null)

  // Template save state
  const [isSavingTemplate, setIsSavingTemplate] = useState(false)
  const [saveSuccessNotification, setSaveSuccessNotification] = useState(false)
  const [templateError, setTemplateError] = useState(null)
  const [imageWarning, setImageWarning] = useState(null)

  // Remove custom design confirmation modal
  const [showRemoveConfirm, setShowRemoveConfirm] = useState(false)

  // Search query for volunteer list
  const [searchQuery, setSearchQuery] = useState('')

  // Confirmation modal states for issuing
  const [confirmModal, setConfirmModal] = useState({
    open: false,
    volunteerIds: [],
    volunteerName: '',
    isAll: false,
  })
  const [isIssuing, setIsIssuing] = useState(false)
  const [actionError, setActionError] = useState(null)

  // Preview modal state: { open: boolean, cert: { volunteerName, eventName, date, hours } }
  const [previewCert, setPreviewCert] = useState(null)

  // Current active template values
  const currentEventTemplate = useMemo(() => {
    return selectedEvent?.certificateTemplate || {
      type: 'builtin',
      templateId: 'classic',
      backgroundImage: '',
      namePosition: 'center',
      textColor: 'dark',
    }
  }, [selectedEvent])

  if (prevEventId !== selectedEventId) {
    setPrevEventId(selectedEventId)
    setCustomDraft(null)
    setDesignTab(selectedEvent?.certificateTemplate?.type === 'custom' ? 'upload' : 'builtin')
    setTemplateError(null)
    setImageWarning(null)
  }

  const templateDraft = customDraft || currentEventTemplate
  const setTemplateDraft = (updater) => {
    setCustomDraft((prev) => {
      const base = prev || currentEventTemplate
      return typeof updater === 'function' ? updater(base) : updater
    })
  }

  // Fetch certificates callback
  const fetchCertificatesCallback = useCallback(() => {
    return getCertificates(selectedEventId)
  }, [selectedEventId])

  const { data: certificates, loading, error, reload } = useFetch(fetchCertificatesCallback)

  // Filter rows by volunteer name
  const filteredRows = useMemo(() => {
    if (!certificates) return []
    if (!searchQuery.trim()) return certificates
    const q = searchQuery.toLowerCase()
    return certificates.filter((c) => c.volunteerName?.toLowerCase().includes(q))
  }, [certificates, searchQuery])

  // Count eligible and pending
  const eligibleVolunteers = useMemo(() => {
    if (!certificates) return []
    return certificates.filter((c) => c.status === 'not_issued')
  }, [certificates])

  // Ensure role guard is evaluated after all hooks
  if (role === 'volunteer') {
    return <Navigate to="/my-duties" replace />
  }

  // -----------------------------------------------------------------
  // TEMPLATE MANAGEMENT HANDLERS
  // -----------------------------------------------------------------

  const handleSelectBuiltin = (templateId) => {
    setTemplateDraft((prev) => ({
      ...prev,
      type: 'builtin',
      templateId,
    }))
    setTemplateError(null)
  }

  const handleFileUpload = (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    setTemplateError(null)
    setImageWarning(null)

    // Validate type (PNG or JPEG only)
    const validTypes = ['image/png', 'image/jpeg', 'image/jpg']
    if (!validTypes.includes(file.type)) {
      setTemplateError('Invalid file format. Please upload a PNG or JPG file.')
      return
    }

    // Validate size (max 5 MB = 5 * 1024 * 1024 bytes)
    const maxSize = 5 * 1024 * 1024
    if (file.size > maxSize) {
      setTemplateError('File size exceeds 5 MB. Please upload an image under 5 MB.')
      return
    }

    // Read as Data URL
    const reader = new FileReader()
    reader.onload = (uploadEvent) => {
      const dataUrl = uploadEvent.target?.result
      if (!dataUrl) return

      // Verify landscape orientation
      const img = new Image()
      img.onload = () => {
        if (img.width < img.height) {
          setImageWarning('The uploaded image is portrait. A landscape image is recommended for best certificate results.')
        } else {
          setImageWarning(null)
        }

        setTemplateDraft((prev) => ({
          ...prev,
          type: 'custom',
          backgroundImage: dataUrl,
          imageFile: file,
        }))
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(file)
  }

  const handleSaveDesign = async () => {
    if (!selectedEventId) return
    try {
      setIsSavingTemplate(true)
      setTemplateError(null)

      if (templateDraft.type === 'custom' && !templateDraft.backgroundImage) {
        setTemplateError('Please upload an image before saving custom design.')
        setIsSavingTemplate(false)
        return
      }

      await updateCertificateTemplate(selectedEventId, templateDraft)
      await refreshEvents()

      setSaveSuccessNotification(true)
      setTimeout(() => setSaveSuccessNotification(false), 2500)
    } catch (err) {
      setTemplateError(err.message || 'Failed to save certificate design.')
    } finally {
      setIsSavingTemplate(false)
    }
  }

  const handleConfirmRemoveCustom = async () => {
    if (!selectedEventId) return
    try {
      setIsSavingTemplate(true)
      const fallbackTemplate = {
        type: 'builtin',
        templateId: 'classic',
        backgroundImage: '',
        namePosition: 'center',
        textColor: 'dark',
      }
      await updateCertificateTemplate(selectedEventId, fallbackTemplate)
      setTemplateDraft(fallbackTemplate)
      setDesignTab('builtin')
      setShowRemoveConfirm(false)
      setImageWarning(null)
      await refreshEvents()
      setSaveSuccessNotification(true)
      setTimeout(() => setSaveSuccessNotification(false), 2500)
    } catch (err) {
      setTemplateError(err.message || 'Failed to remove custom design.')
    } finally {
      setIsSavingTemplate(false)
    }
  }

  // -----------------------------------------------------------------
  // ISSUANCE HANDLERS
  // -----------------------------------------------------------------

  const handleOpenSingleConfirm = (row) => {
    setActionError(null)
    setConfirmModal({
      open: true,
      volunteerIds: [row.volunteerId],
      volunteerName: row.volunteerName,
      isAll: false,
    })
  }

  const handleOpenAllConfirm = () => {
    setActionError(null)
    const eligibleIds = eligibleVolunteers.map((v) => v.volunteerId)
    setConfirmModal({
      open: true,
      volunteerIds: eligibleIds,
      volunteerName: '',
      isAll: true,
    })
  }

  const handleCloseConfirm = () => {
    if (isIssuing) return
    setConfirmModal({ open: false, volunteerIds: [], volunteerName: '', isAll: false })
    setActionError(null)
  }

  const handleConfirmIssue = async () => {
    try {
      setIsIssuing(true)
      setActionError(null)
      await issueCertificates(selectedEventId, confirmModal.volunteerIds)
      await reload()
      setConfirmModal({ open: false, volunteerIds: [], volunteerName: '', isAll: false })
    } catch (err) {
      setActionError(err.message || 'Failed to issue certificates.')
    } finally {
      setIsIssuing(false)
    }
  }

  // -----------------------------------------------------------------
  // PREVIEW MODAL HANDLERS
  // -----------------------------------------------------------------

  const handleOpenPreview = (row) => {
    const formattedDate = row?.issuedAt
      ? new Date(row.issuedAt).toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })
      : new Date().toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })

    setPreviewCert({
      volunteerName: row?.volunteerName || 'Alex Morgan',
      eventName: selectedEvent?.name || row?.eventName || 'TechnoSphere 2025',
      date: formattedDate,
      hours: row?.hours || 4,
      status: row?.status || 'not_issued',
      template: templateDraft,
    })
  }

  const handleClosePreview = () => {
    setPreviewCert(null)
  }

  // -----------------------------------------------------------------
  // CONDITIONAL RENDER STATES
  // -----------------------------------------------------------------

  // 1. Empty state: No event selected in topbar picker
  if (!selectedEventId) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-16 h-16 rounded-2xl bg-surface-container-low flex items-center justify-center text-outline mb-space-md">
            <span className="material-symbols-outlined text-[32px]">event_busy</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Choose an event to view certificates
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
            Please select an event from the topbar dropdown to configure certificate designs, view eligible volunteers, and issue participation certificates.
          </p>
        </div>
      </div>
    )
  }

  // 2. Loading state
  if (loading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading certificate eligibility & design...</p>
        </div>
      </div>
    )
  }

  // 3. Error state
  if (error) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-error-container text-error flex items-center justify-center mb-space-md shadow-sm">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
            Unable to load certificates
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {error || 'An unexpected error occurred while fetching certificates for this event.'}
          </p>
          <button
            onClick={() => reload()}
            type="button"
            className="px-space-lg py-2.5 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg shadow-sm hover:opacity-90 active:scale-95 transition-all cursor-pointer"
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col w-full">
      {/* Top Header Banner */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-lg mb-space-xl">
        <div className="flex flex-col gap-space-xs">
          <div className="flex items-center gap-space-sm text-outline">
            <span className="material-symbols-outlined text-[16px]">domain_verification</span>
            <span className="font-label-sm text-label-sm uppercase tracking-wider">
              Event Scope: {selectedEvent?.name || 'Selected Event'}
            </span>
          </div>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">Certificates</h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
            Choose a certificate design and issue participation certificates to volunteers who completed their duties.
          </p>
        </div>
      </div>

      {/* =================================================================== */}
      {/* CERTIFICATE DESIGN SELECTION SECTION */}
      {/* =================================================================== */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm p-6 mb-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-outline-variant/20">
          <div>
            <h2 className="text-lg font-semibold text-on-surface tracking-tight">Certificate design</h2>
            <p className="text-xs text-outline mt-0.5">
              Choose an official built-in certificate layout or upload your customized background template.
            </p>
          </div>

          {/* Tab buttons */}
          <div className="flex items-center gap-1 bg-surface-container-low p-1 rounded-lg self-start sm:self-auto">
            <button
              type="button"
              onClick={() => {
                setDesignTab('builtin')
                setTemplateDraft((prev) => ({ ...prev, type: 'builtin' }))
              }}
              className={`px-3 py-1.5 rounded-md text-xs transition-all cursor-pointer ${
                designTab === 'builtin'
                  ? 'bg-surface-container-lowest text-primary font-semibold shadow-xs'
                  : 'text-on-surface-variant hover:text-on-surface font-medium'
              }`}
            >
              Built-in templates
            </button>
            <button
              type="button"
              onClick={() => {
                setDesignTab('upload')
                setTemplateDraft((prev) => ({ ...prev, type: 'custom' }))
              }}
              className={`px-3 py-1.5 rounded-md text-xs transition-all cursor-pointer ${
                designTab === 'upload'
                  ? 'bg-surface-container-lowest text-primary font-semibold shadow-xs'
                  : 'text-on-surface-variant hover:text-on-surface font-medium'
              }`}
            >
              Custom upload
            </button>
          </div>
        </div>

        {/* Panel A: Built-in Templates */}
        {designTab === 'builtin' && (
          <div className="pt-5 animate-fade-in">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
              {/* Classic Option */}
              <div
                onClick={() => handleSelectBuiltin('classic')}
                className={`relative rounded-xl p-4 cursor-pointer flex flex-col justify-between transition-all ${
                  templateDraft.templateId === 'classic' && templateDraft.type === 'builtin'
                    ? 'border-2 border-primary bg-primary/5 shadow-xs'
                    : 'border border-outline-variant/30 hover:border-primary/50 bg-surface-container-lowest'
                }`}
              >
                {templateDraft.templateId === 'classic' && templateDraft.type === 'builtin' && (
                  <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-primary text-white flex items-center justify-center">
                    <span className="material-symbols-outlined text-[14px]">check</span>
                  </div>
                )}
                <div className="h-28 bg-white border border-primary/20 rounded-lg p-3 flex flex-col items-center justify-center relative overflow-hidden mb-3 shadow-xs">
                  <div className="absolute inset-1.5 border border-primary/10 rounded pointer-events-none" />
                  <div className="w-4 h-4 rounded bg-primary mb-1.5" />
                  <div className="w-24 h-1.5 bg-outline-variant/50 rounded-full mb-1" />
                  <div className="w-16 h-1 bg-outline-variant/30 rounded-full mb-2" />
                  <div className="w-20 h-2 bg-primary/20 rounded-full" />
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-on-surface">Classic</span>
                  {templateDraft.templateId === 'classic' && templateDraft.type === 'builtin' && (
                    <span className="text-[11px] font-medium text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                      Selected
                    </span>
                  )}
                </div>
                <p className="text-xs text-outline mt-1">Formal ornamental border with traditional serif typography.</p>
              </div>

              {/* Modern Option */}
              <div
                onClick={() => handleSelectBuiltin('modern')}
                className={`relative rounded-xl p-4 cursor-pointer flex flex-col justify-between transition-all ${
                  templateDraft.templateId === 'modern' && templateDraft.type === 'builtin'
                    ? 'border-2 border-primary bg-primary/5 shadow-xs'
                    : 'border border-outline-variant/30 hover:border-primary/50 bg-surface-container-lowest'
                }`}
              >
                {templateDraft.templateId === 'modern' && templateDraft.type === 'builtin' && (
                  <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-primary text-white flex items-center justify-center">
                    <span className="material-symbols-outlined text-[14px]">check</span>
                  </div>
                )}
                <div className="h-28 bg-surface-container-low border border-outline-variant/30 rounded-lg p-3 flex flex-col items-center justify-center relative overflow-hidden mb-3">
                  <div className="w-full h-1.5 bg-primary absolute top-0 left-0 right-0" />
                  <div className="w-5 h-5 rounded bg-on-surface mb-1.5" />
                  <div className="w-20 h-1.5 bg-outline-variant/60 rounded-full mb-1" />
                  <div className="w-24 h-2 bg-outline-variant/40 rounded-full" />
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-on-surface">Modern</span>
                  {templateDraft.templateId === 'modern' && templateDraft.type === 'builtin' && (
                    <span className="text-[11px] font-medium text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                      Selected
                    </span>
                  )}
                </div>
                <p className="text-xs text-outline mt-1">Crisp geometric lines with bold structural layout.</p>
              </div>

              {/* Minimal Option */}
              <div
                onClick={() => handleSelectBuiltin('minimal')}
                className={`relative rounded-xl p-4 cursor-pointer flex flex-col justify-between transition-all ${
                  templateDraft.templateId === 'minimal' && templateDraft.type === 'builtin'
                    ? 'border-2 border-primary bg-primary/5 shadow-xs'
                    : 'border border-outline-variant/30 hover:border-primary/50 bg-surface-container-lowest'
                }`}
              >
                {templateDraft.templateId === 'minimal' && templateDraft.type === 'builtin' && (
                  <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-primary text-white flex items-center justify-center">
                    <span className="material-symbols-outlined text-[14px]">check</span>
                  </div>
                )}
                <div className="h-28 bg-surface-container-low border border-outline-variant/30 rounded-lg p-3 flex flex-col items-center justify-center relative overflow-hidden mb-3">
                  <div className="w-16 h-1.5 bg-outline-variant/50 rounded-full mb-2" />
                  <div className="w-24 h-2 bg-on-surface/50 rounded-full mb-1.5" />
                  <div className="w-12 h-1 bg-outline-variant/30 rounded-full" />
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-on-surface">Minimal</span>
                  {templateDraft.templateId === 'minimal' && templateDraft.type === 'builtin' && (
                    <span className="text-[11px] font-medium text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                      Selected
                    </span>
                  )}
                </div>
                <p className="text-xs text-outline mt-1">Understated editorial layout focusing purely on typography.</p>
              </div>
            </div>
          </div>
        )}

        {/* Panel B: Custom Upload */}
        {designTab === 'upload' && (
          <div className="pt-5 animate-fade-in">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
              {/* Upload Dropzone */}
              <div>
                <label className="border-2 border-dashed border-outline-variant hover:border-primary rounded-xl p-6 text-center bg-surface-container-low/50 hover:bg-primary/5 transition-all cursor-pointer flex flex-col items-center justify-center block">
                  <input
                    type="file"
                    accept="image/png, image/jpeg, image/jpg"
                    onChange={handleFileUpload}
                    className="hidden"
                  />
                  <div className="w-10 h-10 rounded-full bg-surface-container-lowest border border-outline-variant/30 shadow-xs flex items-center justify-center text-primary mb-2">
                    <span className="material-symbols-outlined text-[20px]">cloud_upload</span>
                  </div>
                  <span className="text-sm font-semibold text-on-surface">Click to upload custom template</span>
                  <p className="text-xs text-outline mt-1">Upload landscape background, PNG or JPG, max 5 MB</p>
                  <span className="mt-3 px-3 py-1.5 bg-surface-container-lowest border border-outline-variant/40 hover:bg-surface-container-low text-xs font-semibold text-on-surface rounded-lg shadow-xs transition-all">
                    Browse file
                  </span>
                </label>

                {/* Status & Remove action */}
                <div className="mt-4 flex items-center justify-between text-xs">
                  <span className="text-outline">
                    {templateDraft.backgroundImage ? 'Custom background loaded' : 'Using default template'}
                  </span>
                  {templateDraft.backgroundImage && (
                    <button
                      type="button"
                      onClick={() => setShowRemoveConfirm(true)}
                      className="text-xs font-medium text-error hover:underline inline-flex items-center gap-1 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[14px]">delete</span>
                      <span>Remove custom design</span>
                    </button>
                  )}
                </div>

                {/* Inline Errors or Warnings */}
                {templateError && (
                  <div className="mt-3 p-2.5 rounded-lg bg-error-container text-error text-xs flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-[16px]">error</span>
                    <span>{templateError}</span>
                  </div>
                )}
                {imageWarning && (
                  <div className="mt-3 p-2.5 rounded-lg bg-amber-50 text-amber-800 text-xs flex items-center gap-1.5 border border-amber-200">
                    <span className="material-symbols-outlined text-[16px]">warning</span>
                    <span>{imageWarning}</span>
                  </div>
                )}
              </div>

              {/* Text Positioning & Color Controls */}
              <div className="space-y-4">
                <div className="bg-surface-container-low border border-outline-variant/30 rounded-xl p-4">
                  <span className="text-xs font-semibold text-on-surface block mb-2">Volunteer name position</span>
                  <div className="grid grid-cols-3 gap-2">
                    {['upper', 'center', 'lower'].map((pos) => {
                      const isSelected = templateDraft.namePosition === pos
                      return (
                        <button
                          key={pos}
                          type="button"
                          onClick={() => setTemplateDraft((prev) => ({ ...prev, namePosition: pos }))}
                          className={`py-1.5 px-3 rounded-lg text-xs capitalize transition-all cursor-pointer ${
                            isSelected
                              ? 'border-2 border-primary bg-primary/10 text-primary font-semibold'
                              : 'border border-outline-variant/40 bg-surface-container-lowest hover:bg-surface-container-low text-on-surface font-medium'
                          }`}
                        >
                          {pos}
                        </button>
                      )
                    })}
                  </div>
                </div>

                <div className="bg-surface-container-low border border-outline-variant/30 rounded-xl p-4">
                  <span className="text-xs font-semibold text-on-surface block mb-2">Text color theme</span>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setTemplateDraft((prev) => ({ ...prev, textColor: 'dark' }))}
                      className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs transition-all cursor-pointer ${
                        templateDraft.textColor === 'dark'
                          ? 'border-2 border-primary bg-primary/10 text-primary font-semibold'
                          : 'border border-outline-variant/40 bg-surface-container-lowest hover:bg-surface-container-low text-on-surface font-medium'
                      }`}
                    >
                      <span className="w-3.5 h-3.5 rounded-full bg-slate-900 border border-slate-700" />
                      <span>Dark text</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setTemplateDraft((prev) => ({ ...prev, textColor: 'light' }))}
                      className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs transition-all cursor-pointer ${
                        templateDraft.textColor === 'light'
                          ? 'border-2 border-primary bg-primary/10 text-primary font-semibold'
                          : 'border border-outline-variant/40 bg-surface-container-lowest hover:bg-surface-container-low text-on-surface font-medium'
                      }`}
                    >
                      <span className="w-3.5 h-3.5 rounded-full bg-white border border-slate-300" />
                      <span>Light text</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Action Ribbon: Preview & Save Design */}
        <div className="pt-4 border-t border-outline-variant/20 flex items-center justify-end gap-2.5">
          {saveSuccessNotification && (
            <span className="flex items-center gap-1 text-emerald-600 font-label-sm text-xs animate-fade-in mr-2">
              <span className="material-symbols-outlined text-[16px]">check_circle</span>
              <span>Saved</span>
            </span>
          )}

          <button
            type="button"
            onClick={() => handleOpenPreview(null)}
            className="px-4 py-2 border border-outline-variant/40 hover:bg-surface-container-low text-on-surface text-xs font-medium rounded-lg transition-colors cursor-pointer"
          >
            Preview design
          </button>

          <button
            type="button"
            disabled={isSavingTemplate}
            onClick={handleSaveDesign}
            className="px-4 py-2 bg-primary-container hover:bg-primary text-on-primary text-xs font-medium rounded-lg shadow-sm transition-all cursor-pointer disabled:opacity-50"
          >
            {isSavingTemplate ? 'Saving...' : 'Save design'}
          </button>
        </div>
      </div>

      {/* =================================================================== */}
      {/* VOLUNTEER LIST & ISSUANCE TOOLBAR */}
      {/* =================================================================== */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-space-md mb-space-lg">
        {/* Search Input */}
        <div className="relative w-full sm:w-80">
          <span className="material-symbols-outlined absolute left-space-md top-1/2 -translate-y-1/2 text-outline text-[18px]">
            search
          </span>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search volunteers by name..."
            className="w-full h-10 pl-10 pr-space-md bg-surface-container-lowest text-on-surface placeholder:text-outline rounded-lg font-body-md text-body-md border border-outline-variant/30 focus:outline-none focus:ring-1 focus:ring-primary shadow-xs transition-all"
          />
        </div>

        {/* Issue to all eligible Button */}
        <button
          type="button"
          onClick={handleOpenAllConfirm}
          disabled={eligibleVolunteers.length === 0}
          className="inline-flex items-center justify-center gap-space-xs px-space-lg py-2 bg-primary-container hover:bg-primary text-on-primary font-label-lg text-label-lg rounded-lg shadow-sm transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <span className="material-symbols-outlined text-[18px]">verified</span>
          <span>Issue to all eligible ({eligibleVolunteers.length})</span>
        </button>
      </div>

      {/* Certificates Table */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm overflow-hidden mb-space-xl">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="border-b border-outline-variant/20 bg-surface-container-low font-label-sm text-label-sm text-outline uppercase tracking-wider">
              <th className="py-space-md px-space-lg" scope="col">Volunteer</th>
              <th className="py-space-md px-space-lg" scope="col">Tasks completed</th>
              <th className="py-space-md px-space-lg" scope="col">Hours</th>
              <th className="py-space-md px-space-lg" scope="col">Status</th>
              <th className="py-space-md px-space-lg text-right" scope="col">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/20 font-body-md">
            {filteredRows.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-20 px-space-lg">
                  <div className="flex flex-col items-center justify-center text-center max-w-md mx-auto">
                    <div className="w-14 h-14 rounded-full bg-surface-container-low text-primary flex items-center justify-center mb-space-md">
                      <span className="material-symbols-outlined text-[28px]">workspace_premium</span>
                    </div>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold mb-space-xs">
                      {certificates.length === 0
                        ? 'No certificates to issue yet'
                        : 'No volunteers match your search'}
                    </h3>
                    <p className="font-body-md text-body-md text-outline leading-relaxed">
                      {certificates.length === 0
                        ? 'Volunteers become eligible after the plan is published and post-event feedback is recorded.'
                        : 'Try searching with a different volunteer name.'}
                    </p>
                  </div>
                </td>
              </tr>
            ) : (
              filteredRows.map((row) => {
                const isIssued = row.status === 'issued'
                return (
                  <tr key={row.volunteerId} className="hover:bg-surface-container-low/40 transition-colors">
                    {/* Volunteer Column */}
                    <td className="py-space-md px-space-lg">
                      <div className="flex items-center gap-space-sm">
                        <div className="w-9 h-9 rounded-full bg-primary/10 text-primary font-semibold text-label-md flex items-center justify-center shrink-0">
                          {row.volunteerName
                            .split(' ')
                            .map((n) => n[0])
                            .slice(0, 2)
                            .join('')}
                        </div>
                        <span className="font-label-md text-label-md text-on-surface font-medium">
                          {row.volunteerName}
                        </span>
                      </div>
                    </td>

                    {/* Tasks completed */}
                    <td className="py-space-md px-space-lg text-on-surface-variant">
                      {row.tasksCompleted} {row.tasksCompleted === 1 ? 'task' : 'tasks'}
                    </td>

                    {/* Hours */}
                    <td className="py-space-md px-space-lg text-on-surface-variant font-mono">
                      {row.hours} hrs
                    </td>

                    {/* Status badge */}
                    <td className="py-space-md px-space-lg">
                      {isIssued ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-secondary-container text-secondary font-label-sm text-label-sm font-semibold">
                          <span className="material-symbols-outlined text-[14px]">check_circle</span>
                          <span>Issued</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container text-outline font-label-sm text-label-sm">
                          <span className="material-symbols-outlined text-[14px]">pending</span>
                          <span>Not Issued</span>
                        </span>
                      )}
                    </td>

                    {/* Action buttons */}
                    <td className="py-space-md px-space-lg text-right">
                      <div className="flex items-center justify-end gap-space-xs">
                        {/* Preview Action */}
                        <button
                          type="button"
                          onClick={() => handleOpenPreview(row)}
                          className="px-space-md py-1.5 rounded-lg border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
                        >
                          Preview
                        </button>

                        {/* Issue Action */}
                        <button
                          type="button"
                          disabled={isIssued}
                          onClick={() => handleOpenSingleConfirm(row)}
                          className="px-space-md py-1.5 rounded-lg bg-primary-container hover:bg-primary text-on-primary font-label-md text-label-md shadow-xs transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {isIssued ? 'Issued' : 'Issue'}
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Confirmation Modal for Removing Custom Design */}
      {showRemoveConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-on-surface/40 backdrop-blur-xs animate-fade-in">
          <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-outline-variant/30 max-w-md w-full p-space-xl flex flex-col gap-space-md animate-scale-up">
            <div className="flex items-center gap-space-sm text-error">
              <span className="material-symbols-outlined text-[28px]">warning</span>
              <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                Remove Custom Design?
              </h3>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
              Are you sure you want to remove the custom uploaded background? The event will fall back to the built-in Classic certificate template.
            </p>
            <div className="flex items-center justify-end gap-space-sm mt-space-sm">
              <button
                type="button"
                onClick={() => setShowRemoveConfirm(false)}
                className="px-space-lg py-2 rounded-lg border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmRemoveCustom}
                className="px-space-lg py-2 rounded-lg bg-error text-on-error font-label-md font-semibold shadow-sm hover:opacity-90 transition-all cursor-pointer"
              >
                Remove and reset
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal for Issuing */}
      {confirmModal.open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-on-surface/40 backdrop-blur-xs animate-fade-in">
          <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-outline-variant/30 max-w-md w-full p-space-xl flex flex-col gap-space-md animate-scale-up">
            <div className="flex items-center gap-space-sm text-primary">
              <span className="material-symbols-outlined text-[28px]">verified</span>
              <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                Confirm Certificate Issuance
              </h3>
            </div>

            <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
              {confirmModal.isAll ? (
                <>
                  Are you sure you want to issue certificates to all{' '}
                  <span className="font-semibold text-on-surface">
                    {confirmModal.volunteerIds.length} eligible volunteers
                  </span>
                  ? This will make their certificates immediately available in their portal using the currently active certificate design.
                </>
              ) : (
                <>
                  Are you sure you want to issue a certificate to{' '}
                  <span className="font-semibold text-on-surface">{confirmModal.volunteerName}</span>?
                </>
              )}
            </p>

            {actionError && (
              <div className="p-space-sm rounded-lg bg-error-container text-error text-body-sm flex items-center gap-space-xs">
                <span className="material-symbols-outlined text-[16px]">error</span>
                <span>{actionError}</span>
              </div>
            )}

            <div className="flex items-center justify-end gap-space-sm mt-space-sm pt-space-xs">
              <button
                type="button"
                disabled={isIssuing}
                onClick={handleCloseConfirm}
                className="px-space-lg py-2 rounded-lg border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isIssuing}
                onClick={handleConfirmIssue}
                className="px-space-lg py-2 rounded-lg bg-primary-container text-on-primary font-label-md font-semibold shadow-sm hover:bg-primary transition-all flex items-center gap-space-xs cursor-pointer disabled:opacity-50"
              >
                {isIssuing ? (
                  <>
                    <div className="w-4 h-4 rounded-full border-2 border-white/20 border-t-white animate-spin" />
                    <span>Issuing...</span>
                  </>
                ) : (
                  <span>Confirm and Issue</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Certificate Preview Modal */}
      {previewCert && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-on-surface/40 backdrop-blur-xs animate-fade-in">
          <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-outline-variant/30 max-w-3xl w-full overflow-hidden flex flex-col animate-scale-up max-h-[90vh]">
            {/* Modal Top Bar */}
            <div className="px-space-lg py-space-md border-b border-outline-variant/20 flex items-center justify-between bg-surface-container-low/50">
              <div className="flex items-center gap-space-xs text-on-surface font-label-lg font-semibold">
                <span className="material-symbols-outlined text-primary text-[20px]">workspace_premium</span>
                <span>Certificate Preview</span>
              </div>
              <button
                type="button"
                onClick={handleClosePreview}
                className="p-1 rounded-md text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Certificate Render using CertificateCard component */}
            <div className="p-6 sm:p-10 bg-surface flex justify-center overflow-y-auto">
              <div className="w-full max-w-2xl flex justify-center">
                <CertificateCard
                  template={previewCert.template}
                  volunteerName={previewCert.volunteerName}
                  eventName={previewCert.eventName}
                  date={previewCert.date}
                  hours={previewCert.hours}
                />
              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-space-lg py-space-sm border-t border-outline-variant/20 bg-surface-container-lowest flex justify-end">
              <button
                type="button"
                onClick={handleClosePreview}
                className="px-space-lg py-1.5 rounded-lg border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md transition-colors cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
