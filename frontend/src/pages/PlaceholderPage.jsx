export default function PlaceholderPage({ title }) {
  return (
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
  )
}
