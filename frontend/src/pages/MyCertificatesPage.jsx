import { useState, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getMyCertificates } from '../services/api'
import { useFetch } from '../hooks/useFetch'
import CertificateCard from '../components/certificates/CertificateCard'

export default function MyCertificatesPage() {
  const { role, user } = useAuth()

  // Selected certificate for viewing / downloading
  const [selectedCert, setSelectedCert] = useState(null)

  const volunteerId = user?.volunteerId || 'vol-1'

  // Fetch my certificates callback
  const fetchMyCertificatesCallback = useCallback(() => {
    return getMyCertificates(volunteerId)
  }, [volunteerId])

  const { data: certificates, loading, error, reload } = useFetch(fetchMyCertificatesCallback)

  // Ensure role guard is evaluated after hooks
  if (role !== 'volunteer') {
    return <Navigate to="/dashboard" replace />
  }

  const handleOpenCert = (cert) => {
    setSelectedCert(cert)
  }

  const handleCloseCert = () => {
    setSelectedCert(null)
  }

  const handleDownloadCert = () => {
    window.print()
  }

  // 1. Loading state
  if (loading) {
    return (
      <div className="flex flex-col w-full min-h-[70vh] items-center justify-center p-margin-lg">
        <div className="flex flex-col items-center gap-space-md">
          <div className="w-12 h-12 rounded-full border-3 border-primary/20 border-t-primary animate-spin" />
          <p className="font-label-md text-label-md text-outline">Loading your certificates...</p>
        </div>
      </div>
    )
  }

  // 2. Error state
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
            {error || 'An error occurred while loading your certificates.'}
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
    <>
      {/* Print stylesheet to isolate certificate card when printing */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #printable-certificate, #printable-certificate * {
            visibility: visible;
          }
          #printable-certificate {
            position: fixed;
            left: 0;
            top: 0;
            width: 100vw;
            height: 100vh;
            margin: 0;
            padding: 2rem;
            background: white !important;
            box-shadow: none !important;
            border: none !important;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            z-index: 99999;
            print-color-adjust: exact !important;
            -webkit-print-color-adjust: exact !important;
          }
        }
      `}</style>

      <div className="flex flex-col w-full">
        {/* Top Header Banner */}
        <div className="flex flex-col gap-space-xs mb-margin-lg">
          <div className="flex items-center gap-space-sm text-primary font-label-sm uppercase tracking-wider">
            <span className="material-symbols-outlined text-[16px]">verified</span>
            <span>Official Accreditations</span>
          </div>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
            My Certificates
          </h1>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
            Official participation certificates awarded to you for your dedicated service and completed event shifts.
          </p>
        </div>

        {/* Empty State when no certificates issued */}
        {(!certificates || certificates.length === 0) ? (
          <div className="w-full bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 p-margin-lg mb-margin-lg flex flex-col items-center justify-center text-center py-20">
            <div className="w-16 h-16 rounded-full bg-surface-container-high flex items-center justify-center text-primary mb-space-lg shadow-sm">
              <span className="material-symbols-outlined text-[32px]">
                military_tech
              </span>
            </div>
            <h2 className="font-headline-md text-headline-md text-on-surface font-semibold mb-space-xs">
              No certificates yet
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-md leading-relaxed">
              Certificates appear here after an event organiser reviews your completed duties and issues your accreditation credential.
            </p>
          </div>
        ) : (
          /* Certificate Grid */
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-gutter-lg mb-space-xl">
            {certificates.map((cert) => {
              const formattedDate = cert.issuedAt
                ? new Date(cert.issuedAt).toLocaleDateString('en-US', {
                    year: 'numeric',
                    month: 'short',
                    day: 'numeric',
                  })
                : 'Issued'

              return (
                <div
                  key={cert.id}
                  className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 overflow-hidden flex flex-col group transition-all duration-200 hover:shadow-md"
                >
                  {/* Card Art Banner */}
                  <div className="h-36 bg-surface-container-low relative flex items-center justify-center overflow-hidden">
                    <div className="absolute inset-0 bg-gradient-to-br from-primary/10 via-transparent to-surface-container-high/40" />
                    <svg
                      className="absolute -right-6 -bottom-6 w-36 h-36 text-primary/5 transform rotate-12 pointer-events-none"
                      fill="currentColor"
                      viewBox="0 0 100 100"
                    >
                      <path d="M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z" />
                    </svg>
                    <div className="relative z-10 flex flex-col items-center gap-space-xs">
                      <div className="w-12 h-12 rounded-xl bg-surface-container-lowest shadow-sm flex items-center justify-center text-primary">
                        <span className="material-symbols-outlined text-[26px]">workspace_premium</span>
                      </div>
                      <div className="inline-flex items-center gap-space-xs bg-surface-container-lowest/90 backdrop-blur-md px-space-md py-0.5 rounded-full shadow-xs">
                        <span className="material-symbols-outlined text-secondary text-[14px]">
                          check_circle
                        </span>
                        <span className="font-label-sm text-label-sm text-secondary font-medium tracking-wide">
                          Eventra Verified
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Card Content */}
                  <div className="p-space-lg flex flex-col flex-1 justify-between gap-space-md">
                    <div className="flex flex-col gap-space-xs">
                      <div className="font-headline-sm text-headline-sm font-semibold text-on-surface leading-snug">
                        {cert.eventName}
                      </div>
                      <div className="flex items-center justify-between text-on-surface-variant font-label-md text-label-md">
                        <div className="flex items-center gap-space-xs">
                          <span className="material-symbols-outlined text-[16px] text-outline">
                            calendar_today
                          </span>
                          <span>{formattedDate}</span>
                        </div>
                        {cert.hours && (
                          <span className="font-mono text-outline">{cert.hours} hrs</span>
                        )}
                      </div>
                    </div>

                    {/* Card Actions */}
                    <div className="flex items-center gap-space-md pt-space-xs">
                      <button
                        type="button"
                        onClick={() => handleOpenCert(cert)}
                        className="flex-1 h-10 rounded-lg bg-surface-container-lowest border border-outline-variant/30 hover:bg-surface-container-low text-on-surface font-label-lg text-label-lg transition-colors flex items-center justify-center gap-space-xs cursor-pointer shadow-xs"
                      >
                        <span className="material-symbols-outlined text-[18px] text-outline">
                          visibility
                        </span>
                        <span>View</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedCert(cert)
                          setTimeout(() => window.print(), 100)
                        }}
                        className="flex-1 h-10 rounded-lg bg-primary-container text-on-primary font-label-lg text-label-lg transition-all hover:bg-primary active:scale-[0.99] flex items-center justify-center gap-space-xs shadow-sm cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[18px]">download</span>
                        <span>Download</span>
                      </button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Certificate View / Download Modal */}
        {selectedCert && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-on-surface/40 backdrop-blur-xs animate-fade-in">
            <div className="bg-surface-container-lowest rounded-2xl shadow-xl border border-outline-variant/30 max-w-2xl w-full overflow-hidden flex flex-col animate-scale-up">
              {/* Modal Top Bar */}
              <div className="px-space-lg py-space-md border-b border-outline-variant/20 flex items-center justify-between bg-surface-container-low/50">
                <div className="flex items-center gap-space-xs text-on-surface font-label-lg font-semibold">
                  <span className="material-symbols-outlined text-primary text-[20px]">workspace_premium</span>
                  <span>Official Certificate</span>
                </div>
                <button
                  type="button"
                  onClick={handleCloseCert}
                  className="p-1 rounded-md text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[20px]">close</span>
                </button>
              </div>

              {/* Printable Certificate Container */}
              <div className="p-6 sm:p-10 bg-surface flex justify-center overflow-y-auto">
                <div id="printable-certificate" className="w-full max-w-2xl flex justify-center">
                  <CertificateCard
                    template={selectedCert.template}
                    volunteerName={selectedCert.volunteerName || user?.fullName || 'Volunteer'}
                    eventName={selectedCert.eventName}
                    date={
                      selectedCert.issuedAt
                        ? new Date(selectedCert.issuedAt).toLocaleDateString('en-US', {
                            year: 'numeric',
                            month: 'long',
                            day: 'numeric',
                          })
                        : new Date().toLocaleDateString('en-US', {
                            year: 'numeric',
                            month: 'long',
                            day: 'numeric',
                          })
                    }
                    hours={selectedCert.hours || 4}
                  />
                </div>
              </div>

              {/* Modal Actions Footer */}
              <div className="px-space-lg py-space-md border-t border-outline-variant/20 bg-surface-container-lowest flex items-center justify-between">
                <button
                  type="button"
                  onClick={handleCloseCert}
                  className="px-space-lg py-2 border border-outline-variant/40 hover:bg-surface-container-low text-on-surface font-label-md rounded-lg transition-colors cursor-pointer"
                >
                  Close
                </button>
                <button
                  type="button"
                  onClick={handleDownloadCert}
                  className="flex items-center gap-space-xs px-space-lg py-2 rounded-lg bg-primary-container text-on-primary font-label-lg font-semibold shadow-sm hover:bg-primary active:scale-95 transition-all cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[18px]">print</span>
                  <span>Print / Save as PDF</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
