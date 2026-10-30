import { useState, useCallback, useMemo } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth, getHomeRoute } from '../context/AuthContext'
import { getSettings, saveSettings } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import { DEFAULT_WEIGHTS } from '../utils/scoring'

const WEIGHT_FIELDS = [
  {
    key: 'skill',
    num: 1,
    title: 'Skill',
    desc: "Skill: how closely a volunteer's skill level matches what the task requires.",
  },
  {
    key: 'experience',
    num: 2,
    title: 'Experience',
    desc: 'Experience: prior event hours logged and track record in similar event domains.',
  },
  {
    key: 'performance',
    num: 3,
    title: 'Performance',
    desc: 'Performance: average coordinator rating and shift feedback scores.',
  },
  {
    key: 'load',
    num: 4,
    title: 'Load',
    desc: 'Load: balancing shift distribution across active registered volunteers.',
  },
  {
    key: 'interest',
    num: 5,
    title: 'Interest',
    desc: 'Interest: volunteer preferences and role track choices.',
  },
]

export default function SettingsPage() {
  const { role } = useAuth()

  // Fetch settings callback
  const fetchSettingsCallback = useCallback(() => {
    return getSettings()
  }, [])

  const { data: initialSettings, loading, error, reload } = useFetch(fetchSettingsCallback)

  // Local form state
  const [weights, setWeights] = useState({ ...DEFAULT_WEIGHTS })
  const [firstTimerBonus, setFirstTimerBonus] = useState(5)
  const [defaultMaxHours, setDefaultMaxHours] = useState(8)

  // UI state
  const [activeTab, setActiveTab] = useState('allocation') // 'profile' | 'notifications' | 'allocation'
  const [isSaving, setIsSaving] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [showResetConfirm, setShowResetConfirm] = useState(false)

  // Track the loaded settings version to sync form state during render
  const [syncedSettings, setSyncedSettings] = useState(null)

  // Populate local state during render when initialSettings arrives
  if (initialSettings && initialSettings !== syncedSettings) {
    setSyncedSettings(initialSettings)
    setWeights({
      skill: initialSettings.weights?.skill ?? DEFAULT_WEIGHTS.skill,
      experience: initialSettings.weights?.experience ?? DEFAULT_WEIGHTS.experience,
      performance: initialSettings.weights?.performance ?? DEFAULT_WEIGHTS.performance,
      load: initialSettings.weights?.load ?? DEFAULT_WEIGHTS.load,
      interest: initialSettings.weights?.interest ?? DEFAULT_WEIGHTS.interest,
    })
    setFirstTimerBonus(initialSettings.firstTimerBonus ?? 5)
    setDefaultMaxHours(initialSettings.defaultMaxHours ?? 8)
  }

  // Compute live weights total
  const weightsTotal = useMemo(() => {
    return (
      (Number(weights.skill) || 0) +
      (Number(weights.experience) || 0) +
      (Number(weights.performance) || 0) +
      (Number(weights.load) || 0) +
      (Number(weights.interest) || 0)
    )
  }, [weights])

  const isTotal100 = weightsTotal === 100

  // Validation
  const bonusNum = Number(firstTimerBonus)
  const isBonusValid = !isNaN(bonusNum) && Number.isInteger(bonusNum) && bonusNum >= 0 && bonusNum <= 20
  const bonusError = !isBonusValid ? 'First-timer bonus must be an integer between 0 and 20.' : ''

  const maxHoursNum = Number(defaultMaxHours)
  const isMaxHoursValid = !isNaN(maxHoursNum) && Number.isInteger(maxHoursNum) && maxHoursNum >= 1
  const maxHoursError = !isMaxHoursValid ? 'Default max hours must be an integer of at least 1.' : ''

  const isFormValid = isTotal100 && isBonusValid && isMaxHoursValid

  // Handle single weight change (from slider or numeric input)
  const handleWeightChange = (key, rawValue) => {
    setSaveSuccess(false)
    setSaveError('')
    let val = parseInt(rawValue, 10)
    if (isNaN(val)) val = 0
    if (val < 0) val = 0
    if (val > 100) val = 100
    setWeights((prev) => ({
      ...prev,
      [key]: val,
    }))
  }

  // Handle Save
  const handleSave = async () => {
    if (!isFormValid || isSaving) return
    setSaveSuccess(false)
    setSaveError('')
    setIsSaving(true)

    try {
      await saveSettings({
        weights: {
          skill: Number(weights.skill) || 0,
          experience: Number(weights.experience) || 0,
          performance: Number(weights.performance) || 0,
          load: Number(weights.load) || 0,
          interest: Number(weights.interest) || 0,
        },
        firstTimerBonus: Number(firstTimerBonus),
        defaultMaxHours: Number(defaultMaxHours),
      })
      setSaveSuccess(true)
      setTimeout(() => {
        setSaveSuccess(false)
      }, 3000)
    } catch (err) {
      setSaveError(err.message || 'Failed to save settings. Please try again.')
    } finally {
      setIsSaving(false)
    }
  }

  // Handle Reset to Defaults with confirm
  const handleConfirmReset = () => {
    setWeights({ ...DEFAULT_WEIGHTS })
    setFirstTimerBonus(5)
    setDefaultMaxHours(8)
    setSaveSuccess(false)
    setSaveError('')
    setShowResetConfirm(false)
  }

  // Redirect volunteers away (role protection: organiser/coordinator only)
  if (role === 'volunteer') {
    return <Navigate to={getHomeRoute(role)} replace />
  }

  // Loading state
  if (loading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading settings...</p>
        </div>
      </div>
    )
  }

  // Error state
  if (error) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-outline-variant/30 p-10 max-w-md w-full text-center flex flex-col items-center">
          <div className="w-14 h-14 rounded-full bg-error-container text-error flex items-center justify-center mb-space-md">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
            Failed to load settings
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mb-space-lg leading-relaxed">
            {error || 'An unexpected error occurred while loading settings.'}
          </p>
          <button
            type="button"
            onClick={reload}
            className="px-space-lg py-2 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors cursor-pointer shadow-sm"
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  // Discrepancy calculation for live message
  const difference = 100 - weightsTotal
  const discrepancyMessage =
    difference > 0
      ? `Total is ${weightsTotal}%. Add ${difference}%.`
      : `Total is ${weightsTotal}%. Reduce by ${Math.abs(difference)}%.`

  return (
    <div className="flex flex-col w-full pb-24">
      <div className="p-margin-lg flex flex-col gap-space-xl">
        {/* Top Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div>
            <div className="flex items-center gap-space-sm mb-1">
              <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold">Settings</h1>
              <span className="font-label-sm text-label-sm bg-surface-container-high text-primary px-2.5 py-0.5 rounded-full font-medium">
                v2.4
              </span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant">
              Manage your profile, notifications, and automated allocation solver rules.
            </p>
          </div>

          {/* Quick status pill */}
          <div className="flex items-center gap-space-sm">
            {saveSuccess && (
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-secondary-container text-on-secondary-container font-label-sm text-label-sm font-semibold transition-all shadow-xs">
                <span className="material-symbols-outlined text-[16px]">check_circle</span>
                <span>Saved successfully</span>
              </div>
            )}
            {isSaving && (
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-primary-fixed text-primary font-label-sm text-label-sm font-medium shadow-xs">
                <span className="material-symbols-outlined text-[16px] animate-spin">sync</span>
                <span>Saving settings...</span>
              </div>
            )}
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="bg-surface-container-lowest rounded-xl p-1.5 shadow-sm flex items-center gap-1 border border-outline-variant/20">
          <button
            type="button"
            onClick={() => setActiveTab('profile')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg font-label-lg text-label-lg transition-all cursor-pointer ${
              activeTab === 'profile'
                ? 'bg-primary text-on-primary shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">account_circle</span>
            <span>Profile</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('notifications')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg font-label-lg text-label-lg transition-all cursor-pointer ${
              activeTab === 'notifications'
                ? 'bg-primary text-on-primary shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">notifications_active</span>
            <span>Notifications</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('allocation')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg font-label-lg text-label-lg transition-all cursor-pointer ${
              activeTab === 'allocation'
                ? 'bg-primary text-on-primary shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">tune</span>
            <span>Allocation rules</span>
            <span className="w-1.5 h-1.5 rounded-full bg-secondary-container" />
          </button>
        </div>

        {/* TAB 1: Profile Section */}
        {activeTab === 'profile' && (
          <div className="flex flex-col gap-space-lg">
            <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-sm border border-outline-variant/20">
              <div className="pb-space-md mb-space-lg flex items-center justify-between border-b border-outline-variant/15">
                <div>
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Personal Information
                  </h2>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    Identity details and campus organization credentials
                  </p>
                </div>
                <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center text-on-surface-variant">
                  <span className="material-symbols-outlined text-[22px]">badge</span>
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-space-lg">
                <div className="flex flex-col gap-1.5">
                  <label className="font-label-md text-label-md text-on-surface font-medium">
                    Full name
                  </label>
                  <input
                    type="text"
                    defaultValue="Rishiraj Jhawar"
                    className="w-full h-10 px-3 rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md border border-outline-variant/40 shadow-xs focus:outline-none focus:border-primary"
                  />
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    Your name displayed on published event shift sheets
                  </span>
                </div>
                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="font-label-md text-label-md text-on-surface font-medium">
                      Email address
                    </label>
                    <span className="font-label-sm text-label-sm bg-surface-container px-2 py-0.5 rounded-full text-on-surface-variant flex items-center gap-1">
                      <span className="material-symbols-outlined text-[12px]">lock</span> Read-only
                    </span>
                  </div>
                  <input
                    type="email"
                    value="organiser@eventra.internal"
                    readOnly
                    className="w-full h-10 px-3 rounded-lg bg-surface-container-low text-on-surface-variant font-body-md text-body-md border border-outline-variant/30 cursor-not-allowed"
                  />
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    Email is managed by university directory
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: Notifications Section */}
        {activeTab === 'notifications' && (
          <div className="flex flex-col gap-space-lg">
            <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-sm border border-outline-variant/20">
              <div className="pb-space-md mb-space-lg border-b border-outline-variant/15">
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Notification Channels
                </h2>
                <p className="font-body-sm text-body-sm text-on-surface-variant">
                  Define how urgent dispatch notices and roster changes are delivered
                </p>
              </div>

              <div className="flex flex-col gap-space-md">
                <div className="flex items-center justify-between p-space-md rounded-xl bg-surface-container-low border border-outline-variant/20">
                  <div className="flex items-center gap-space-md">
                    <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                      <span className="material-symbols-outlined text-[20px]">mail</span>
                    </div>
                    <div>
                      <div className="font-label-lg text-label-lg text-on-surface font-semibold">
                        Email notifications
                      </div>
                      <p className="font-body-sm text-body-sm text-on-surface-variant">
                        Receive system digests and assignment updates via university email.
                      </p>
                    </div>
                  </div>
                  <span className="font-label-sm text-label-sm px-2.5 py-1 rounded-full bg-secondary-container/50 text-secondary font-semibold">
                    Enabled
                  </span>
                </div>

                <div className="flex items-center gap-space-md p-space-md rounded-xl bg-surface-container text-on-surface-variant">
                  <span className="material-symbols-outlined text-primary text-[20px]">info</span>
                  <span className="font-body-sm text-body-sm">
                    Volunteers are only notified after an assignment plan is approved or published.
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: Allocation Rules (Main Working Tab) */}
        {activeTab === 'allocation' && (
          <div className="flex flex-col gap-space-xl">
            {/* Constraints Card */}
            <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-sm border border-outline-variant/20">
              <div className="pb-space-md mb-space-lg border-b border-outline-variant/15">
                <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Volunteer Limits &amp; First-Timer Policy
                </h2>
                <p className="font-body-sm text-body-sm text-on-surface-variant">
                  Global parameters governing automated solver matches and workload balance
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-space-lg">
                {/* Default Max Hours Input */}
                <div className="flex flex-col gap-1.5 p-space-md rounded-xl bg-surface-container-low border border-outline-variant/20">
                  <div className="flex items-center justify-between">
                    <label className="font-label-md text-label-md text-on-surface font-semibold">
                      Default maximum hours per person
                    </label>
                    <span className="material-symbols-outlined text-[18px] text-on-surface-variant">
                      timelapse
                    </span>
                  </div>
                  <div className="relative max-w-xs mt-1">
                    <input
                      type="number"
                      min="1"
                      step="1"
                      disabled={isSaving}
                      value={defaultMaxHours}
                      onChange={(e) => {
                        setDefaultMaxHours(e.target.value)
                        setSaveSuccess(false)
                        setSaveError('')
                      }}
                      className="w-full h-10 px-3 pr-16 rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md border border-outline-variant/40 shadow-xs focus:outline-none focus:border-primary disabled:opacity-50"
                    />
                    <span className="absolute right-3 top-2.5 font-label-sm text-label-sm text-outline">
                      hours
                    </span>
                  </div>
                  {maxHoursError ? (
                    <span className="font-body-sm text-body-sm text-error mt-1">{maxHoursError}</span>
                  ) : (
                    <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                      Standard allocation cap per volunteer per event cycle (min: 1 hr).
                    </span>
                  )}
                </div>

                {/* First-Timer Bonus Points */}
                <div className="flex flex-col gap-1.5 p-space-md rounded-xl bg-surface-container-low border border-outline-variant/20">
                  <div className="flex items-center justify-between">
                    <label className="font-label-md text-label-md text-on-surface font-semibold">
                      First-timer bonus score
                    </label>
                    <span className="material-symbols-outlined text-[18px] text-secondary">
                      stars
                    </span>
                  </div>
                  <div className="relative max-w-xs mt-1">
                    <input
                      type="number"
                      min="0"
                      max="20"
                      step="1"
                      disabled={isSaving}
                      value={firstTimerBonus}
                      onChange={(e) => {
                        setFirstTimerBonus(e.target.value)
                        setSaveSuccess(false)
                        setSaveError('')
                      }}
                      className="w-full h-10 px-3 pr-16 rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md border border-outline-variant/40 shadow-xs focus:outline-none focus:border-primary disabled:opacity-50"
                    />
                    <span className="absolute right-3 top-2.5 font-label-sm text-label-sm text-outline">
                      pts
                    </span>
                  </div>
                  {bonusError ? (
                    <span className="font-body-sm text-body-sm text-error mt-1">{bonusError}</span>
                  ) : (
                    <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                      Priority boost (0–20) given to new volunteers on non-critical tasks.
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Scoring Weights Card */}
            <div className="bg-surface-container-lowest rounded-xl p-space-xl shadow-sm border border-outline-variant/20">
              {/* Header with Live Total & Reset Button */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-md pb-space-md mb-space-lg border-b border-outline-variant/15">
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Scoring Weights
                    </h2>
                    <span className="material-symbols-outlined text-[18px] text-primary">auto_awesome</span>
                  </div>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    Calibrate multi-objective matching optimization weights (must sum to exactly 100%)
                  </p>
                </div>

                <div className="flex items-center gap-space-sm self-start sm:self-auto">
                  <div
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-full font-label-md text-label-md font-semibold transition-all ${
                      isTotal100
                        ? 'bg-secondary-container/50 text-secondary border border-secondary/30'
                        : 'bg-error-container text-on-error-container border border-error/30'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[18px]">
                      {isTotal100 ? 'check_circle' : 'warning'}
                    </span>
                    <span>Total: {weightsTotal}%</span>
                  </div>

                  <button
                    type="button"
                    disabled={isSaving}
                    onClick={() => setShowResetConfirm(true)}
                    className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-sm text-label-sm transition-all flex items-center gap-1 cursor-pointer border border-outline-variant/30 disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-[16px]">restart_alt</span>
                    <span>Reset to defaults</span>
                  </button>
                </div>
              </div>

              {/* Inline Discrepancy Error Banner if not 100% */}
              {!isTotal100 && (
                <div className="mb-space-lg p-space-md rounded-xl bg-error-container/60 border border-error/30 text-on-error-container flex items-center justify-between gap-space-md shadow-xs">
                  <div className="flex items-center gap-space-sm">
                    <span className="material-symbols-outlined text-[20px] text-error">error</span>
                    <span className="font-label-md text-label-md font-semibold">
                      {discrepancyMessage}
                    </span>
                  </div>
                  <span className="font-body-sm text-body-sm text-outline hidden md:inline">
                    Save is disabled until weights equal 100%
                  </span>
                </div>
              )}

              {/* Sliders & Numeric Inputs */}
              <div className="flex flex-col gap-space-lg">
                {WEIGHT_FIELDS.map((field) => {
                  const currentValue = weights[field.key] ?? 0
                  return (
                    <div
                      key={field.key}
                      className="flex flex-col gap-2 p-space-md rounded-xl bg-surface-container-low border border-outline-variant/20"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-7 h-7 rounded-lg bg-primary/10 text-primary flex items-center justify-center font-label-sm text-label-sm font-bold">
                            {field.num}
                          </span>
                          <span className="font-label-lg text-label-lg text-on-surface font-semibold">
                            {field.title}
                          </span>
                        </div>

                        {/* Numeric input synced with slider */}
                        <div className="flex items-center gap-2">
                          <input
                            type="number"
                            min="0"
                            max="100"
                            step="1"
                            disabled={isSaving}
                            value={currentValue}
                            onChange={(e) => handleWeightChange(field.key, e.target.value)}
                            className="w-16 h-8 px-2 text-center rounded-lg bg-surface-container-lowest text-on-surface font-label-md text-label-md font-semibold border border-outline-variant/40 shadow-xs focus:outline-none focus:border-primary disabled:opacity-50"
                          />
                          <span className="font-label-md text-label-md text-outline">%</span>
                        </div>
                      </div>

                      {/* Range slider */}
                      <div className="py-1">
                        <input
                          type="range"
                          min="0"
                          max="100"
                          step="1"
                          disabled={isSaving}
                          value={currentValue}
                          onChange={(e) => handleWeightChange(field.key, e.target.value)}
                          className="w-full h-2 bg-surface-container-highest rounded-lg appearance-none cursor-pointer accent-primary focus:outline-none disabled:opacity-50"
                        />
                      </div>

                      <p className="font-body-sm text-body-sm text-on-surface-variant">
                        {field.desc}
                      </p>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )}

        {/* Global Save Error Banner */}
        {saveError && (
          <div className="p-space-md rounded-xl bg-error-container text-on-error-container flex items-center gap-space-sm border border-error/30">
            <span className="material-symbols-outlined text-[20px] text-error">error</span>
            <span className="font-body-md text-body-md">{saveError}</span>
          </div>
        )}

        {/* Bottom Sticky Action Bar */}
        <div className="fixed bottom-0 left-64 right-0 bg-surface-container-lowest/95 backdrop-blur-md px-space-xl py-space-md border-t border-outline-variant/20 shadow-lg z-30 flex items-center justify-between">
          <div className="flex items-center gap-space-sm">
            {saveSuccess ? (
              <span className="flex items-center gap-1.5 font-label-md text-label-md text-secondary font-semibold">
                <span className="w-2.5 h-2.5 rounded-full bg-secondary" />
                All changes saved
              </span>
            ) : !isTotal100 ? (
              <span className="flex items-center gap-1.5 font-label-md text-label-md text-error font-medium">
                <span className="w-2.5 h-2.5 rounded-full bg-error" />
                {discrepancyMessage}
              </span>
            ) : (
              <span className="flex items-center gap-1.5 font-label-md text-label-md text-outline">
                <span className="w-2.5 h-2.5 rounded-full bg-outline" />
                Weights balanced (100%)
              </span>
            )}
          </div>

          <div className="flex items-center gap-space-md">
            <button
              type="button"
              disabled={isSaving}
              onClick={() => {
                if (initialSettings) {
                  setWeights({ ...initialSettings.weights })
                  setFirstTimerBonus(initialSettings.firstTimerBonus ?? 5)
                  setDefaultMaxHours(initialSettings.defaultMaxHours ?? 8)
                }
                setSaveError('')
                setSaveSuccess(false)
              }}
              className="h-10 px-4 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer border border-outline-variant/30 disabled:opacity-50"
            >
              Discard changes
            </button>

            <button
              type="button"
              disabled={!isFormValid || isSaving}
              onClick={handleSave}
              className="h-10 px-5 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md shadow-sm transition-all flex items-center gap-2 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[18px]">
                {saveSuccess ? 'check_circle' : isSaving ? 'sync' : 'check'}
              </span>
              <span>{saveSuccess ? 'Saved' : isSaving ? 'Saving...' : 'Save changes'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Reset Confirmation Modal */}
      {showResetConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-outline-variant/30 p-6 max-w-sm w-full flex flex-col gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center text-primary shrink-0">
                <span className="material-symbols-outlined text-[22px]">restart_alt</span>
              </div>
              <div>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                  Reset to defaults?
                </h3>
                <p className="font-body-sm text-body-sm text-on-surface-variant">
                  This will restore default weights (Skill 45%, Experience 15%, Performance 15%, Load 15%, Interest 10%) and policy limits.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-outline-variant/20">
              <button
                type="button"
                onClick={() => setShowResetConfirm(false)}
                className="px-4 py-2 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmReset}
                className="px-4 py-2 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-label-md text-label-md transition-colors cursor-pointer shadow-sm"
              >
                Confirm reset
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
