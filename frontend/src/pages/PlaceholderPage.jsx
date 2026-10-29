import { useLocation } from 'react-router-dom'

export default function PlaceholderPage({ title }) {
  const location = useLocation()
  const notice = location.state?.message

  return (
    <div className="flex flex-col gap-4 w-full">
      {notice && (
        <div className="bg-surface-container-high text-on-surface px-4 py-3 rounded-xl flex items-center gap-2 border border-outline-variant/30 shadow-sm">
          <span className="material-symbols-outlined text-primary-container text-[20px]">info</span>
          <span className="font-label-md text-label-md">{notice}</span>
        </div>
      )}
      <div className="bg-surface-container-lowest rounded-xl shadow-sm p-12 flex flex-col items-center justify-center text-center">
        <span className="material-symbols-outlined text-[48px] text-outline mb-space-md">
          construction
        </span>
        <h2 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs">
          {title || 'Under Construction'}
        </h2>
        <p className="font-body-md text-body-md text-on-surface-variant max-w-sm">
          This screen is coming soon. Select Events from the sidebar to view active events.
        </p>
      </div>
    </div>
  )
}
