/**
 * CertificateCard.jsx
 *
 * Renders a certificate based on:
 * - template: { type: "builtin" | "custom", templateId: "classic" | "modern" | "minimal", backgroundImage: string, namePosition: "upper" | "center" | "lower", textColor: "dark" | "light" }
 * - volunteerName: string
 * - eventName: string
 * - date: string
 * - hours?: number | string
 * - isPreviewThumbnail?: boolean (for mini card previews if needed)
 */

export default function CertificateCard({
  template,
  volunteerName = '[Volunteer Name]',
  eventName = '[Event Name]',
  date = '[Date]',
  hours = 4,
}) {
  const t = template || {
    type: 'builtin',
    templateId: 'classic',
    backgroundImage: '',
    namePosition: 'center',
    textColor: 'dark',
  }

  // -------------------------------------------------------------
  // CUSTOM TEMPLATE RENDERING
  // -------------------------------------------------------------
  if (t.type === 'custom' && t.backgroundImage) {
    const isLightText = t.textColor === 'light'
    const textColorClass = isLightText ? 'text-white' : 'text-slate-900'
    const textSubColorClass = isLightText ? 'text-slate-200' : 'text-slate-600'
    const metaBgClass = isLightText ? 'bg-black/30 border-white/20' : 'bg-white/80 border-black/10'

    // Determine vertical layout for name & text over image
    let justifyClass = 'justify-center'
    if (t.namePosition === 'upper') {
      justifyClass = 'justify-start pt-8 sm:pt-12'
    } else if (t.namePosition === 'lower') {
      justifyClass = 'justify-end pb-8 sm:pb-12'
    }

    return (
      <div
        className="w-full aspect-[1.414/1] max-w-2xl bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 relative overflow-hidden flex flex-col"
        style={{
          backgroundImage: `url(${t.backgroundImage})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          printColorAdjust: 'exact',
          WebkitPrintColorAdjust: 'exact',
        }}
      >
        <div className={`absolute inset-0 p-6 sm:p-10 flex flex-col items-center text-center ${justifyClass}`}>
          {/* Volunteer Name */}
          <div className="max-w-[90%] z-10">
            <h2
              className={`font-serif italic font-bold tracking-tight text-xl sm:text-3xl md:text-4xl drop-shadow-xs truncate ${textColorClass}`}
            >
              {volunteerName}
            </h2>
            <div className={`w-36 sm:w-48 h-0.5 mx-auto my-2 ${isLightText ? 'bg-white/60' : 'bg-slate-900/60'}`} />
          </div>

          {/* Event and Attribution Text */}
          <div className="max-w-[85%] z-10 flex flex-col items-center">
            <p className={`text-xs sm:text-sm font-medium ${textSubColorClass}`}>
              For dedicated contributions and service to
            </p>
            <p className={`text-sm sm:text-base font-semibold truncate ${textColorClass}`}>
              {eventName}
            </p>
          </div>

          {/* Metadata pill */}
          <div
            className={`mt-4 px-4 py-1.5 rounded-full border backdrop-blur-xs flex items-center gap-4 text-xs font-medium z-10 ${metaBgClass} ${textColorClass}`}
          >
            <span>Date: {date}</span>
            <span>•</span>
            <span>{hours} hours verified</span>
          </div>
        </div>
      </div>
    )
  }

  // -------------------------------------------------------------
  // BUILT-IN TEMPLATES: Classic | Modern | Minimal
  // -------------------------------------------------------------
  const templateId = t.templateId || 'classic'

  // 1. MODERN TEMPLATE
  if (templateId === 'modern') {
    return (
      <div
        className="w-full bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm p-6 sm:p-10 relative flex flex-col justify-between overflow-hidden text-left"
        style={{ printColorAdjust: 'exact', WebkitPrintColorAdjust: 'exact' }}
      >
        {/* Modern Bold Top Geometric Bar */}
        <div className="absolute top-0 left-0 right-0 h-2 bg-primary" />

        <div className="flex items-start justify-between gap-4 mb-6 z-10">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-md bg-primary-container text-on-primary text-[10px] font-bold uppercase tracking-wider mb-2">
              <span className="material-symbols-outlined text-[13px]">verified</span>
              <span>Accreditation Certificate</span>
            </div>
            <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-on-surface uppercase">
              Certificate of Participation
            </h2>
          </div>
          <div className="w-10 h-10 rounded-lg bg-surface-container-low flex items-center justify-center text-primary font-bold text-lg">
            E
          </div>
        </div>

        <div className="py-4 z-10">
          <p className="text-xs text-outline uppercase tracking-wider mb-1 font-semibold">
            Awarded to
          </p>
          <div className="text-2xl sm:text-3xl font-bold text-primary tracking-tight truncate mb-3">
            {volunteerName}
          </div>
          <p className="text-sm text-on-surface-variant max-w-lg leading-relaxed">
            In formal recognition of outstanding performance, active duty attendance, and technical logistics execution at{' '}
            <span className="font-semibold text-on-surface">{eventName}</span>.
          </p>
        </div>

        <div className="pt-6 border-t border-outline-variant/20 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 z-10">
          <div className="flex items-center gap-6 text-xs text-outline font-label-md">
            <div>
              <span className="block text-[10px] uppercase text-outline">Issue Date</span>
              <span className="font-semibold text-on-surface">{date}</span>
            </div>
            <div className="w-px h-6 bg-outline-variant/30" />
            <div>
              <span className="block text-[10px] uppercase text-outline">Verified Hours</span>
              <span className="font-semibold text-on-surface font-mono">{hours} hrs</span>
            </div>
          </div>

          <div className="flex flex-col items-start sm:items-end text-xs">
            <div className="w-40 border-b border-outline-variant/60 mb-1" />
            <span className="font-medium text-on-surface">Eventra Operations Director</span>
            <span className="text-[10px] text-outline">Verified Digital Credential</span>
          </div>
        </div>
      </div>
    )
  }

  // 2. MINIMAL TEMPLATE
  if (templateId === 'minimal') {
    return (
      <div
        className="w-full bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm p-8 sm:p-12 relative flex flex-col items-center text-center justify-between"
        style={{ printColorAdjust: 'exact', WebkitPrintColorAdjust: 'exact' }}
      >
        <div className="flex flex-col items-center gap-2 mb-6">
          <span className="text-[11px] font-mono uppercase tracking-widest text-outline">
            Credential · Reference #EVT-CERT
          </span>
          <h2 className="text-lg sm:text-xl font-medium tracking-wide text-on-surface uppercase">
            Certificate of Service
          </h2>
        </div>

        <div className="my-6 max-w-lg">
          <p className="text-xs text-outline italic mb-3">Presented to</p>
          <div className="text-2xl sm:text-3xl font-serif text-on-surface font-semibold tracking-normal mb-4">
            {volunteerName}
          </div>
          <p className="text-xs sm:text-sm text-on-surface-variant leading-relaxed">
            For operational participation and voluntary contributions during{' '}
            <span className="font-medium text-on-surface">{eventName}</span>.
          </p>
        </div>

        <div className="w-full max-w-md pt-6 border-t border-outline-variant/30 flex items-center justify-between text-xs text-outline font-mono">
          <div>
            <span>DATE: </span>
            <span className="text-on-surface font-semibold">{date}</span>
          </div>
          <div>
            <span>HOURS: </span>
            <span className="text-on-surface font-semibold">{hours}</span>
          </div>
          <div>
            <span>STATUS: </span>
            <span className="text-primary font-semibold">VERIFIED</span>
          </div>
        </div>
      </div>
    )
  }

  // 3. CLASSIC TEMPLATE (Default)
  return (
    <div
      className="w-full bg-surface-container-lowest rounded-xl border-2 border-primary/20 shadow-sm p-8 sm:p-10 relative flex flex-col items-center text-center overflow-hidden"
      style={{ printColorAdjust: 'exact', WebkitPrintColorAdjust: 'exact' }}
    >
      {/* Decorative Inner Line */}
      <div className="absolute inset-2 border border-primary/10 rounded-lg pointer-events-none" />

      {/* Top Eventra Badge */}
      <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 text-primary border border-primary/20 mb-space-md relative z-10">
        <span className="material-symbols-outlined text-[14px]">layers</span>
        <span className="font-label-sm text-label-sm uppercase font-bold tracking-wider">
          Eventra Verified Credential
        </span>
      </div>

      <h2 className="font-headline-lg text-headline-lg font-bold uppercase tracking-tight text-on-surface mb-space-xs relative z-10">
        Certificate of Participation
      </h2>
      <p className="font-body-sm text-body-sm text-outline uppercase tracking-widest mb-space-lg relative z-10">
        This certificate is proudly presented to
      </p>

      {/* Volunteer Name */}
      <div className="py-space-xs px-space-xl mb-space-md relative z-10">
        <span className="font-headline-xl text-headline-xl italic text-primary font-semibold font-serif">
          {volunteerName}
        </span>
        <div className="w-48 h-0.5 bg-primary/30 mx-auto mt-2" />
      </div>

      <p className="font-body-md text-body-md text-on-surface-variant max-w-md mb-space-xs relative z-10">
        For dedicated service and contributions to
      </p>
      <p className="font-headline-sm text-headline-sm font-semibold text-on-surface mb-space-xl relative z-10">
        {eventName}
      </p>

      {/* Metadata Row */}
      <div className="grid grid-cols-2 gap-space-lg w-full max-w-md py-space-sm px-space-lg bg-surface-container-low rounded-lg border border-outline-variant/30 mb-space-xl text-body-sm relative z-10">
        <div className="text-center">
          <span className="text-outline block uppercase font-label-sm text-label-sm mb-0.5">Date</span>
          <span className="font-semibold text-on-surface">{date}</span>
        </div>
        <div className="text-center border-l border-outline-variant/30">
          <span className="text-outline block uppercase font-label-sm text-label-sm mb-0.5">Hours verified</span>
          <span className="font-semibold text-on-surface font-mono">{hours} hrs</span>
        </div>
      </div>

      {/* Signature Line */}
      <div className="w-full max-w-md pt-space-xs flex flex-col items-center relative z-10">
        <div className="w-56 border-b border-outline-variant mb-space-xs" />
        <span className="font-label-sm text-label-sm text-on-surface font-medium">
          Organiser Signature / Eventra Verification
        </span>
        <span className="font-body-sm text-body-sm text-outline mt-0.5">Official Accreditation</span>
      </div>
    </div>
  )
}
