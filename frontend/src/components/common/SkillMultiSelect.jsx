import { useState, useRef, useEffect, useCallback } from 'react'

/**
 * SkillMultiSelect — a multi-select dropdown that supports predefined options
 * and custom skill entries.
 *
 * Props
 * ─────
 * value        string[]   Currently selected skills
 * onChange     (string[]) => void
 * options      string[]   Predefined option list (shown in dropdown)
 * disabled     boolean    Greys out and blocks all interaction
 * maxItems     number     Maximum number of skills allowed (default: 30)
 * maxLength    number     Max chars per skill name (default: 40)
 */
export default function SkillMultiSelect({
  value = [],
  onChange,
  options = [],
  disabled = false,
  maxItems = 30,
  maxLength = 40,
}) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [inlineError, setInlineError] = useState('')

  const containerRef = useRef(null)
  const searchRef = useRef(null)
  const triggerRef = useRef(null)

  // ── helpers ──────────────────────────────────────────────────────────────

  /** Case-insensitive membership test. Returns the canonical spelling if found. */
  const findExisting = useCallback(
    (text) => {
      const lower = text.trim().toLowerCase()
      // Check selected first (preserves custom skills)
      const inSelected = value.find((s) => s.toLowerCase() === lower)
      if (inSelected) return inSelected
      // Then check predefined options
      const inOptions = options.find((o) => o.toLowerCase() === lower)
      if (inOptions) return inOptions
      return null
    },
    [value, options],
  )

  // ── open / close ─────────────────────────────────────────────────────────

  const openDropdown = () => {
    if (disabled) return
    setOpen(true)
    setSearch('')
    setInlineError('')
  }

  const closeDropdown = () => {
    setOpen(false)
    setSearch('')
    setInlineError('')
  }

  // Close on outside click
  useEffect(() => {
    if (!open) return
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        closeDropdown()
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  // Focus search input when dropdown opens
  useEffect(() => {
    if (open && searchRef.current) {
      searchRef.current.focus()
    }
  }, [open])

  // ── add / remove ─────────────────────────────────────────────────────────

  const addSkill = useCallback(
    (raw) => {
      const trimmed = raw.trim()
      if (!trimmed) return

      // Dedupe
      const existing = findExisting(trimmed)
      if (existing) {
        if (value.includes(existing)) {
          setInlineError('')
          return // already selected
        }
        // Not yet selected — add with canonical spelling
        if (value.length >= maxItems) {
          setInlineError(`Maximum ${maxItems} skills allowed.`)
          return
        }
        onChange([...value, existing])
        setInlineError('')
        setSearch('')
        return
      }

      // Length check
      if (trimmed.length > maxLength) {
        setInlineError(`Skill name must be ${maxLength} characters or fewer.`)
        return
      }

      // Max check
      if (value.length >= maxItems) {
        setInlineError(`Maximum ${maxItems} skills allowed.`)
        return
      }

      onChange([...value, trimmed])
      setInlineError('')
      setSearch('')
    },
    [value, onChange, findExisting, maxItems, maxLength],
  )

  const removeSkill = useCallback(
    (skill) => {
      if (disabled) return
      onChange(value.filter((s) => s !== skill))
    },
    [disabled, value, onChange],
  )

  const toggleSkill = useCallback(
    (skill) => {
      if (disabled) return
      if (value.includes(skill)) {
        removeSkill(skill)
      } else {
        addSkill(skill)
      }
    },
    [disabled, value, addSkill, removeSkill],
  )

  // ── keyboard ──────────────────────────────────────────────────────────────

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      closeDropdown()
      triggerRef.current?.focus()
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      if (search.trim()) {
        addSkill(search)
      }
    }
  }

  const handleTriggerKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      open ? closeDropdown() : openDropdown()
    }
    if (e.key === 'Escape' && open) {
      e.preventDefault()
      closeDropdown()
    }
  }

  // ── filtered list ─────────────────────────────────────────────────────────

  const lowerSearch = search.trim().toLowerCase()

  // All options + custom selected skills (not in options), sorted
  const customSelected = value.filter(
    (s) => !options.some((o) => o.toLowerCase() === s.toLowerCase()),
  )
  const allDisplayed = [...options, ...customSelected]

  const filtered = lowerSearch
    ? allDisplayed.filter((o) => o.toLowerCase().includes(lowerSearch))
    : allDisplayed

  // "Add custom" row: shown when search is non-empty and no exact match
  const exactMatch = lowerSearch ? findExisting(search) : null
  const shouldShowAddRow = lowerSearch.length > 0 && !exactMatch

  // ── render ────────────────────────────────────────────────────────────────

  return (
    <div ref={containerRef} className="flex flex-col gap-2">
      {/* Selected skills chips */}
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {value.map((skill) => (
            <span
              key={skill}
              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary-fixed text-on-primary-fixed-variant font-label-md text-label-md shadow-2xs"
            >
              <span>{skill}</span>
              {!disabled && (
                <button
                  type="button"
                  onClick={() => removeSkill(skill)}
                  className="hover:opacity-75 cursor-pointer flex items-center justify-center"
                  title={`Remove ${skill}`}
                  aria-label={`Remove ${skill}`}
                >
                  <span className="material-symbols-outlined text-[15px]">close</span>
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      {/* Dropdown trigger */}
      <div className="relative">
        <button
          ref={triggerRef}
          type="button"
          disabled={disabled}
          onClick={() => (open ? closeDropdown() : openDropdown())}
          onKeyDown={handleTriggerKeyDown}
          aria-haspopup="listbox"
          aria-expanded={open}
          className={`w-full flex items-center justify-between gap-2 h-10 px-space-md rounded-lg border font-body-md text-body-md transition-colors
            ${disabled
              ? 'bg-surface-container-low/40 text-on-surface/40 border-outline-variant/20 cursor-not-allowed opacity-60'
              : open
              ? 'bg-surface-container-low text-on-surface border-primary/50 ring-2 ring-primary/20 cursor-pointer'
              : 'bg-surface-container-low text-on-surface border-outline-variant/30 hover:border-outline/50 cursor-pointer'
            }`}
        >
          <span className={value.length === 0 ? 'text-on-surface/50' : ''}>
            {value.length === 0
              ? 'Select or add skills…'
              : `${value.length} skill${value.length === 1 ? '' : 's'} selected`}
          </span>
          <span className="material-symbols-outlined text-[20px] text-outline shrink-0">
            {open ? 'expand_less' : 'expand_more'}
          </span>
        </button>

        {/* Dropdown panel */}
        {open && (
          <div
            className="absolute z-50 mt-1 w-full rounded-xl bg-surface-container-lowest border border-outline-variant/30 shadow-lg flex flex-col overflow-hidden"
            style={{ maxHeight: '340px' }}
          >
            {/* Search input */}
            <div className="p-2 border-b border-outline-variant/20 shrink-0">
              <input
                ref={searchRef}
                type="text"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setInlineError('')
                }}
                onKeyDown={handleSearchKeyDown}
                placeholder="Search or type a custom skill…"
                className="w-full h-9 px-3 rounded-lg bg-surface-container-low text-on-surface font-body-md text-body-md border border-outline-variant/30 focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
              {/* Inline error (length / max items) */}
              {inlineError && (
                <p className="mt-1 font-body-sm text-[12px] text-error px-1">{inlineError}</p>
              )}
            </div>

            {/* Options list */}
            <ul
              role="listbox"
              aria-multiselectable="true"
              className="overflow-y-auto"
              style={{ maxHeight: '240px' }}
            >
              {/* "Add custom" row */}
              {shouldShowAddRow && (
                <li>
                  <button
                    type="button"
                    onClick={() => addSkill(search)}
                    className="w-full flex items-center gap-2 px-3 py-2.5 text-left hover:bg-primary/5 transition-colors cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[18px] text-primary shrink-0">
                      add_circle
                    </span>
                    <span className="font-body-md text-body-md text-on-surface">
                      Add{' '}
                      <span className="font-semibold text-primary">
                        &ldquo;{search.trim()}&rdquo;
                      </span>{' '}
                      as a custom skill
                    </span>
                  </button>
                  {filtered.length > 0 && (
                    <div className="mx-3 border-t border-outline-variant/20" />
                  )}
                </li>
              )}

              {/* Matching options */}
              {filtered.map((option) => {
                const isSelected = value.includes(option)
                const isCustom = !options.some(
                  (o) => o.toLowerCase() === option.toLowerCase(),
                )
                return (
                  <li
                    key={option}
                    role="option"
                    aria-selected={isSelected}
                  >
                    <button
                      type="button"
                      onClick={() => toggleSkill(option)}
                      className={`w-full flex items-center gap-2.5 px-3 py-2.5 text-left transition-colors cursor-pointer
                        ${isSelected
                          ? 'bg-primary/8 hover:bg-primary/12'
                          : 'hover:bg-surface-container-low'
                        }`}
                    >
                      {/* Checkbox indicator */}
                      <span
                        className={`w-4 h-4 rounded border shrink-0 flex items-center justify-center transition-colors
                          ${isSelected
                            ? 'bg-primary border-primary'
                            : 'border-outline-variant/60 bg-transparent'
                          }`}
                      >
                        {isSelected && (
                          <span className="material-symbols-outlined text-on-primary text-[13px]">
                            check
                          </span>
                        )}
                      </span>

                      <span className="flex-1 font-body-md text-body-md text-on-surface">
                        {option}
                      </span>

                      {/* Custom tag */}
                      {isCustom && (
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide bg-surface-container text-outline border border-outline-variant/40 shrink-0">
                          Custom
                        </span>
                      )}
                    </button>
                  </li>
                )
              })}

              {/* No results (search active, no matches, no add row for exact dupes) */}
              {!shouldShowAddRow && filtered.length === 0 && (
                <li className="px-4 py-6 text-center font-body-md text-body-md text-outline">
                  No skills found
                </li>
              )}

              {/* No results when search is empty and options list is empty */}
              {!lowerSearch && filtered.length === 0 && (
                <li className="px-4 py-6 text-center font-body-md text-body-md text-outline">
                  No skills available
                </li>
              )}
            </ul>

            {/* Footer: count info */}
            <div className="px-3 py-2 border-t border-outline-variant/15 shrink-0 flex items-center justify-between">
              <span className="font-body-sm text-[11px] text-outline">
                {value.length} / {maxItems} selected
              </span>
              <button
                type="button"
                onClick={closeDropdown}
                className="font-label-sm text-[12px] text-primary hover:opacity-80 cursor-pointer"
              >
                Done
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Empty state hint when nothing selected and not disabled */}
      {value.length === 0 && !disabled && !open && (
        <p className="font-body-sm text-body-sm text-outline italic">
          No skills offered yet.
        </p>
      )}
    </div>
  )
}
