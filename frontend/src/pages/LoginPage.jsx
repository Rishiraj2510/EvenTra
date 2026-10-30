import { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { login, USE_MOCKS } from '../services/api'
import { demoUser, demoVolunteerUser } from '../mocks/users'
import logo from '../assets/logo.png'

export default function LoginPage() {
  const { user, loginUser } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldErrors, setFieldErrors] = useState({ email: '', password: '' })
  const [authError, setAuthError] = useState('')
  const [loading, setLoading] = useState(false)

  // Route protection: if already logged in, redirect based on role
  if (user) {
    return <Navigate to={user.role === 'volunteer' ? '/my-duties' : '/events'} replace />
  }

  const validate = () => {
    const errors = { email: '', password: '' }
    let isValid = true

    if (!email.trim()) {
      errors.email = 'College email address is required'
      isValid = false
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = 'Please enter a valid email address'
      isValid = false
    }

    if (!password.trim()) {
      errors.password = 'Password is required'
      isValid = false
    }

    setFieldErrors(errors)
    return isValid
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setAuthError('')

    if (!validate()) {
      return
    }

    setLoading(true)
    try {
      const userData = await login(email, password)
      loginUser(userData)
      const targetRoute = userData?.role === 'volunteer' ? '/my-duties' : '/events'
      navigate(targetRoute, { replace: true })
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'Authentication failed. Please verify your credentials.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="min-h-screen w-full flex flex-col justify-center items-center p-0 bg-surface font-body-md text-body-md text-on-surface antialiased selection:bg-primary-container selection:text-on-primary">
      <div className="flex flex-col w-full items-center justify-center p-4 md:p-8">
        <div className="w-full max-w-6xl grid grid-cols-1 lg:grid-cols-12 rounded-xl shadow-xl overflow-hidden bg-surface-container-lowest">
          {/* Left Branded Panel (5 cols on lg) */}
          <div className="lg:col-span-5 bg-gradient-to-br from-primary via-primary-container to-on-primary-fixed-variant p-8 md:p-12 flex flex-col justify-between relative overflow-hidden text-on-primary">
            {/* Decorative Backdrop Geometry */}
            <div className="absolute -right-20 -top-20 w-80 h-80 rounded-full bg-white/5 blur-2xl pointer-events-none" />
            <div className="absolute -left-20 -bottom-20 w-96 h-96 rounded-full bg-primary/30 blur-3xl pointer-events-none" />

            {/* Header & Brand Mark */}
            <div className="relative z-10">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-white/10 backdrop-blur-md flex items-center justify-center shadow-sm">
                  <span className="material-symbols-outlined text-secondary-fixed text-2xl">hub</span>
                </div>
                <div className="flex flex-col">
                  <span className="font-headline-md text-headline-md tracking-tight font-bold text-white leading-none">
                    Eventra
                  </span>
                  <span className="font-label-sm text-label-sm text-primary-fixed-dim uppercase tracking-wider mt-1">
                    Coordination Grid
                  </span>
                </div>
              </div>

              <div className="mt-14">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 backdrop-blur-md text-secondary-fixed text-label-sm font-label-sm mb-4">
                  <span className="material-symbols-outlined text-sm">auto_awesome</span>
                  <span>Algorithmic Dispatch Engine</span>
                </div>
                <h1 className="font-headline-xl text-headline-xl text-white tracking-tight leading-tight">
                  Right volunteer.<br />Right task.
                </h1>
                <p className="font-body-md text-body-md text-primary-fixed-dim mt-4 leading-relaxed max-w-md">
                  Fair, algorithmic task allocation and AI-assisted coordination for campus and collegiate events.
                </p>
              </div>
            </div>

            {/* Algorithmic Interconnected Mesh SVG Graphic */}
            <div className="relative z-10 my-8 py-4 flex justify-center items-center">
              <svg
                className="w-full max-w-xs h-48 drop-shadow-md"
                fill="none"
                viewBox="0 0 320 200"
                xmlns="http://www.w3.org/2000/svg"
              >
                {/* Connection lines */}
                <path
                  d="M50 100 L120 45 M50 100 L120 155 M120 45 L200 70 M120 155 L200 130 M200 70 L270 100 M200 130 L270 100 M120 45 L120 155 M200 70 L200 130"
                  stroke="rgba(255,255,255,0.22)"
                  strokeDasharray="4 4"
                  strokeWidth="2"
                />
                <path d="M50 100 Q160 100 270 100" stroke="rgba(111,251,190,0.6)" strokeWidth="2" />
                {/* Outer task node 1 */}
                <circle cx="50" cy="100" fill="rgba(255,255,255,0.12)" r="18" />
                <circle cx="50" cy="100" fill="#c3c0ff" r="10" />
                {/* Intermediary matching nodes */}
                <circle cx="120" cy="45" fill="rgba(255,255,255,0.15)" r="14" />
                <circle cx="120" cy="45" fill="#6ffbbe" r="7" />
                <circle cx="120" cy="155" fill="rgba(255,255,255,0.15)" r="14" />
                <circle cx="120" cy="155" fill="#dad7ff" r="7" />
                {/* Central dynamic allocation hub */}
                <circle cx="160" cy="100" fill="rgba(255,255,255,0.1)" r="24" />
                <circle cx="160" cy="100" fill="#4f46e5" r="16" stroke="rgba(255,255,255,0.8)" strokeWidth="2" />
                <text
                  fill="#ffffff"
                  fontFamily="Inter, sans-serif"
                  fontSize="10"
                  fontWeight="700"
                  textAnchor="middle"
                  x="160"
                  y="104"
                >
                  AI
                </text>
                <circle cx="200" cy="70" fill="rgba(255,255,255,0.15)" r="14" />
                <circle cx="200" cy="70" fill="#dad7ff" r="7" />
                <circle cx="200" cy="130" fill="rgba(255,255,255,0.15)" r="14" />
                <circle cx="200" cy="130" fill="#6ffbbe" r="7" />
                {/* Target assigned volunteer node */}
                <circle cx="270" cy="100" fill="rgba(255,255,255,0.12)" r="18" />
                <circle cx="270" cy="100" fill="#6ffbbe" r="10" />
              </svg>
            </div>
          </div>

          {/* Right Panel: Auth Forms Container (7 cols on lg) */}
          <div className="lg:col-span-7 p-6 sm:p-10 md:p-12 flex flex-col justify-center bg-surface-container-lowest">
            {/* Top Header & Context */}
            <div className="flex items-center justify-between mb-8">
              <div className="flex items-center">
                <img
                  alt="Eventra Logo"
                  className="h-8 w-auto object-contain"
                  src={logo}
                />
              </div>
              <div className="flex items-center gap-2 text-on-surface-variant font-label-sm text-label-sm bg-surface-container-low px-3 py-1.5 rounded-full">
                <span className="w-2 h-2 rounded-full bg-secondary" />
                <span>Collegiate Portal Online</span>
              </div>
            </div>

            <div className="mb-8">
              <h2 className="font-headline-lg text-headline-lg font-bold text-on-surface tracking-tight" id="viewHeading">
                Welcome back
              </h2>
              <p className="font-body-md text-body-md text-on-surface-variant mt-1" id="viewSubheading">
                Access your roster, shift schedules, and operational allocations.
              </p>
            </div>

            {/* LOGIN FORM */}
            <form className="flex flex-col space-y-5" id="loginForm" onSubmit={handleSubmit} noValidate>
              <div>
                <label className="block font-label-md text-label-md font-medium text-on-surface mb-1.5" htmlFor="loginEmail">
                  College Email Address
                </label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-on-surface-variant">
                    <span className="material-symbols-outlined text-xl">alternate_email</span>
                  </span>
                  <input
                    id="loginEmail"
                    type="email"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value)
                      if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: '' }))
                    }}
                    placeholder="name@college.edu"
                    disabled={loading}
                    className={`w-full h-10 pl-11 pr-4 rounded-lg bg-surface-container-lowest text-on-surface placeholder:text-outline font-body-md text-body-md shadow-sm outline-none transition-colors ${
                      fieldErrors.email
                        ? 'border border-error focus:border-error'
                        : 'border border-outline-variant/40 focus:bg-surface-container-low'
                    }`}
                  />
                </div>
                {fieldErrors.email && (
                  <p className="font-body-sm text-body-sm text-error mt-1">{fieldErrors.email}</p>
                )}
              </div>

              <div>
                <label className="block font-label-md text-label-md font-medium text-on-surface mb-1.5" htmlFor="loginPassword">
                  Password
                </label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-on-surface-variant">
                    <span className="material-symbols-outlined text-xl">lock</span>
                  </span>
                  <input
                    id="loginPassword"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value)
                      if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: '' }))
                    }}
                    placeholder="••••••••••••"
                    disabled={loading}
                    className={`w-full h-10 pl-11 pr-11 rounded-lg bg-surface-container-lowest text-on-surface placeholder:text-outline font-body-md text-body-md shadow-sm outline-none transition-colors ${
                      fieldErrors.password
                        ? 'border border-error focus:border-error'
                        : 'border border-outline-variant/40 focus:bg-surface-container-low'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-on-surface-variant hover:text-on-surface cursor-pointer"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <span className="material-symbols-outlined text-xl">
                      {showPassword ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>
                {fieldErrors.password && (
                  <p className="font-body-sm text-body-sm text-error mt-1">{fieldErrors.password}</p>
                )}
              </div>

              {/* Inline Error Notice Banner */}
              {authError && (
                <div
                  className="flex items-start gap-3 p-3.5 rounded-xl bg-error-container text-on-error-container"
                  id="loginAlert"
                >
                  <span className="material-symbols-outlined text-xl flex-shrink-0 text-error">error</span>
                  <div className="flex-1">
                    <p className="font-label-md text-label-md font-semibold text-error">Authentication failed</p>
                    <p className="font-body-sm text-body-sm mt-0.5 text-on-error-container">{authError}</p>
                  </div>
                </div>
              )}

              <div className="pt-2">
                <button
                  id="loginSubmitBtn"
                  type="submit"
                  disabled={loading}
                  className="w-full h-10 bg-primary-container text-on-primary rounded-lg font-label-lg text-label-lg font-medium shadow-md hover:bg-primary transition-all flex items-center justify-center gap-2 cursor-pointer active:scale-[0.99] disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {loading ? (
                    <>
                      <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path
                          className="opacity-75"
                          fill="currentColor"
                          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                        />
                      </svg>
                      <span>Signing in...</span>
                    </>
                  ) : (
                    <>
                      <span>Log in</span>
                      <span className="material-symbols-outlined text-lg">arrow_forward</span>
                    </>
                  )}
                </button>
              </div>
            </form>

            {/* Demo accounts note (shown only when USE_MOCKS is true) */}
            {USE_MOCKS && (
              <div className="mt-6 pt-5 border-t border-slate-200 text-left">
                <div className="flex items-center gap-1.5 mb-2 text-on-surface-variant">
                  <span className="material-symbols-outlined text-[16px] text-outline">badge</span>
                  <span className="font-label-sm text-label-sm font-semibold uppercase tracking-wider">
                    Demo accounts
                  </span>
                  <span className="text-outline text-label-sm">· Use any password</span>
                </div>
                <div className="flex flex-col sm:flex-row gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setEmail(demoUser.email)
                      if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: '' }))
                    }}
                    className="flex-1 p-2.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-left transition-colors border border-outline-variant/30 cursor-pointer group"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-label-sm text-label-sm font-medium text-on-surface">
                        Organiser
                      </span>
                      <span className="material-symbols-outlined text-[14px] text-outline group-hover:text-primary transition-colors">
                        north_east
                      </span>
                    </div>
                    <p className="font-body-sm text-body-sm text-primary font-mono truncate mt-0.5">
                      {demoUser.email}
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setEmail(demoVolunteerUser.email)
                      if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: '' }))
                    }}
                    className="flex-1 p-2.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-left transition-colors border border-outline-variant/30 cursor-pointer group"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-label-sm text-label-sm font-medium text-on-surface">
                        Volunteer
                      </span>
                      <span className="material-symbols-outlined text-[14px] text-outline group-hover:text-primary transition-colors">
                        north_east
                      </span>
                    </div>
                    <p className="font-body-sm text-body-sm text-primary font-mono truncate mt-0.5">
                      {demoVolunteerUser.email}
                    </p>
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  )
}
