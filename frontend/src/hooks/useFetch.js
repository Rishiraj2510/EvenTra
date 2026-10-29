import { useState, useEffect, useCallback } from 'react'

export function useFetch(asyncFn, immediate = true) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(immediate)
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await asyncFn()
      setData(result)
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      throw err
    } finally {
      setLoading(false)
    }
  }, [asyncFn])

  useEffect(() => {
    let ignore = false
    if (immediate) {
      asyncFn()
        .then((result) => {
          if (!ignore) {
            setData(result)
            setLoading(false)
          }
        })
        .catch((err) => {
          if (!ignore) {
            setError(err instanceof Error ? err.message : String(err))
            setLoading(false)
          }
        })
    }
    return () => {
      ignore = true
    }
  }, [asyncFn, immediate])

  return { data, loading, error, reload }
}
