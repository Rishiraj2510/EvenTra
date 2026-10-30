export default function StarRating({ value, max = 5, size = '18px' }) {
  if (value === null || value === undefined) {
    return null
  }

  const numericVal = Number(value)
  if (isNaN(numericVal)) {
    return null
  }

  // Round value to 1 decimal place
  const rounded = Math.round(numericVal * 10) / 10

  // Determine size classes or inline style for font-size
  const sizeStyle = typeof size === 'number' ? `${size}px` : size

  return (
    <div
      className="inline-flex items-center gap-0.5"
      aria-label={`Rated ${rounded} out of ${max}`}
    >
      {Array.from({ length: max }, (_, index) => {
        const starIndex = index + 1
        // star i (1 to max) is full if value >= i, half if value >= i - 0.5, otherwise empty
        const isFull = rounded >= starIndex
        const isHalf = !isFull && rounded >= starIndex - 0.5

        if (isFull) {
          return (
            <span
              key={starIndex}
              className="material-symbols-outlined text-tertiary-container select-none"
              style={{
                fontSize: sizeStyle,
                fontVariationSettings: "'FILL' 1",
              }}
            >
              star
            </span>
          )
        }

        if (isHalf) {
          return (
            <span
              key={starIndex}
              className="material-symbols-outlined text-tertiary-container select-none"
              style={{
                fontSize: sizeStyle,
                fontVariationSettings: "'FILL' 1",
              }}
            >
              star_half
            </span>
          )
        }

        return (
          <span
            key={starIndex}
            className="material-symbols-outlined text-outline-variant select-none"
            style={{
              fontSize: sizeStyle,
              fontVariationSettings: "'FILL' 0",
            }}
          >
            star
          </span>
        )
      })}
    </div>
  )
}
