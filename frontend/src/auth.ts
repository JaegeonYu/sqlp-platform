import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError, type Me } from './api'

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api<Me>('/api/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
  })
}

export function useProviders() {
  return useQuery({
    queryKey: ['providers'],
    queryFn: () => api<{ google: boolean }>('/api/auth/providers'),
    staleTime: Infinity,
  })
}

export function useLogout() {
  const client = useQueryClient()
  return async () => {
    await api('/api/auth/logout', { method: 'POST' })
    client.clear()
    window.location.assign('/login')
  }
}
